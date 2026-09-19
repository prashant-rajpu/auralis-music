package com.auralis.app.together

import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what the session sent, and lets a test put words in the room's mouth. */
class FakeTransport : TogetherTransport {

    override val connection = MutableStateFlow<TogetherConnection>(TogetherConnection.Idle)
    override val messages = MutableSharedFlow<TogetherServerMessage>(extraBufferCapacity = 64)

    val sent = mutableListOf<TogetherClientMessage>()
    var joined: Triple<String, String, String>? = null
    var left = false
    var createResult: Result<RoomCredentials> =
        Result.success(RoomCredentials("ABCDEF", "host-token-0123456789"))

    /** Everything except the clock-sync chatter, which is almost always noise in an assertion. */
    val meaningful: List<TogetherClientMessage>
        get() = sent.filterNot { it is TogetherClientMessage.Ping }

    override suspend fun createRoom(): Result<RoomCredentials> = createResult

    override fun join(code: String, token: String, displayName: String) {
        joined = Triple(code, token, displayName)
        left = false
    }

    override fun send(message: TogetherClientMessage) {
        sent.add(message)
    }

    override fun leave() {
        left = true
        connection.value = TogetherConnection.Idle
    }
}

class FakePlayer : TogetherPlayer {
    override var currentTrack: Track? = null
    override var positionMs: Long = 0
    override var durationMs: Long = 240_000
    override var isPlaying: Boolean = false

    var appliedSpeed: Float = 1f
    val seeks = mutableListOf<Long>()
    val loaded = mutableListOf<Track>()

    override fun play() {
        isPlaying = true
    }

    override fun pause() {
        isPlaying = false
    }

    override fun seekTo(positionMs: Long) {
        seeks.add(positionMs)
        this.positionMs = positionMs
    }

    override fun setSpeed(speed: Float) {
        appliedSpeed = speed
    }

    override fun playFromPeer(track: Track) {
        loaded.add(track)
        currentTrack = track
        positionMs = 0
        isPlaying = true
    }
}

class FakeStore : TogetherStore {
    override val lastRoom = MutableStateFlow<LastRoom?>(null)
    var forgotten = false

    override fun memberToken(): String = "member-token-0123456789"

    override fun rememberRoom(code: String, token: String, partnerName: String) {
        lastRoom.value = LastRoom(code, token, partnerName)
    }

    override fun forgetRoom() {
        forgotten = true
        lastRoom.value = null
    }
}

class FakeRepository : MusicRepository {
    var results: List<Track> = emptyList()
    var failWith: Exception? = null
    val queries = mutableListOf<String>()

    override val availableProviders: List<Provider> = listOf(Provider.AUDIUS)

    override suspend fun searchTracks(query: String, filter: Provider?): List<Track> {
        queries.add(query)
        failWith?.let { throw it }
        return results
    }

    override suspend fun fetchLocalTracks(): List<Track> = emptyList()
    override suspend fun fetchServerTracks(filter: Provider?): List<Track> = emptyList()
    override suspend fun relatedTracks(seed: Track): List<Track> = emptyList()
    override suspend fun downloadTrack(track: Track) = Unit
    override suspend fun deleteDownloadedTrack(trackId: String) = Unit
    override suspend fun isTrackDownloaded(trackId: String): Boolean = false
}

class TestClock(var nowMs: Long = 1_000_000) : WallClock {
    override fun nowMs(): Long = nowMs

    fun advance(byMs: Long) {
        nowMs += byMs
    }
}

fun track(id: String = "auralis_global_abc", title: String = "A Song"): Track = Track(
    id = id,
    title = title,
    artist = "An Artist",
    albumArtUrl = null,
    mediaUrl = "https://audius.co/stream.mp3",
    durationMs = 240_000,
)

class FakeRecorder : TogetherRecorder {
    var sessionId: String? = null
    val sharedPlays = mutableListOf<Pair<String, String>>() // session id to track id
    val events = mutableListOf<Triple<String, String?, Boolean>>() // type, payload, fromMe
    var ended = false

    override suspend fun beginSession(roomCode: String, partnerName: String?, wasHost: Boolean): String {
        val id = "session-$roomCode"
        sessionId = id
        return id
    }

    override suspend fun recordSharedPlay(
        sessionId: String,
        track: Track,
        partnerName: String?,
        addedByMe: Boolean,
    ) {
        sharedPlays.add(sessionId to track.id)
    }

    override suspend fun recordEvent(
        sessionId: String,
        type: String,
        payload: String?,
        fromMe: Boolean,
        trackId: String?,
    ) {
        events.add(Triple(type, payload, fromMe))
    }

    override suspend fun endSession(sessionId: String) {
        ended = true
    }
}
