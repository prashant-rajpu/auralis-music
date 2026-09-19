package com.auralis.app.together

import android.os.Handler
import android.os.Looper
import com.auralis.app.domain.model.Track
import com.auralis.app.playback.PlaybackManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real player behind [TogetherPlayer].
 *
 * Every command is posted to the main thread, and that is the entire point of this class existing.
 * ExoPlayer enforces thread affinity: it is built on the main thread and throws
 * `IllegalStateException: Player is accessed on the wrong thread` if touched from anywhere else.
 * A session runs its message stream and its sync loop on one background thread — deliberately, so
 * they cannot race each other — so without this hop every correction would kill the app.
 *
 * Reads need no hop: they come from `StateFlow`s, which are safe from any thread.
 */
@Singleton
class PlaybackManagerTogetherPlayer @Inject constructor(
    private val playback: PlaybackManager,
) : TogetherPlayer {

    private val main = Handler(Looper.getMainLooper())

    override val currentTrack: Track? get() = playback.currentTrack.value
    override val positionMs: Long get() = playback.currentPositionMs.value
    override val durationMs: Long get() = playback.durationMs.value
    override val isPlaying: Boolean get() = playback.isPlaying.value

    override fun play() = onMain { playback.play() }
    override fun pause() = onMain { playback.pause() }
    override fun seekTo(positionMs: Long) = onMain { playback.seekTo(positionMs) }

    /** The quiet setter: a drift nudge is not a thing the user did, so it raises no banner. */
    override fun setSpeed(speed: Float) = onMain { playback.setSyncSpeed(speed) }

    override fun playFromPeer(track: Track) = onMain { playback.playTrack(track) }

    /**
     * Posts even when already on the main thread, so ordering is the same either way: a seek
     * issued before a play always arrives before it.
     */
    private inline fun onMain(crossinline block: () -> Unit) {
        main.post { block() }
    }
}
