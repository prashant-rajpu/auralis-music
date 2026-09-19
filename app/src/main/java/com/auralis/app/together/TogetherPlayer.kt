package com.auralis.app.together

import com.auralis.app.domain.model.Track

/**
 * The slice of the player a session needs.
 *
 * It exists so the session logic can be tested. Everything hard about Together — drift, echo
 * suppression, a peer on a track this edition cannot resolve, a reconnection mid-song — is decided
 * in [TogetherSession], and none of it could be checked here if it were welded to
 * `PlaybackManager` and an ExoPlayer that needs a device to exist.
 *
 * Deliberately state plus commands and nothing derived. Buffering, for instance, is worked out by
 * the session from successive position samples, because the session is the part with a clock.
 */
interface TogetherPlayer {
    val currentTrack: Track?
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)

    /** Starts [track], which arrived from a peer and carries no stream URL of its own. */
    fun playFromPeer(track: Track)
}
