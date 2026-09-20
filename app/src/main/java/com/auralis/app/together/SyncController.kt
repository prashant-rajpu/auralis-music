package com.auralis.app.together

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Decides what this phone should do to stay in the same moment of the same song as the other one.
 *
 * The old ntfy path tolerated 1500 ms of drift and corrected with a seek, which is exactly the
 * wrong shape: a second and a half apart is plainly not together, and a seek is audible every time.
 * So there are two corrections, not one.
 *
 * | Drift | Response |
 * |---|---|
 * | > 400 ms | Seek. Already obviously wrong, so a jump costs nothing. |
 * | 120–400 ms | Nudge the speed to 0.98× or 1.02× until it closes. Inaudible, and no jump. |
 * | < 120 ms | Leave it alone. |
 * | Peer buffering | Hold, rather than running ahead of them. |
 *
 * A nudge that has not closed the gap within [SyncConfig.maxNudgeDurationMs] gives up and seeks:
 * if 2% is not catching up, the gap is not the kind a nudge can close.
 *
 * Pure apart from the nudge it is currently running, and every timestamp is passed in, so the whole
 * drift table is testable without a player, a network, or a second phone.
 */
data class SyncConfig(
    val hardSeekMs: Long = 400,
    val nudgeMs: Long = 120,
    /** Hysteresis: a running nudge keeps going until drift falls below this, not merely below nudgeMs. */
    val releaseMs: Long = 60,
    val maxNudgeDurationMs: Long = 3_000,
    val slowSpeed: Float = 0.98f,
    val fastSpeed: Float = 1.02f,
    /**
     * Beyond this, a playback snapshot is history rather than a position. Projecting forward from a
     * stamp this old would put us hours into a track nobody is playing.
     */
    val staleAfterMs: Long = 30 * 60 * 1000,
    /**
     * The only gap worth seeking when the clock itself is only good to a fraction of a second.
     * Wide enough that measurement error cannot trigger it; narrow enough to still catch a real
     * desync, like one side having skipped.
     */
    val coarseSeekMs: Long = 2_000,
)

/** What the other side last told us, in room time. */
data class RemotePlayback(
    val trackKey: String,
    val positionMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
    val atServerMs: Long,
    val durationMs: Long = 0,
    /** They are still loading. Running ahead of them is worse than waiting. */
    val peerBuffering: Boolean = false,
)

data class LocalPlayback(
    val trackKey: String,
    val positionMs: Long,
    val isPlaying: Boolean,
    val isBuffering: Boolean = false,
)

/** Also the sync indicator's model: the user should be able to see what the connection is doing. */
enum class SyncState {
    IN_SYNC,
    /** Nudging the speed to close a small gap. */
    CATCHING_UP,
    /** Just seeked. Audible, and worth showing so it does not look like a glitch. */
    CORRECTED,
    /** The peer moved to a different song and we have not loaded it yet. */
    LOADING_TRACK,
    /** Holding for a peer who is buffering. */
    WAITING_FOR_PEER,
    PAUSED,
    /** The last thing we heard is too old to act on; waiting for a fresh update. */
    STALE,
}

data class SyncDecision(
    val state: SyncState,
    /** Null means do not seek. */
    val seekToMs: Long? = null,
    val speed: Float = 1f,
    val shouldPlay: Boolean = false,
    /** Set when the peer is on a song we are not; the caller loads it and applies [seekToMs]. */
    val loadTrackKey: String? = null,
    /** Positive means we are ahead of them. Logged for the soak test and shown in the indicator. */
    val driftMs: Long = 0,
)

class SyncController(private val config: SyncConfig = SyncConfig()) {

    private var nudgeStartedAtMs: Long? = null
    private var nudgeSpeed: Float = 1f

    val isNudging: Boolean get() = nudgeStartedAtMs != null

    fun reset() {
        nudgeStartedAtMs = null
        nudgeSpeed = 1f
    }

    /**
     * Where the peer is *now*, projected from where they were when they told us. Clamped to the
     * track so a long silence cannot project past the end of it.
     */
    fun expectedPositionMs(remote: RemotePlayback, nowServerMs: Long): Long {
        val elapsed = (nowServerMs - remote.atServerMs).coerceAtLeast(0L)
        val advanced =
            if (remote.isPlaying && !remote.peerBuffering) (elapsed * remote.speed.toDouble()).roundToLong()
            else 0L
        val projected = remote.positionMs + advanced
        val ceiling = if (remote.durationMs > 0) remote.durationMs else Long.MAX_VALUE
        return projected.coerceIn(0L, ceiling)
    }

    /**
     * [precise] is false when the clock was measured over a link too slow to trust to the
     * millisecond. The gap is then only worth acting on when it is far larger than the
     * measurement error, and never worth nudging: a biased offset makes a nudge lean one way and
     * stay there, which is exactly how one side ends up playing permanently slow.
     */
    fun decide(
        local: LocalPlayback,
        remote: RemotePlayback,
        nowServerMs: Long,
        precise: Boolean = true,
    ): SyncDecision {
        if (nowServerMs - remote.atServerMs > config.staleAfterMs) {
            reset()
            return SyncDecision(state = SyncState.STALE, shouldPlay = local.isPlaying)
        }

        val expected = expectedPositionMs(remote, nowServerMs)

        if (local.trackKey != remote.trackKey) {
            reset()
            return SyncDecision(
                state = SyncState.LOADING_TRACK,
                seekToMs = expected,
                shouldPlay = remote.isPlaying && !remote.peerBuffering,
                loadTrackKey = remote.trackKey,
            )
        }

        val drift = local.positionMs - expected

        // Their problem, not ours: hold where we are instead of drifting ahead while they load.
        if (remote.peerBuffering) {
            reset()
            return SyncDecision(state = SyncState.WAITING_FOR_PEER, driftMs = drift)
        }

        // Our problem: there is nothing to correct until we have audio. Seeking mid-buffer only
        // restarts the buffering.
        if (local.isBuffering) {
            reset()
            return SyncDecision(
                state = SyncState.CATCHING_UP,
                shouldPlay = remote.isPlaying,
                driftMs = drift,
            )
        }

        if (!remote.isPlaying) {
            reset()
            return SyncDecision(
                state = SyncState.PAUSED,
                seekToMs = if (abs(drift) > config.hardSeekMs) expected else null,
                driftMs = drift,
            )
        }

        val magnitude = abs(drift)

        if (!precise) {
            reset()
            return SyncDecision(
                state = if (magnitude > config.coarseSeekMs) SyncState.CORRECTED else SyncState.IN_SYNC,
                seekToMs = if (magnitude > config.coarseSeekMs) expected else null,
                shouldPlay = true,
                driftMs = drift,
            )
        }

        val nudgeExpired = nudgeStartedAtMs?.let { nowServerMs - it > config.maxNudgeDurationMs } == true
        if (magnitude > config.hardSeekMs || (nudgeExpired && magnitude >= config.nudgeMs)) {
            reset()
            return SyncDecision(
                state = SyncState.CORRECTED,
                seekToMs = expected,
                shouldPlay = true,
                driftMs = drift,
            )
        }

        if (nudgeExpired) reset()

        val shouldNudge = magnitude >= config.nudgeMs || (isNudging && magnitude >= config.releaseMs)
        if (shouldNudge) {
            if (nudgeStartedAtMs == null) nudgeStartedAtMs = nowServerMs
            // Ahead of them: run slower. Behind: run faster.
            nudgeSpeed = if (drift > 0) config.slowSpeed else config.fastSpeed
            return SyncDecision(
                state = SyncState.CATCHING_UP,
                speed = nudgeSpeed,
                shouldPlay = true,
                driftMs = drift,
            )
        }

        reset()
        return SyncDecision(state = SyncState.IN_SYNC, shouldPlay = true, driftMs = drift)
    }
}
