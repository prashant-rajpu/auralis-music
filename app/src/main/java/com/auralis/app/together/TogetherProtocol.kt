package com.auralis.app.together

import com.auralis.app.core.util.SearchQueryNormalizer
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Kotlin half of the wire format in `relay/src/protocol.ts`. Both sides must agree; the relay
 * is the authority, because it is the side that can reject a message.
 *
 * The rule that matters: **a message never carries a playable stream URL.** Peers exchange a
 * [TrackRef] — provider, id, and enough metadata to display and find the song — and each phone
 * resolves its own audio through its own resolver. The relay enforces this by rejecting unknown
 * fields; this side enforces it by construction, because [TrackRef] has no field a URL could land
 * in and [TrackRef.toTrack] always produces an empty `mediaUrl`. A compromised relay, or a peer
 * talking to a self-hosted one, therefore cannot make someone else's player fetch anything.
 *
 * Chat is carried as ciphertext. There is deliberately no plaintext field to put a message in.
 */

/** Tolerant of fields a newer relay adds; there is no field here that a URL could occupy. */
val TogetherJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

@Serializable
data class TrackRef(
    val provider: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    /** Cover art only. Never audio, and never handed to the player. */
    val artUrl: String? = null,
    /** Lets an edition without the sender's catalog find its own copy of the song. */
    val fallbackQuery: String? = null,
) {
    /** Identity across both phones, even when the two resolved different files for it. */
    val key: String get() = "$provider:$providerId"
}

@Serializable
data class Member(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val buffering: Boolean,
    val joinedAtMs: Long,
    /**
     * IANA zone id. Carried because the whole point of this feature is two people who are not in
     * the same place, and knowing whether it is the middle of the night where they are changes
     * whether you start a session at all.
     */
    val timeZone: String = "",
)

@Serializable
data class QueueEntry(val track: TrackRef, val addedBy: String)

@Serializable
data class ChatEntry(val senderId: String, val serverMs: Long, val ciphertext: String)

@Serializable
data class PlaybackSnapshot(
    val track: TrackRef,
    val positionMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
    /** Server clock at the moment this position was true. The whole point of a relay. */
    val atServerMs: Long,
)

@Serializable
data class RoomSnapshot(
    val members: List<Member> = emptyList(),
    val hostId: String = "",
    val playback: PlaybackSnapshot? = null,
    val queue: List<QueueEntry> = emptyList(),
    val chat: List<ChatEntry> = emptyList(),
)

@Serializable
sealed interface TogetherClientMessage {

    @Serializable
    @SerialName("ping")
    data class Ping(val at: Long) : TogetherClientMessage

    @Serializable
    @SerialName("playback")
    data class Playback(
        val track: TrackRef,
        val positionMs: Long,
        val isPlaying: Boolean,
        val speed: Float = 1f,
    ) : TogetherClientMessage

    @Serializable
    @SerialName("seek")
    data class Seek(val positionMs: Long) : TogetherClientMessage

    @Serializable
    @SerialName("queueAdd")
    data class QueueAdd(val track: TrackRef) : TogetherClientMessage

    @Serializable
    @SerialName("queueRemove")
    data class QueueRemove(val providerId: String) : TogetherClientMessage

    @Serializable
    @SerialName("buffering")
    data class Buffering(val buffering: Boolean) : TogetherClientMessage

    /** Encrypted on this side. The relay stores and forwards bytes it cannot read. */
    @Serializable
    @SerialName("chat")
    data class Chat(val ciphertext: String) : TogetherClientMessage

    @Serializable
    @SerialName("reaction")
    data class Reaction(val emoji: String) : TogetherClientMessage

    @Serializable
    @SerialName("bye")
    data object Bye : TogetherClientMessage
}

@Serializable
sealed interface TogetherServerMessage {

    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val memberId: String,
        val hostId: String,
        val serverMs: Long,
        val state: RoomSnapshot,
    ) : TogetherServerMessage

    @Serializable
    @SerialName("pong")
    data class Pong(val at: Long, val serverMs: Long) : TogetherServerMessage

    @Serializable
    @SerialName("presence")
    data class Presence(
        val members: List<Member>,
        val hostId: String,
        val serverMs: Long,
    ) : TogetherServerMessage

    @Serializable
    @SerialName("playback")
    data class Playback(
        val senderId: String,
        val serverMs: Long,
        val track: TrackRef,
        val positionMs: Long,
        val isPlaying: Boolean,
        val speed: Float = 1f,
    ) : TogetherServerMessage

    @Serializable
    @SerialName("seek")
    data class Seek(
        val senderId: String,
        val serverMs: Long,
        val positionMs: Long,
    ) : TogetherServerMessage

    @Serializable
    @SerialName("queue")
    data class Queue(val serverMs: Long, val items: List<QueueEntry>) : TogetherServerMessage

    @Serializable
    @SerialName("chat")
    data class Chat(
        val senderId: String,
        val serverMs: Long,
        val ciphertext: String,
    ) : TogetherServerMessage

    @Serializable
    @SerialName("reaction")
    data class Reaction(
        val senderId: String,
        val serverMs: Long,
        val emoji: String,
    ) : TogetherServerMessage

    @Serializable
    @SerialName("error")
    data class Failure(val code: String, val message: String = "") : TogetherServerMessage
}

/**
 * Whether this edition can play a peer's track, and how.
 *
 * A `play` guest cannot resolve a YouTube id — that code is not compiled into it — and neither
 * edition can open a file that lives on the other phone. Rather than desyncing silently, those
 * fall back to a catalog search, and say so when even that has nothing to go on.
 */
enum class TrackAvailability {
    /** This edition can resolve the sender's id directly. */
    DIRECT,

    /** Not resolvable here, but there is enough to search for our own copy. */
    FALLBACK_SEARCH,

    /** Nothing this edition can do with it. */
    UNAVAILABLE,
}

object TogetherProtocol {

    fun encode(message: TogetherClientMessage): String =
        TogetherJson.encodeToString(TogetherClientMessage.serializer(), message)

    /** Returns null rather than throwing: one unreadable frame must not drop the session. */
    fun decodeServerMessage(raw: String): TogetherServerMessage? = try {
        TogetherJson.decodeFromString(TogetherServerMessage.serializer(), raw)
    } catch (e: Exception) {
        null
    }

    fun trackRef(track: Track): TrackRef = TrackRef(
        provider = track.provider.id,
        providerId = track.providerId,
        title = track.title,
        artist = track.artist,
        durationMs = track.durationMs,
        artUrl = track.albumArtUrl?.takeIf { it.startsWith("https://") },
        fallbackQuery = SearchQueryNormalizer.normalize(track.title, track.artist),
    )

    /**
     * Rebuilds a local [Track] from a peer's reference. `mediaUrl` is always empty — the resolver
     * fills it in from this phone's own sources, which is what keeps a peer from choosing what we
     * play from.
     */
    fun toTrack(ref: TrackRef): Track = Track(
        id = localId(ref),
        title = ref.title,
        artist = ref.artist,
        albumArtUrl = ref.artUrl?.takeIf { it.startsWith("https://") },
        mediaUrl = "",
        durationMs = ref.durationMs,
        source = "Together",
    )

    fun availability(ref: TrackRef, hasScrapedSources: Boolean): TrackAvailability {
        val searchable = searchQuery(ref).isNotBlank()
        return when (ref.provider) {
            Provider.AUDIUS.id, Provider.JAMENDO.id -> TrackAvailability.DIRECT
            Provider.YOUTUBE.id, Provider.JIOSAAVN.id ->
                if (hasScrapedSources) TrackAvailability.DIRECT
                else if (searchable) TrackAvailability.FALLBACK_SEARCH
                else TrackAvailability.UNAVAILABLE
            // A file on the other phone, or a track we have no id scheme for: search for our own.
            else ->
                if (searchable) TrackAvailability.FALLBACK_SEARCH else TrackAvailability.UNAVAILABLE
        }
    }

    fun searchQuery(ref: TrackRef): String =
        ref.fallbackQuery?.takeIf { it.isNotBlank() }
            ?: SearchQueryNormalizer.normalize(ref.title, ref.artist)

    /**
     * The inverse of [Track.providerId]. Track ids still encode their origin in a prefix, so a
     * reference has to be put back into that shape for the resolver to recognise it.
     */
    private fun localId(ref: TrackRef): String = when (ref.provider) {
        Provider.YOUTUBE.id -> "yt_${ref.providerId}"
        Provider.AUDIUS.id -> "auralis_global_${ref.providerId}"
        Provider.JIOSAAVN.id -> "auralis_${ref.providerId}"
        Provider.JAMENDO.id -> "jamendo_${ref.providerId}"
        Provider.LOCAL.id -> "local_${ref.providerId}"
        else -> ref.providerId
    }
}
