package com.auralis.app.together

import com.auralis.app.domain.model.Track
import com.auralis.app.playback.PlaybackManager
import javax.inject.Inject
import javax.inject.Singleton

/** The real player behind [TogetherPlayer]. Deliberately nothing but forwarding. */
@Singleton
class PlaybackManagerTogetherPlayer @Inject constructor(
    private val playback: PlaybackManager,
) : TogetherPlayer {

    override val currentTrack: Track? get() = playback.currentTrack.value
    override val positionMs: Long get() = playback.currentPositionMs.value
    override val durationMs: Long get() = playback.durationMs.value
    override val isPlaying: Boolean get() = playback.isPlaying.value

    override fun play() = playback.play()
    override fun pause() = playback.pause()
    override fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    override fun setSpeed(speed: Float) = playback.setPlaybackSpeed(speed)
    override fun playFromPeer(track: Track) = playback.playTrack(track)
}
