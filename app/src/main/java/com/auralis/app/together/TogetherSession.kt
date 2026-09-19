package com.auralis.app.together

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.auralis.app.BuildConfig
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** The wall clock, injected so a test can drive an hour of a session in milliseconds. */
fun interface WallClock {
    fun nowMs(): Long
}

data class TogetherRoom(
    val code: String,
    val secret: String?,
    val memberId: String,
    val hostId: String,
    val members: List<Member> = emptyList(),
    val encryption: EncryptionStrength = EncryptionStrength.CODE_ONLY,
) {
    val isHost: Boolean get() = memberId.isNotEmpty() && memberId == hostId
    val partner: Member? get() = members.firstOrNull { it.id != memberId }
}

data class TogetherChatMessage(
    val senderId: String,
    val senderName: String,
    val serverMs: Long,
    val text: String,
    val isMine: Boolean,
)

data class TogetherReaction(val senderId: String, val senderName: String, val emoji: String)

data class TogetherUiState(
    val connection: TogetherConnection = TogetherConnection.Idle,
    val room: TogetherRoom? = null,
    val syncState: SyncState = SyncState.IN_SYNC,
    val driftMs: Long = 0,
    val clockOffsetMs: Long = 0,
    val roundTripMs: Long = 0,
    val clockReady: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val chat: List<TogetherChatMessage> = emptyList(),
    /** Set when the peer is playing something this edition cannot get to at all. */
    val unplayable: TrackRef? = null,
    val message: String? = null,
) {
    val isActive: Boolean get() = room != null
}

/**
 * One listening session, from this phone's side.
 *
 * It owns the four pieces that have to agree with each other: the transport, the clock, the drift
 * controller, and the room's key. Most of the interesting behaviour is about not fighting itself.
 *
 * **Nobody is "the follower".** Either partner can press play, and whoever acted last is what
 * everyone syncs to — which is how two people actually listen together, rather than one driving
 * and one watching. That only works because a member ignores its own messages coming back, and
 * because a correction never triggers a broadcast: applying a seek we were *told* to make must not
 * look like a seek we chose. For [BROADCAST_QUIET_MS] after any correction, changes seen in the
 * player are ours to obey, not ours to announce.
 *
 * Everything runs on one thread. The message stream and the sync loop both touch the same handful
 * of fields, and a race between them would show up as drift nobody could reproduce.
 */
@Singleton
class TogetherSession @Inject constructor(
    private val transport: TogetherTransport,
    private val store: TogetherStore,
    private val player: TogetherPlayer,
    private val repository: MusicRepository,
    private val clock: WallClock,
    @TogetherScope private val scope: CoroutineScope,
) {

    private val clockSync = ClockSync()
    private val syncController = SyncController()

    private val _state = MutableStateFlow(TogetherUiState())
    val state = _state.asStateFlow()

    private val _reactions = MutableSharedFlow<TogetherReaction>(extraBufferCapacity = 16)
    val reactions = _reactions.asSharedFlow()

    private var key: RoomKey? = null
    private var remote: RemotePlayback? = null
    private var jobs = mutableListOf<Job>()
    private var appliedSpeed = 1f

    /** Position sampled last tick, for spotting a stall the player does not report. */
    private var lastSampledPosition = -1L
    private var lastPositionMovedAtMs = 0L

    /** Suppresses broadcasting a change we made because we were told to. */
    private var quietUntilMs = 0L

    /** What we last told the room, so an unchanged state is not re-sent every tick. */
    private var lastBroadcast: Broadcast? = null

    private data class Broadcast(val trackKey: String, val isPlaying: Boolean, val positionMs: Long)

    // --- joining and leaving ---

    /** Creates a room, and the secret that keeps its chat out of the relay's reach. */
    suspend fun host(displayName: String): Result<Invite> {
        val credentials = transport.createRoom().getOrElse { return Result.failure(it) }
        val invite = Invite(credentials.code, TogetherCrypto.generateRoomSecret())
        store.rememberRoom(credentials.code, credentials.hostToken, partnerName = "")
        open(invite, credentials.hostToken, displayName)
        return Result.success(invite)
    }

    fun join(invite: Invite, displayName: String) {
        val token = store.memberToken()
        store.rememberRoom(invite.code, token, partnerName = "")
        open(invite, token, displayName)
    }

    /**
     * Walks back into the room this phone was in before it closed or crashed.
     *
     * The invite's secret is deliberately not stored beside the code: it belongs to the invite, and
     * a resumed session is not a new one. Chat in a resumed session falls back to the code-derived
     * key, which [TogetherRoom.encryption] reports honestly.
     */
    fun resumeLastRoom(displayName: String): Boolean {
        val last = store.lastRoom.value ?: return false
        open(Invite(last.code, secret = null), last.token, displayName)
        return true
    }

    fun leave() {
        transport.leave()
        stop()
        if (appliedSpeed != 1f) {
            player.setSpeed(1f)
            appliedSpeed = 1f
        }
        store.forgetRoom()
        _state.value = TogetherUiState()
    }

    private fun open(invite: Invite, token: String, displayName: String) {
        stop()
        key = TogetherCrypto.deriveKey(invite.code, invite.secret)
        clockSync.reset()
        syncController.reset()
        remote = null
        lastBroadcast = null
        appliedSpeed = 1f
        quietUntilMs = 0L

        _state.value = TogetherUiState(
            connection = TogetherConnection.Connecting,
            room = TogetherRoom(
                code = invite.code,
                secret = invite.secret,
                memberId = "",
                hostId = "",
                encryption = invite.strength,
            ),
        )

        transport.join(invite.code, token, displayName)
        jobs += scope.launch { transport.messages.collect(::handle) }
        jobs += scope.launch { collectConnection() }
        jobs += scope.launch { pingLoop() }
        jobs += scope.launch { syncLoop() }
    }

    private fun stop() {
        jobs.forEach(Job::cancel)
        jobs = mutableListOf()
    }

    // --- things the user does ---

    fun sendChat(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val sealed = key?.let { TogetherCrypto.seal(it, body) } ?: return
        transport.send(TogetherClientMessage.Chat(sealed))
    }

    fun sendReaction(emoji: String) {
        transport.send(TogetherClientMessage.Reaction(emoji.take(16)))
    }

    fun addToSharedQueue(track: Track) {
        transport.send(TogetherClientMessage.QueueAdd(TogetherProtocol.trackRef(track)))
    }

    fun removeFromSharedQueue(ref: TrackRef) {
        transport.send(TogetherClientMessage.QueueRemove(ref.providerId))
    }

    /** Tells the room where we are. Called when the user acts, and when the tick spots that they did. */
    fun broadcastLocalPlayback() {
        val track = player.currentTrack ?: return
        val ref = TogetherProtocol.trackRef(track)
        transport.send(
            TogetherClientMessage.Playback(
                track = ref,
                positionMs = player.positionMs,
                isPlaying = player.isPlaying,
                speed = 1f,
            ),
        )
        lastBroadcast = Broadcast(ref.key, player.isPlaying, player.positionMs)
    }

    // --- the loops ---

    private suspend fun collectConnection() {
        transport.connection.collect { connection ->
            _state.update { current ->
                current.copy(
                    connection = connection,
                    room = if (connection is TogetherConnection.Connected) {
                        current.room?.copy(memberId = connection.memberId, hostId = connection.hostId)
                    } else {
                        current.room
                    },
                )
            }
            // A new socket is a new path; the old offset was measured on a route that is gone.
            if (connection is TogetherConnection.Reconnecting) clockSync.reset()
        }
    }

    @VisibleForTesting
    internal suspend fun handle(message: TogetherServerMessage) {
        when (message) {
            is TogetherServerMessage.Welcome -> {
                // Identity first, separately: whether a message in the snapshot is ours depends on
                // knowing who we are, and doing both in one update would read the old value.
                _state.update {
                    it.copy(
                        room = it.room?.copy(
                            memberId = message.memberId,
                            hostId = message.hostId,
                            members = message.state.members,
                        ),
                        queue = message.state.queue,
                    )
                }
                _state.update {
                    it.copy(chat = message.state.chat.map { e -> decrypt(e, message.state.members) })
                }
                message.state.playback?.let { playback ->
                    remote = RemotePlayback(
                        trackKey = playback.track.key,
                        positionMs = playback.positionMs,
                        isPlaying = playback.isPlaying,
                        speed = playback.speed,
                        atServerMs = playback.atServerMs,
                        durationMs = playback.track.durationMs,
                    )
                    adopt(playback.track)
                }
            }

            is TogetherServerMessage.Pong -> {
                clockSync.observe(
                    sentAtMs = message.at,
                    serverMs = message.serverMs,
                    receivedAtMs = clock.nowMs(),
                )
                _state.update {
                    it.copy(
                        clockOffsetMs = clockSync.offsetMs,
                        roundTripMs = clockSync.bestRoundTripMs,
                        clockReady = clockSync.isSynced,
                    )
                }
            }

            is TogetherServerMessage.Presence -> _state.update {
                it.copy(room = it.room?.copy(members = message.members, hostId = message.hostId))
            }

            is TogetherServerMessage.Playback -> {
                if (isOurs(message.senderId)) return
                remote = RemotePlayback(
                    trackKey = message.track.key,
                    positionMs = message.positionMs,
                    isPlaying = message.isPlaying,
                    speed = message.speed,
                    atServerMs = message.serverMs,
                    durationMs = message.track.durationMs,
                )
                adopt(message.track)
            }

            is TogetherServerMessage.Seek -> {
                if (isOurs(message.senderId)) return
                remote = remote?.copy(positionMs = message.positionMs, atServerMs = message.serverMs)
            }

            is TogetherServerMessage.Queue -> _state.update { it.copy(queue = message.items) }

            is TogetherServerMessage.Chat -> _state.update {
                val entry = ChatEntry(message.senderId, message.serverMs, message.ciphertext)
                it.copy(chat = (it.chat + decrypt(entry, it.room?.members.orEmpty())).takeLast(MAX_CHAT))
            }

            is TogetherServerMessage.Reaction -> {
                if (isOurs(message.senderId)) return
                _reactions.tryEmit(
                    TogetherReaction(message.senderId, nameOf(message.senderId), message.emoji),
                )
            }

            is TogetherServerMessage.Failure ->
                _state.update { it.copy(message = message.message.ifBlank { message.code }) }
        }
    }

    /**
     * Pings hard at first so the offset is usable within about a second, then settles down. Clock
     * sync is the one thing that has to be right before anything else can be.
     */
    private suspend fun pingLoop() {
        var sent = 0
        while (currentCoroutineContext().isActive) {
            transport.send(TogetherClientMessage.Ping(at = clock.nowMs()))
            sent++
            delay(if (sent < BURST_PINGS) BURST_INTERVAL_MS else STEADY_INTERVAL_MS)
        }
    }

    private suspend fun syncLoop() {
        while (currentCoroutineContext().isActive) {
            delay(TICK_MS)
            tick()
        }
    }

    @VisibleForTesting
    internal fun tick() {
        sampleStall()

        val target = remote?.copy(peerBuffering = peerIsBuffering()) ?: return
        if (!clockSync.isSynced) return

        // Before correcting anything: did the person holding *this* phone just do something? If
        // they hit pause, the room should pause. Checking after the correction would mean the
        // sync loop un-pauses them a fraction of a second later, which is the single most
        // infuriating thing a feature like this can do.
        if (takeControlIfUserActed()) return

        val track = player.currentTrack
        val local = LocalPlayback(
            trackKey = track?.let { TogetherProtocol.trackRef(it).key }.orEmpty(),
            positionMs = player.positionMs,
            isPlaying = player.isPlaying,
            isBuffering = isStalled(),
        )

        val decision = syncController.decide(local, target, clockSync.serverNow(clock.nowMs()))
        _state.update { it.copy(syncState = decision.state, driftMs = decision.driftMs) }

        // A track change is handled when the message arrives; re-triggering it here would restart
        // the load on every tick while it is still loading.
        if (decision.state == SyncState.LOADING_TRACK || decision.state == SyncState.STALE) return

        var corrected = false
        decision.seekToMs?.let {
            player.seekTo(it)
            corrected = true
        }
        if (decision.speed != appliedSpeed) {
            player.setSpeed(decision.speed)
            appliedSpeed = decision.speed
            corrected = true
        }
        if (decision.shouldPlay && !player.isPlaying) {
            player.play()
            corrected = true
        } else if (!decision.shouldPlay && player.isPlaying) {
            player.pause()
            corrected = true
        }

        if (corrected) {
            quietUntilMs = clock.nowMs() + BROADCAST_QUIET_MS
            // We now match the room by construction. Recording that stops the next tick reporting
            // it back as though we had chosen it.
            noteBroadcast()
        } else {
            noteBroadcast()
        }
    }

    /**
     * Notices the person holding this phone pressing play, skipping or scrubbing — including from
     * the notification or a Bluetooth button, which no in-app callback would catch — tells the
     * room, and makes this phone the thing everyone else syncs to.
     *
     * That last part is what "either of you can drive" means in practice: taking control is just
     * replacing the reference state with our own. Without it the next tick would dutifully undo
     * what the user just did, because the peer's last message still says otherwise.
     */
    private fun takeControlIfUserActed(): Boolean {
        if (clock.nowMs() < quietUntilMs) return false
        val track = player.currentTrack ?: return false
        val previous = lastBroadcast ?: return false
        val key = TogetherProtocol.trackRef(track).key

        // Playing on advances the position by about a tick. Anything much larger, in either
        // direction, is someone's thumb on the scrubber.
        val scrubbed = abs(player.positionMs - previous.positionMs) > JUMP_MS
        if (previous.trackKey == key && previous.isPlaying == player.isPlaying && !scrubbed) {
            return false
        }

        broadcastLocalPlayback()
        remote = RemotePlayback(
            trackKey = key,
            positionMs = player.positionMs,
            isPlaying = player.isPlaying,
            speed = 1f,
            atServerMs = clockSync.serverNow(clock.nowMs()),
            durationMs = player.durationMs,
        )
        syncController.reset()
        return true
    }

    // --- resolving what the peer is playing, in this edition ---

    private fun adopt(ref: TrackRef) {
        val current = player.currentTrack
        if (current != null && TogetherProtocol.trackRef(current).key == ref.key) return

        when (TogetherProtocol.availability(ref, BuildConfig.HAS_SCRAPED_SOURCES)) {
            TrackAvailability.DIRECT -> {
                _state.update { it.copy(unplayable = null) }
                player.playFromPeer(TogetherProtocol.toTrack(ref))
                quietUntilMs = clock.nowMs() + BROADCAST_QUIET_MS
                noteBroadcast()
            }

            TrackAvailability.FALLBACK_SEARCH -> {
                jobs += scope.launch { resolveBySearch(ref) }
            }

            TrackAvailability.UNAVAILABLE -> {
                _state.update { it.copy(unplayable = ref) }
                player.pause()
            }
        }
    }

    /**
     * This edition cannot resolve the sender's id — a `play` build handed a YouTube track, or a
     * file that lives on the other phone. Find our own copy rather than desyncing in silence.
     */
    private suspend fun resolveBySearch(ref: TrackRef) {
        val found = runCatching { repository.searchTracks(TogetherProtocol.searchQuery(ref), null) }
            .onFailure { Log.w(TAG, "Could not search for a peer's track", it) }
            .getOrDefault(emptyList())
            .firstOrNull()

        if (found == null) {
            _state.update { it.copy(unplayable = ref) }
            player.pause()
            return
        }
        _state.update { it.copy(unplayable = null) }
        player.playFromPeer(found)
        quietUntilMs = clock.nowMs() + BROADCAST_QUIET_MS
        noteBroadcast()
    }

    /** Remembers where we are without telling anyone, because they already know. */
    private fun noteBroadcast() {
        val track = player.currentTrack ?: return
        lastBroadcast = Broadcast(
            TogetherProtocol.trackRef(track).key,
            player.isPlaying,
            player.positionMs,
        )
    }

    // --- small helpers ---

    private fun sampleStall() {
        val position = player.positionMs
        if (position != lastSampledPosition) {
            lastSampledPosition = position
            lastPositionMovedAtMs = clock.nowMs()
        }
    }

    private fun isStalled(): Boolean =
        player.isPlaying && clock.nowMs() - lastPositionMovedAtMs > STALL_MS

    private fun peerIsBuffering(): Boolean =
        _state.value.room?.members.orEmpty().any { it.buffering && !isOurs(it.id) }

    private fun isOurs(memberId: String): Boolean {
        val mine = _state.value.room?.memberId
        return !mine.isNullOrEmpty() && memberId == mine
    }

    private fun nameOf(memberId: String): String =
        _state.value.room?.members?.firstOrNull { it.id == memberId }?.name ?: "Them"

    private fun decrypt(entry: ChatEntry, members: List<Member>): TogetherChatMessage {
        val opened = key?.let { TogetherCrypto.open(it, entry.ciphertext) }
        return TogetherChatMessage(
            senderId = entry.senderId,
            senderName = members.firstOrNull { it.id == entry.senderId }?.name ?: "Them",
            serverMs = entry.serverMs,
            // Shown rather than hidden: silently dropping a message we cannot open would look
            // like the other person never sent anything.
            text = opened ?: UNREADABLE,
            isMine = isOurs(entry.senderId),
        )
    }

    companion object {
        /** Sent with a different invite; this phone has no key for it. */
        const val UNREADABLE = "…"

        private const val TAG = "TogetherSession"
        internal const val TICK_MS = 500L
        private const val BURST_PINGS = 5
        private const val BURST_INTERVAL_MS = 250L
        private const val STEADY_INTERVAL_MS = 5_000L
        private const val STALL_MS = 700L

        /** Long enough to cover a seek settling, short enough not to swallow a real user action. */
        internal const val BROADCAST_QUIET_MS = 1_500L

        /** A position move bigger than this in one tick is a scrub, not playing on. */
        private const val JUMP_MS = 2_000L

        private const val MAX_CHAT = 100
    }
}
