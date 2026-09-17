package com.auralis.app.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.auralis.app.domain.model.SoundProfile
import com.auralis.app.domain.model.Track
import com.auralis.app.network.JamState
import com.auralis.app.network.JamWebSocketClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jamClient: JamWebSocketClient,
    val audioEffectManager: AudioEffectManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var playerA: ExoPlayer = ExoPlayer.Builder(context).build()
    private var playerB: ExoPlayer = ExoPlayer.Builder(context).build()

    // playerA is initially active; playerB is standby for crossfade
    private var activePlayer: ExoPlayer = playerA
    private var standbyPlayer: ExoPlayer = playerB

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue = _queue.asStateFlow()

    private var currentIndex = -1
    private var isCrossfading = false
    private var positionTickerJob: Job? = null
    private var crossfadeJob: Job? = null

    init {
        setupPlayer(playerA)
        setupPlayer(playerB)
        startPositionTicker()
        observeJamState()
        audioEffectManager.attachAudioSession(activePlayer.audioSessionId)
    }

    private fun setupPlayer(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (player == activePlayer) {
                    _isPlaying.value = playing
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player == activePlayer) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = player.duration.coerceAtLeast(0L)
                        audioEffectManager.attachAudioSession(player.audioSessionId)
                    } else if (playbackState == Player.STATE_ENDED && !isCrossfading) {
                        // Naturally advance to next track in queue with crossfade
                        skipNext()
                    }
                }
            }
        })
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                if (activePlayer.isPlaying) {
                    val pos = activePlayer.currentPosition.coerceAtLeast(0L)
                    val dur = activePlayer.duration.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }

                    // Check for automatic crossfade trigger before song ends
                    val crossfadeDurationMs = audioEffectManager.currentProfile.crossfadeDurationSec * 1000L
                    if (dur > crossfadeDurationMs && (dur - pos) <= crossfadeDurationMs && !isCrossfading) {
                        if (currentIndex + 1 < _queue.value.size) {
                            skipNext()
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun observeJamState() {
        scope.launch {
            jamClient.jamState.collect { state ->
                when (state) {
                    is JamState.SyncPlayback -> {
                        activePlayer.seekTo(state.position)
                        if (state.isPlaying) activePlayer.play() else activePlayer.pause()
                    }
                    else -> {}
                }
            }
        }
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        _queue.value = newQueue
        currentIndex = newQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        crossfadeJob?.cancel()
        isCrossfading = false

        standbyPlayer.stop()
        standbyPlayer.volume = 1.0f

        activePlayer.stop()
        activePlayer.volume = 1.0f

        loadMediaToPlayer(activePlayer, track)
        activePlayer.prepare()
        activePlayer.play()

        _currentTrack.value = track
        _isPlaying.value = true
        _currentPositionMs.value = 0L
        _durationMs.value = track.durationMs

        audioEffectManager.attachAudioSession(activePlayer.audioSessionId)
        jamClient.broadcastPlaybackState(track.id, 0L, true)
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = currentIndex + 1
        if (nextIdx < q.size) {
            val nextTrack = q[nextIdx]
            currentIndex = nextIdx
            startCrossfadeTo(nextTrack)
        }
    }

    fun skipPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (activePlayer.currentPosition > 3000L) {
            // Restart current track
            activePlayer.seekTo(0L)
            _currentPositionMs.value = 0L
            return
        }
        val prevIdx = currentIndex - 1
        if (prevIdx >= 0) {
            val prevTrack = q[prevIdx]
            currentIndex = prevIdx
            startCrossfadeTo(prevTrack)
        }
    }

    private fun startCrossfadeTo(nextTrack: Track) {
        val crossfadeMs = (audioEffectManager.currentProfile.crossfadeDurationSec * 1000L).coerceIn(1000L, 12000L)

        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            isCrossfading = true

            // Prepare standby player with next song at 0 volume
            standbyPlayer.stop()
            standbyPlayer.volume = 0.0f
            loadMediaToPlayer(standbyPlayer, nextTrack)
            standbyPlayer.prepare()
            standbyPlayer.play()

            _currentTrack.value = nextTrack

            // Simultaneous cross-mixing volume interpolation
            val stepInterval = 50L
            val totalSteps = (crossfadeMs / stepInterval).coerceAtLeast(1L)

            for (step in 1..totalSteps) {
                val progress = step.toFloat() / totalSteps.toFloat()
                activePlayer.volume = (1.0f - progress).coerceIn(0f, 1f)
                standbyPlayer.volume = progress.coerceIn(0f, 1f)
                delay(stepInterval)
            }

            // Transition complete: finalize swap
            activePlayer.stop()
            activePlayer.volume = 1.0f
            standbyPlayer.volume = 1.0f

            // Swap players
            val temp = activePlayer
            activePlayer = standbyPlayer
            standbyPlayer = temp

            audioEffectManager.attachAudioSession(activePlayer.audioSessionId)
            _isPlaying.value = true
            isCrossfading = false

            jamClient.broadcastPlaybackState(nextTrack.id, 0L, true)
        }
    }

    private fun loadMediaToPlayer(player: ExoPlayer, track: Track) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.mediaUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.albumArtUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
    }

    fun seekTo(positionMs: Long) {
        activePlayer.seekTo(positionMs.coerceAtLeast(0L))
        _currentPositionMs.value = positionMs
        _currentTrack.value?.let { track ->
            jamClient.broadcastPlaybackState(track.id, positionMs, activePlayer.isPlaying)
        }
    }

    fun togglePlayPause() {
        if (activePlayer.isPlaying) {
            activePlayer.pause()
            _isPlaying.value = false
            _currentTrack.value?.let { track ->
                jamClient.broadcastPlaybackState(track.id, activePlayer.currentPosition, false)
            }
        } else {
            activePlayer.play()
            _isPlaying.value = true
            _currentTrack.value?.let { track ->
                jamClient.broadcastPlaybackState(track.id, activePlayer.currentPosition, true)
            }
        }
    }

    fun applySoundProfile(profile: SoundProfile) {
        audioEffectManager.applyProfile(profile)
    }
}
