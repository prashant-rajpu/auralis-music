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
    /** Null when this phone has no key for it — sent with a different invite. */
    val note: TogetherNote?,
    val isMine: Boolean,
)

/** Thinking of you, from them. Transient, so it is an event rather than state. */
data class TogetherKnock(val senderId: String, val senderName: String)

/** One step of the call handshake, already decrypted, from the person on the other end. */
data class CallSignal(val senderId: String, val senderName: String, val note: TogetherNote)

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
    /** Room time at which both phones fade out together. Null when no one has said goodnight. */
    val goodnightAtServerMs: Long? = null,
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
 * Everything runs on one thread, including every call that arrives from the UI. The message
 * stream, the sync loop and the buttons all touch the same handful of fields — among them a list
 * of jobs and a set of track keys, neither of which survives being mutated from two threads at
 * once. So the public API below does no work of its own: it hands the work to [scope], which is
 * single-threaded, and which therefore also keeps everything in the order it was asked for.
 */
@Singleton
class TogetherSession @Inject constructor(
    private val transport: TogetherTransport,
    private val store: TogetherStore,
    private val player: TogetherPlayer,
    private val repository: MusicRepository,
    private val recorder: TogetherRecorder,
    private val mailbox: TogetherMailbox,
    private val clock: WallClock,
    @TogetherScope private val scope: CoroutineScope,
) {

    private val clockSync = ClockSync()
    private val syncController = SyncController()

    private val _state = MutableStateFlow(TogetherUiState())
    val state = _state.asStateFlow()

    private val _reactions = MutableSharedFlow<TogetherReaction>(extraBufferCapacity = 16)
    val reactions = _reactions.asSharedFlow()

    private val _knocks = MutableSharedFlow<TogetherKnock>(extraBufferCapacity = 8)
    val knocks = _knocks.asSharedFlow()

    /**
     * The call handshake, routed out to whoever is running WebRTC.
     *
     * Buffered generously and never replayed: a candidate that arrives before the peer connection
     * is ready is useless, and one delivered twice is worse than one dropped.
     */
    private val _callSignals = MutableSharedFlow<CallSignal>(extraBufferCapacity = 64)
    val callSignals = _callSignals.asSharedFlow()

    /**
     * Where a call should look for a path between the two phones.
     *
     * Asked for on every welcome rather than when a call starts: the relay caches them for hours,
     * so it costs one frame per join and saves a round trip on the one path where a round trip is
     * felt — the seconds between "calling" and hearing her.
     */
    private val _iceServers = MutableStateFlow<List<IceServer>>(emptyList())
    val iceServers = _iceServers.asStateFlow()

    private var key: RoomKey? = null

    /** The row every play and note in this session is filed under, once there is a partner. */
    private var recordedSessionId: String? = null

    /** Filed once per track per session, not once per tick. */
    private val recordedTracks = mutableSetOf<String>()

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
        onSession { open(invite, credentials.hostToken, displayName) }
        return Result.success(invite)
    }

    fun join(invite: Invite, displayName: String) {
        val token = store.memberToken()
        store.rememberRoom(invite.code, token, partnerName = "")
        onSession { open(invite, token, displayName) }
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
        onSession { open(Invite(last.code, secret = null), last.token, displayName) }
        return true
    }

    fun leave() = onSession {
        recordedSessionId?.let { id -> runCatching { recorder.endSession(id) } }
        recordedSessionId = null
        recordedTracks.clear()
        transport.leave()
        stop()
        releaseSpeed()
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
        recordedSessionId = null
        recordedTracks.clear()

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

    /**
     * Runs [block] on the session's own thread.
     *
     * Every public entry point goes through here, so the fields below are only ever touched from
     * one place. A single-threaded dispatcher also preserves submission order, so a leave that
     * follows a send still happens after it.
     */
    private fun onSession(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    // --- things the user does ---

    fun sendChat(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        onSession { sendNote(TogetherNote.Text(body)) }
    }

    /** A song, with something said about it. It is the note that makes it a dedication. */
    fun dedicate(track: Track, note: String) = onSession {
        sendNote(TogetherNote.Dedication(TogetherProtocol.trackRef(track), note.trim().take(500)))
    }

    /** One line of what is playing, sent at the moment it plays. */
    fun sendLyricMoment(line: String, positionMs: Long) = onSession {
        val track = player.currentTrack ?: return@onSession
        val trimmed = line.trim().take(300)
        if (trimmed.isEmpty()) return@onSession
        sendNote(
            TogetherNote.LyricMoment(
                line = trimmed,
                track = TogetherProtocol.trackRef(track),
                positionMs = positionMs,
            ),
        )
    }

    fun knock() = onSession { sendNote(TogetherNote.Knock) }

    /** Puts one step of the call handshake on the wire, encrypted like everything else. */
    fun sendCallSignal(note: TogetherNote) = onSession { sendNote(note) }

    /**
     * Fade out on both phones at the same moment. Scheduled in room time rather than as a local
     * countdown, so "in twenty minutes" means the same instant on both sides of the world.
     */
    fun goodnightIn(delayMs: Long) = onSession {
        if (!clockSync.isSynced) return@onSession
        val at = clockSync.serverNow(clock.nowMs()) + delayMs.coerceAtLeast(0L)
        _state.update { it.copy(goodnightAtServerMs = at) }
        sendNote(TogetherNote.Goodnight(at))
    }

    fun cancelGoodnight() = onSession {
        _state.update { it.copy(goodnightAtServerMs = null) }
    }

    /**
     * Files the message, then sends it — in that order, and deliberately.
     *
     * A message written down before it goes out survives the send failing, the socket being down,
     * and the app being killed between the two. It stays in the outbox with the exact bytes that
     * were sealed, so the retry is the identical frame: the copy the relay echoes back carries the
     * same id and lands on this row rather than beside it.
     */
    private suspend fun sendNote(note: TogetherNote) {
        val sealed = key?.let { TogetherNotes.seal(it, note) } ?: return
        file(sealed, note)
        transport.send(TogetherClientMessage.Chat(sealed))
    }

    /** Writes an outgoing message to the mailbox as pending. Not for the call handshake. */
    private suspend fun file(sealed: String, note: TogetherNote) {
        if (note.storedKind == null) return
        val room = _state.value.room ?: return
        runCatching {
            mailbox.remember(
                StoredNote(
                    id = TogetherNotes.idFor(sealed),
                    roomCode = room.code,
                    senderId = room.memberId,
                    senderName = "",
                    fromMe = true,
                    note = note,
                    atMs = clock.nowMs(),
                    pending = true,
                    ciphertext = sealed,
                ),
            )
        }
    }

    /**
     * Puts back on the wire whatever never made it off this phone.
     *
     * Runs on every welcome, so a message typed during a dropped connection goes out the moment
     * the socket comes back rather than when the user notices and retypes it.
     */
    private suspend fun flushOutbox(roomCode: String) {
        val pending = runCatching { mailbox.outbox(roomCode) }.getOrNull().orEmpty()
        pending.forEach { stored ->
            stored.ciphertext?.let { transport.send(TogetherClientMessage.Chat(it)) }
        }
    }

    fun sendReaction(emoji: String) = onSession {
        transport.send(TogetherClientMessage.Reaction(emoji.take(16)))
    }

    fun addToSharedQueue(track: Track) = onSession {
        transport.send(TogetherClientMessage.QueueAdd(TogetherProtocol.trackRef(track)))
    }

    fun removeFromSharedQueue(ref: TrackRef) = onSession {
        transport.send(TogetherClientMessage.QueueRemove(ref.providerId))
    }

    /** Tells the room where we are, when something outside the session asks us to. */
    fun broadcastLocalPlayback() = onSession { broadcastNow() }

    /**
     * The same thing for callers already on the session thread. Kept separate from the public
     * entry point on purpose: the tick broadcasts and then immediately makes itself the reference
     * state, and deferring half of that pair would leave the two disagreeing for a beat.
     */
    private fun broadcastNow() {
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
                openMemoryIfPartnerIsHere()
                message.state.chat.forEach { entry ->
                    fileIncoming(entry, message.state.members)
                }
                _state.value.room?.code?.let { flushOutbox(it) }
                transport.send(TogetherClientMessage.Ice)
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

            is TogetherServerMessage.Presence -> {
                _state.update {
                    it.copy(room = it.room?.copy(members = message.members, hostId = message.hostId))
                }
                openMemoryIfPartnerIsHere()
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

            is TogetherServerMessage.Chat -> {
                val entry = ChatEntry(message.senderId, message.serverMs, message.ciphertext)
                val decrypted = decrypt(entry, _state.value.room?.members.orEmpty())
                val note = decrypted.note
                when {
                    // A knock is a moment, not a line in a transcript.
                    note is TogetherNote.Knock ->
                        if (!decrypted.isMine) {
                            _knocks.tryEmit(TogetherKnock(entry.senderId, decrypted.senderName))
                        }

                    note is TogetherNote.Goodnight ->
                        _state.update { it.copy(goodnightAtServerMs = note.atServerMs) }

                    // Our own signalling coming back is not an incoming call.
                    note != null && note.isCallSignalling ->
                        if (!decrypted.isMine) {
                            _callSignals.tryEmit(
                                CallSignal(entry.senderId, decrypted.senderName, note),
                            )
                        }

                    else -> _state.update { it.copy(chat = (it.chat + decrypted).takeLast(MAX_CHAT)) }
                }
                decrypted.note?.let { fileNote(it, fromMe = decrypted.isMine) }
                fileIncoming(entry, _state.value.room?.members.orEmpty())
            }

            is TogetherServerMessage.Reaction -> {
                if (isOurs(message.senderId)) return
                _reactions.tryEmit(
                    TogetherReaction(message.senderId, nameOf(message.senderId), message.emoji),
                )
            }

            is TogetherServerMessage.Ice -> {
                // An empty list would mean a call with nowhere to look, so the old one is kept.
                if (message.iceServers.isNotEmpty()) _iceServers.value = message.iceServers
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
        fadeOutIfGoodnightHasArrived()

        val target = remote?.copy(peerBuffering = peerIsBuffering()) ?: run {
            releaseSpeed()
            return
        }
        if (!clockSync.isSynced) {
            releaseSpeed()
            return
        }

        // Before correcting anything: did the person holding *this* phone just do something? If
        // they hit pause, the room should pause. Checking after the correction would mean the
        // sync loop un-pauses them a fraction of a second later, which is the single most
        // infuriating thing a feature like this can do.
        if (takeControlIfUserActed()) {
            releaseSpeed()
            return
        }

        val track = player.currentTrack
        val local = LocalPlayback(
            trackKey = track?.let { TogetherProtocol.trackRef(it).key }.orEmpty(),
            positionMs = player.positionMs,
            isPlaying = player.isPlaying,
            isBuffering = isStalled(),
        )

        val decision = syncController.decide(
            local = local,
            remote = target,
            nowServerMs = clockSync.serverNow(clock.nowMs()),
            precise = clockSync.isReliable,
        )
        _state.update { it.copy(syncState = decision.state, driftMs = decision.driftMs) }

        // A track change is handled when the message arrives; re-triggering it here would restart
        // the load on every tick while it is still loading.
        if (decision.state == SyncState.LOADING_TRACK || decision.state == SyncState.STALE) {
            releaseSpeed()
            return
        }

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

        broadcastNow()
        fileSharedPlay(track)
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
                val local = TogetherProtocol.toTrack(ref)
                player.playFromPeer(local)
                quietUntilMs = clock.nowMs() + BROADCAST_QUIET_MS
                noteBroadcast()
                fileSharedPlay(local)
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
        fileSharedPlay(found)
    }

    /**
     * A room you are sitting in alone is not a session the two of you had, so nothing is filed
     * until someone else is actually there.
     */
    private fun openMemoryIfPartnerIsHere() {
        if (recordedSessionId != null) return
        val room = _state.value.room ?: return
        val partner = room.partner ?: return
        scope.launch {
            val id = runCatching {
                recorder.beginSession(room.code, partner.name, room.isHost)
            }.getOrNull() ?: return@launch
            recordedSessionId = id
            // The track already playing when they arrived counts too.
            player.currentTrack?.let { fileSharedPlay(it) }
        }
    }

    /** Once per track per session: Our Songs counts sessions, not seconds. */
    private fun fileSharedPlay(track: Track) {
        val id = recordedSessionId ?: return
        val key = TogetherProtocol.trackRef(track).key
        if (!recordedTracks.add(key)) return
        val partner = _state.value.room?.partner?.name
        scope.launch {
            runCatching { recorder.recordSharedPlay(id, track, partner, addedByMe = true) }
        }
    }

    private fun fileNote(note: TogetherNote, fromMe: Boolean) {
        val id = recordedSessionId ?: return
        val (type, payload, trackId) = when (note) {
            is TogetherNote.Text -> Triple(TogetherRecorder.EVENT_CHAT, note.body, null)
            is TogetherNote.Dedication -> Triple(
                TogetherRecorder.EVENT_DEDICATION,
                note.note,
                TogetherProtocol.toTrack(note.track).id,
            )
            is TogetherNote.LyricMoment -> Triple(
                TogetherRecorder.EVENT_LYRIC,
                note.line,
                TogetherProtocol.toTrack(note.track).id,
            )
            is TogetherNote.Knock -> Triple(TogetherRecorder.EVENT_KNOCK, null, null)
            // Not memories: a sleep timer and the handshake for a call are both plumbing. A
            // session timeline should read back as what the two of you did, not as a packet log.
            is TogetherNote.Goodnight -> return
            else -> return
        }
        scope.launch {
            runCatching { recorder.recordEvent(id, type, payload, fromMe, trackId) }
        }
    }

    /**
     * The sleep timer both phones share. Compared in room time, so the two fade out on the same
     * beat rather than however many seconds apart their clocks happen to be.
     */
    private fun fadeOutIfGoodnightHasArrived() {
        val at = _state.value.goodnightAtServerMs ?: return
        if (!clockSync.isSynced) return
        if (clockSync.serverNow(clock.nowMs()) < at) return

        player.pause()
        _state.update { it.copy(goodnightAtServerMs = null) }
        // Nothing to correct against any more, and nothing to announce: they are stopping too.
        remote = null
        quietUntilMs = clock.nowMs() + BROADCAST_QUIET_MS
    }

    /**
     * Puts the speed back to normal.
     *
     * Every path that leaves the correction loop early has to come through here. A nudge is two
     * percent off, which nobody hears for a second — and which sounds exactly like a broken app if
     * a track change, a stale peer or a lost clock strands it there for the rest of the song.
     */
    private fun releaseSpeed() {
        if (appliedSpeed == 1f) return
        player.setSpeed(1f)
        appliedSpeed = 1f
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

    /**
     * Files a message that came off the wire, whoever sent it.
     *
     * Our own messages come back too, and that echo is what turns a pending row into a delivered
     * one: the relay's stamp is the clock both phones agree on, so it replaces the local guess at
     * the same time. The insert is idempotent — a rejoin replays the last fifty — and the mark is
     * harmless on a row that was never pending.
     */
    private suspend fun fileIncoming(entry: ChatEntry, members: List<Member>) {
        val room = _state.value.room ?: return
        val note = key?.let { TogetherNotes.open(it, entry.ciphertext) } ?: return
        if (note.storedKind == null) return
        val id = TogetherNotes.idFor(entry.ciphertext)
        runCatching {
            mailbox.remember(
                StoredNote(
                    id = id,
                    roomCode = room.code,
                    senderId = entry.senderId,
                    senderName = members.firstOrNull { it.id == entry.senderId }?.name.orEmpty(),
                    fromMe = isOurs(entry.senderId),
                    note = note,
                    atMs = entry.serverMs,
                ),
            )
            mailbox.markDelivered(id, entry.serverMs)
        }
    }

    private fun decrypt(entry: ChatEntry, members: List<Member>): TogetherChatMessage {
        // A note we cannot open is kept as null and shown as such, rather than dropped: silently
        // hiding it would look like the other person never sent anything.
        val note = key?.let { TogetherNotes.open(it, entry.ciphertext) }
        return TogetherChatMessage(
            senderId = entry.senderId,
            senderName = members.firstOrNull { it.id == entry.senderId }?.name ?: "Them",
            serverMs = entry.serverMs,
            note = note,
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
