package com.auralis.app.network

import com.auralis.app.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlin.math.abs

data class JamTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val mediaUrl: String = "",
    val albumArtUrl: String? = null,
    val durationMs: Long = 0L,
    val qualityBadge: String = "HQ Audio",
    val source: String = "Auralis Master"
)

data class JamMessageDto(
    val type: String = "",
    val sender: String = "",
    val user: String? = null,
    val position: Long = 0L,
    val isPlaying: Boolean = false,
    val action: String = "sync",
    val emoji: String? = null,
    val quote: String? = null,
    val track: JamTrackDto? = null
)

/** One line of an ntfy JSON stream / WebSocket feed. */
data class NtfyEvent(
    val id: String?,
    val event: String?,
    val message: String?
)

/**
 * Pure protocol helper for Spotify Jam & Together Mode sync.
 * Handles topic sanitization, JSON building, payload parsing, echo suppression, drift calculations
 * and validation of any URL that arrives from a peer before it can reach the player.
 */
object JamProtocolHelper {

    private val gson = Gson()

    private const val TOPIC_PREFIX = "auralis_jam_"
    private const val MIN_CODE_LENGTH = 4
    private const val MAX_CODE_LENGTH = 32

    // Only these hosts may ever be handed to ExoPlayer as a remote stream.
    private val STREAM_HOSTS = listOf("googlevideo.com", "saavncdn.com", "audius.co", "dzcdn.net")

    // Only these hosts may be loaded as artwork when a peer sends a track.
    private val ARTWORK_HOSTS = listOf(
        "ytimg.com", "googleusercontent.com", "ggpht.com",
        "saavncdn.com", "audius.co", "dzcdn.net", "unsplash.com"
    )

    private val YOUTUBE_WATCH_URL = Regex("""^https://(www\.|music\.)?youtube\.com/watch\?v=[0-9A-Za-z_-]{11}$""")

    fun sanitizeCode(jamId: String): String =
        jamId.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    fun isValidJamCode(jamId: String): Boolean =
        sanitizeCode(jamId).length in MIN_CODE_LENGTH..MAX_CODE_LENGTH

    fun cleanTopic(jamId: String): String = TOPIC_PREFIX + sanitizeCode(jamId)

    fun shouldSeek(currentPositionMs: Long, targetPositionMs: Long, thresholdMs: Long = 1500L): Boolean {
        return abs(currentPositionMs - targetPositionMs) > thresholdMs
    }

    fun parseNtfyEvent(eventJsonLine: String): NtfyEvent? {
        return try {
            val root = gson.fromJson(eventJsonLine, JsonObject::class.java) ?: return null
            NtfyEvent(
                id = root.stringOrNull("id"),
                event = root.stringOrNull("event"),
                message = root.stringOrNull("message")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts the raw payload message from an ntfy HTTP stream or WebSocket event line.
     */
    fun extractMessageBody(eventJsonLine: String): String? =
        parseNtfyEvent(eventJsonLine)?.takeIf { it.event == "message" }?.message

    /**
     * Strips noise (like "Official Video", "feat.", "lyrics") to maximize audio search hit rates.
     */
    fun cleanSearchQuery(title: String, artist: String): String {
        val cleanTitle = title
            .replace(Regex("(?i)\\(.*?(official|feat|ft|video|audio|lyrics|remix|hd|4k).*?\\)"), "")
            .replace(Regex("(?i)\\[.*?(official|feat|ft|video|audio|lyrics|remix|hd|4k).*?\\]"), "")
            .replace(Regex("(?i)(official\\s+video|official\\s+audio|lyric\\s+video|visualizer|remastered|video)"), "")
            .trim()
        val cleanArtist = artist
            .replace(Regex("(?i)\\b(ft\\.?|feat\\.?|featuring)\\b.*"), "")
            .trim()
        return "$cleanTitle $cleanArtist".trim()
    }

    fun isLocalFileUrl(url: String?): Boolean =
        !url.isNullOrBlank() && (url.startsWith("file://") || url.startsWith("/"))

    fun isYouTubeWatchUrl(url: String?): Boolean = url != null && YOUTUBE_WATCH_URL.matches(url)

    /**
     * True only for something ExoPlayer may open directly: a local file, or an https URL on a known
     * music CDN. Web pages (youtube.com/watch) and arbitrary hosts are never "playable".
     */
    fun isPlayableDirectStreamUrl(url: String?): Boolean =
        isLocalFileUrl(url) || isAllowlistedHttpsUrl(url, STREAM_HOSTS)

    /** Local files never need resolving; anything else that is not a direct stream does. */
    fun needsStreamResolution(track: Track): Boolean =
        !isLocalFileUrl(track.mediaUrl) &&
            (track.isYouTubeTrack() || !isPlayableDirectStreamUrl(track.mediaUrl))

    /** A peer may only hand us a canonical YouTube watch URL or a stream on a known CDN. */
    fun sanitizeInboundMediaUrl(url: String?): String = when {
        url.isNullOrBlank() -> ""
        isYouTubeWatchUrl(url) -> url
        isAllowlistedHttpsUrl(url, STREAM_HOSTS) -> url
        else -> ""
    }

    fun sanitizeInboundArtworkUrl(url: String?): String? =
        url?.takeIf { isAllowlistedHttpsUrl(it, ARTWORK_HOSTS) }

    private fun isAllowlistedHttpsUrl(url: String?, hosts: List<String>): Boolean {
        val host = httpsHostOf(url) ?: return false
        return hosts.any { host == it || host.endsWith(".$it") }
    }

    // Hand-rolled instead of java.net.URI: CDN URLs contain characters URI rejects, and a parse
    // failure must not turn a legitimate stream into an "unplayable" track.
    private fun httpsHostOf(url: String?): String? {
        if (url == null || !url.startsWith("https://", ignoreCase = true)) return null
        val authority = url.substring("https://".length).takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        if (host.isEmpty() || host.startsWith(".") || host.endsWith(".")) return null
        return host.takeIf { h -> h.all { it.isLetterOrDigit() || it == '-' || it == '.' } }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun toTrackDto(track: Track) = JamTrackDto(
        id = track.id,
        title = track.title,
        artist = track.artist,
        mediaUrl = track.mediaUrl,
        albumArtUrl = track.albumArtUrl,
        durationMs = track.durationMs,
        qualityBadge = track.qualityBadge,
        source = track.source
    )

    private fun toInboundTrack(dto: JamTrackDto) = Track(
        id = dto.id,
        title = dto.title,
        artist = dto.artist,
        mediaUrl = sanitizeInboundMediaUrl(dto.mediaUrl),
        albumArtUrl = sanitizeInboundArtworkUrl(dto.albumArtUrl),
        durationMs = dto.durationMs,
        qualityBadge = dto.qualityBadge,
        source = dto.source
    )

    fun buildSyncPlaybackJson(
        username: String,
        track: Track,
        position: Long,
        isPlaying: Boolean,
        action: String = "sync"
    ): String {
        val dto = JamMessageDto(
            type = "sync_playback",
            sender = username,
            position = position,
            isPlaying = isPlaying,
            action = action,
            track = toTrackDto(track)
        )
        return gson.toJson(dto)
    }

    fun buildRequestSyncJson(username: String): String {
        val dto = JamMessageDto(
            type = "request_sync",
            sender = username
        )
        return gson.toJson(dto)
    }

    fun buildUserJoinedJson(username: String): String {
        val dto = JamMessageDto(
            type = "user_joined",
            sender = username,
            user = username
        )
        return gson.toJson(dto)
    }

    fun buildUserLeftJson(username: String): String {
        val dto = JamMessageDto(
            type = "user_left",
            sender = username,
            user = username
        )
        return gson.toJson(dto)
    }

    fun buildReactionJson(username: String, emoji: String): String {
        val dto = JamMessageDto(
            type = "reaction",
            sender = username,
            emoji = emoji
        )
        return gson.toJson(dto)
    }

    fun buildMemoryQuoteJson(username: String, quote: String): String {
        val dto = JamMessageDto(
            type = "memory_quote",
            sender = username,
            quote = quote
        )
        return gson.toJson(dto)
    }

    fun buildQueueTrackJson(username: String, track: Track): String {
        val dto = JamMessageDto(
            type = "queue_track",
            sender = username,
            track = toTrackDto(track)
        )
        return gson.toJson(dto)
    }

    fun parseJamPayload(jsonString: String, currentUsername: String): JamState? {
        return try {
            val dto = gson.fromJson(jsonString, JamMessageDto::class.java) ?: return null

            // Echo suppression: Ignore own echoes (case-insensitive)
            if (dto.sender.equals(currentUsername, ignoreCase = true)) {
                return null
            }

            when (dto.type) {
                "sync_playback" -> {
                    val trackDto = dto.track ?: return null
                    JamState.SyncPlayback(toInboundTrack(trackDto), dto.position, dto.isPlaying, dto.sender, dto.action)
                }

                "request_sync" -> {
                    JamState.RequestSync(dto.sender)
                }

                "user_joined" -> {
                    val joinedUser = dto.user ?: dto.sender
                    JamState.UserJoined(joinedUser)
                }

                "user_left" -> {
                    val leftUser = dto.user ?: dto.sender
                    JamState.UserLeft(leftUser)
                }

                "reaction" -> {
                    val emoji = dto.emoji ?: "❤️"
                    JamState.ReactionReceived(emoji, dto.sender)
                }

                "memory_quote" -> {
                    val quote = dto.quote ?: "I love you jaanaa 💋"
                    JamState.MemoryQuoteReceived(quote, dto.sender)
                }

                "queue_track" -> {
                    val trackDto = dto.track ?: return null
                    JamState.QueueTrack(toInboundTrack(trackDto), dto.sender)
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
