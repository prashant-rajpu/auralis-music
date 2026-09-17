package com.auralis.app.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.auralis.app.domain.model.SoundProfile
import com.auralis.app.domain.model.Track
import com.auralis.app.network.JamState
import com.auralis.app.network.JamWebSocketClient
import com.auralis.app.network.SponsorBlockManager
import com.auralis.app.network.YouTubeMusicApi
import com.auralis.app.network.YouTubeStreamResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class RepeatMode {
    OFF, ALL, ONE
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val jamClient: JamWebSocketClient,
    val audioEffectManager: AudioEffectManager,
    val streamResolver: YouTubeStreamResolver,
    val settingsPreferences: AuralisSettingsPreferences,
    val sponsorBlockManager: SponsorBlockManager,
    val youTubeMusicApi: YouTubeMusicApi,
    val personalizationManager: PersonalizationManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Call-compatible audio attributes: Music usage with handleAudioFocus = false
    // so music continues playing without being paused when on video/voice calls (WhatsApp, Instagram, Meet, etc.)
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private var playerA: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
        .build()

    private var playerB: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
        .build()

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

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode = _repeatMode.asStateFlow()

    // Live Jam / Together Mode Action notification banner
    private val _lastJamAction = MutableStateFlow<String?>(null)
    val lastJamAction = _lastJamAction.asStateFlow()

    // Real-time live emoji reactions (emoji, sender)
    private val _lastReaction = MutableStateFlow<Pair<String, String>?>(null)
    val lastReaction = _lastReaction.asStateFlow()

    // Romantic memory quote overlay (quote, sender)
    private val _lastMemoryQuote = MutableStateFlow<Pair<String, String>?>(null)
    val lastMemoryQuote = _lastMemoryQuote.asStateFlow()

    val jamSession = jamClient.currentSession
    val jamState = jamClient.jamState

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex = _currentQueueIndex.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _sleepTimerMinutesRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesRemaining = _sleepTimerMinutesRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    private var currentIndex = -1
        set(value) {
            field = value
            _currentQueueIndex.value = value
        }
    private var isCrossfading = false
    private var isFetchingRadio = false
    private var positionTickerJob: Job? = null
    private var crossfadeJob: Job? = null
    private var dismissJob: Job? = null
    private var tickCounter = 0
    private var mediaSession: MediaSession? = null

    init {
        setupPlayer(playerA)
        setupPlayer(playerB)
        startPositionTicker()
        observeJamState()
        audioEffectManager.attachAudioSession(activePlayer.audioSessionId)
        try {
            mediaSession = MediaSession.Builder(context, activePlayer)
                .setId("AuralisTogetherMediaSession")
                .build()
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to build MediaSession", e)
        }
    }

    fun sendJamReaction(emoji: String) {
        jamClient.broadcastReaction(emoji)
        val myName = jamSession.value?.username ?: "You"
        _lastReaction.value = Pair(emoji, myName)
        _lastJamAction.value = "$myName sent $emoji 💖"
        scheduleActionDismiss()
    }

    fun sendMemoryQuote(quote: String = "I love you jaanaa 💋") {
        jamClient.broadcastMemoryQuote(quote)
        val myName = jamSession.value?.username ?: "You"
        _lastMemoryQuote.value = Pair(quote, myName)
        _lastJamAction.value = "$myName: \"$quote\" 💌"
        scheduleActionDismiss()
    }

    fun clearMemoryQuote() {
        _lastMemoryQuote.value = null
    }

    fun clearReaction() {
        _lastReaction.value = null
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
                        _currentTrack.value?.let { personalizationManager.recordTrackPlay(it) }
                    } else if (playbackState == Player.STATE_ENDED && !isCrossfading) {
                        if (_repeatMode.value == RepeatMode.ONE) {
                            activePlayer.seekTo(0L)
                            activePlayer.play()
                        } else {
                            skipNext()
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlaybackManager", "Player error on active=${player == activePlayer}: ${error.errorCodeName}", error)
                if (player == activePlayer) {
                    val current = _currentTrack.value
                    if (current != null && current.isYouTubeTrack()) {
                        scope.launch {
                            try {
                                val freshUrl = streamResolver.resolveStreamUrl(current, forceRefresh = true)
                                if (freshUrl != current.mediaUrl) {
                                    val recovered = current.copy(mediaUrl = freshUrl)
                                    _currentTrack.value = recovered
                                    loadMediaToPlayer(activePlayer, recovered)
                                    activePlayer.prepare()
                                    activePlayer.play()
                                }
                            } catch (e: Exception) {
                                Log.e("PlaybackManager", "Stream recovery failed", e)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun scheduleActionDismiss() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(4000)
            _lastJamAction.value = null
        }
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

                    // SponsorBlock auto-skip detection during playback
                    val curTrack = _currentTrack.value
                    if (curTrack != null && settingsPreferences.sponsorBlockEnabled.value && curTrack.isYouTubeTrack()) {
                        val videoId = curTrack.getYouTubeVideoId() ?: curTrack.id.removePrefix("yt_")
                        val skipTarget = sponsorBlockManager.checkSkipTargetMs(videoId, pos)
                        if (skipTarget != null && skipTarget > pos) {
                            Log.d("PlaybackManager", "SponsorBlock auto-skip triggered: jumping from $pos to $skipTarget ms")
                            activePlayer.seekTo(skipTarget)
                            _currentPositionMs.value = skipTarget
                        }
                    }

                    // Check for automatic crossfade trigger before song ends
                    val crossfadeDurationMs = audioEffectManager.currentProfile.crossfadeDurationSec * 1000L
                    if (dur > crossfadeDurationMs && (dur - pos) <= crossfadeDurationMs && !isCrossfading) {
                        if (_repeatMode.value != RepeatMode.ONE) {
                            if (currentIndex + 1 < _queue.value.size || _repeatMode.value == RepeatMode.ALL) {
                                skipNext()
                            } else if (settingsPreferences.infiniteRadioAutoplay.value) {
                                triggerInfiniteRadioAutoplay()
                            }
                        }
                    }

                    // Periodic anti-drift sync: Host broadcasts position every 8s so both phones stay locked in time
                    tickCounter++
                    if (tickCounter % 40 == 0) { // 40 * 200ms = 8 seconds
                        val session = jamClient.currentSession.value
                        if (session != null && session.isHost) {
                            _currentTrack.value?.let { track ->
                                jamClient.broadcastPlaybackState(track, pos, true, action = "drift_sync")
                            }
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
                        val incomingTrack = state.track
                        val current = _currentTrack.value

                        if (current == null || current.id != incomingTrack.id) {
                            _lastJamAction.value = "${state.sender} played ${incomingTrack.title}"
                            scheduleActionDismiss()
                            playTrackFromJam(incomingTrack, state.position, state.isPlaying)
                        } else {
                            if (activePlayer.isPlaying != state.isPlaying) {
                                _lastJamAction.value = if (state.isPlaying) "${state.sender} resumed" else "${state.sender} paused"
                                scheduleActionDismiss()
                                if (state.isPlaying) activePlayer.play() else activePlayer.pause()
                                _isPlaying.value = state.isPlaying
                            }
                            val drift = Math.abs(activePlayer.currentPosition - state.position)
                            if (drift > 1500L) {
                                activePlayer.seekTo(state.position)
                                _currentPositionMs.value = state.position
                            }
                        }
                    }


                    is JamState.UserJoined -> {
                        val session = jamClient.currentSession.value
                        val current = _currentTrack.value
                        if (session != null && session.isHost && current != null) {
                            jamClient.broadcastPlaybackState(
                                track = current,
                                position = activePlayer.currentPosition,
                                isPlaying = activePlayer.isPlaying,
                                action = "sync_response"
                            )
                        }
                        _lastJamAction.value = "${state.username} joined the Jam"
                        scheduleActionDismiss()
                    }

                    is JamState.UserLeft -> {
                        _lastJamAction.value = "${state.username} left the Jam"
                        scheduleActionDismiss()
                    }

                    is JamState.QueueTrack -> {
                        val currentList = _queue.value
                        if (currentList.none { it.id == state.track.id }) {
                            _queue.value = currentList + state.track
                        }
                        _lastJamAction.value = "${state.sender} queued ${state.track.title}"
                        scheduleActionDismiss()
                    }

                    is JamState.ReactionReceived -> {
                        _lastReaction.value = Pair(state.emoji, state.sender)
                        _lastJamAction.value = "${state.sender} sent ${state.emoji} 💖"
                        scheduleActionDismiss()
                    }

                    is JamState.MemoryQuoteReceived -> {
                        _lastMemoryQuote.value = Pair(state.quote, state.sender)
                        _lastJamAction.value = "${state.sender}: \"${state.quote}\" 💌"
                        scheduleActionDismiss()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun playTrackFromJam(track: Track, position: Long, isPlaying: Boolean) {
        crossfadeJob?.cancel()
        isCrossfading = false

        _currentTrack.value = track
        _isPlaying.value = isPlaying
        _currentPositionMs.value = position
        _durationMs.value = track.durationMs

        if (_queue.value.none { it.id == track.id }) {
            _queue.value = _queue.value + track
        }
        currentIndex = _queue.value.indexOfFirst { it.id == track.id }

        scope.launch {
            val resolvedTrack = if (track.isYouTubeTrack() || !track.mediaUrl.startsWith("http")) {
                val resolvedUrl = streamResolver.resolveStreamUrl(track)
                track.copy(mediaUrl = resolvedUrl)
            } else {
                track
            }

            standbyPlayer.stop()
            standbyPlayer.volume = 1.0f

            activePlayer.stop()
            activePlayer.volume = 1.0f

            loadMediaToPlayer(activePlayer, resolvedTrack)
            activePlayer.prepare()
            activePlayer.seekTo(position)
            if (isPlaying) {
                activePlayer.play()
            } else {
                activePlayer.pause()
            }

            audioEffectManager.attachAudioSession(activePlayer.audioSessionId)
            applySponsorBlockIntroSkip(resolvedTrack)
        }
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        _queue.value = newQueue
        currentIndex = newQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        crossfadeJob?.cancel()
        isCrossfading = false

        _currentTrack.value = track
        _isPlaying.value = true
        _currentPositionMs.value = 0L
        _durationMs.value = track.durationMs

        scope.launch {
            val resolvedTrack = if (track.isYouTubeTrack() || !track.mediaUrl.startsWith("http")) {
                val resolvedUrl = streamResolver.resolveStreamUrl(track)
                track.copy(mediaUrl = resolvedUrl)
            } else {
                track
            }

            standbyPlayer.stop()
            standbyPlayer.volume = 1.0f

            activePlayer.stop()
            activePlayer.volume = 1.0f

            loadMediaToPlayer(activePlayer, resolvedTrack)
            activePlayer.prepare()
            activePlayer.play()

            audioEffectManager.attachAudioSession(activePlayer.audioSessionId)
            // Instant broadcast so partner's phone changes to this song immediately
            jamClient.broadcastPlaybackState(resolvedTrack, 0L, true, action = "change_track")

            applySponsorBlockIntroSkip(resolvedTrack)
        }
    }

    fun playPlaylist(playlist: List<Track>, startIndex: Int = 0) {
        if (playlist.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, playlist.size - 1)
        playTrack(playlist[safeIndex], playlist)
    }

    fun startRadio(anchorTrack: Track) {
        playTrack(anchorTrack, listOf(anchorTrack))
        triggerInfiniteRadioAutoplay(anchorTrack)
    }

    private fun applySponsorBlockIntroSkip(track: Track) {
        if (!settingsPreferences.sponsorBlockEnabled.value || !track.isYouTubeTrack()) return

        scope.launch {
            try {
                val videoId = track.getYouTubeVideoId() ?: track.id.removePrefix("yt_")
                sponsorBlockManager.fetchSegments(videoId)
                val introSkip = sponsorBlockManager.getIntroSkipTargetMs(videoId)
                if (introSkip != null && introSkip > 1500L && activePlayer.currentPosition < introSkip) {
                    Log.d("PlaybackManager", "SponsorBlock auto-skipped intro to ${introSkip}ms")
                    activePlayer.seekTo(introSkip)
                    _currentPositionMs.value = introSkip
                }
            } catch (e: Exception) {
                // Silently ignore
            }
        }
    }

    fun playTrackAtIndex(index: Int) {
        val q = _queue.value
        if (index in q.indices) {
            val target = q[index]
            currentIndex = index
            jamClient.broadcastPlaybackState(target, 0L, true, action = "change_track")
            startCrossfadeTo(target)
        }
    }

    fun playNext(track: Track) {
        val q = _queue.value.toMutableList()
        if (q.isEmpty() || currentIndex == -1) {
            playTrack(track, listOf(track))
        } else {
            val existingIndex = q.indexOfFirst { it.id == track.id }
            if (existingIndex > currentIndex) {
                q.removeAt(existingIndex)
            }
            val insertIndex = (currentIndex + 1).coerceAtMost(q.size)
            q.add(insertIndex, track)
            _queue.value = q
        }
        _lastJamAction.value = "Playing next: ${track.title} 🎶"
        scheduleActionDismiss()
    }

    fun addToQueue(track: Track) {
        val q = _queue.value.toMutableList()
        if (q.isEmpty()) {
            playTrack(track, listOf(track))
        } else {
            if (q.none { it.id == track.id }) {
                q.add(track)
                _queue.value = q
            }
        }
        _lastJamAction.value = "Added to queue: ${track.title} 🎵"
        scheduleActionDismiss()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val q = _queue.value.toMutableList()
        if (fromIndex in q.indices && toIndex in q.indices && fromIndex != toIndex) {
            val currentTrackId = _currentTrack.value?.id
            val item = q.removeAt(fromIndex)
            q.add(toIndex, item)
            _queue.value = q
            currentIndex = q.indexOfFirst { it.id == currentTrackId }
        }
    }

    fun removeQueueItem(index: Int) {
        val q = _queue.value.toMutableList()
        if (index in q.indices && index != currentIndex) {
            val currentTrackId = _currentTrack.value?.id
            q.removeAt(index)
            _queue.value = q
            currentIndex = q.indexOfFirst { it.id == currentTrackId }
        }
    }

    fun clearUpcomingQueue() {
        val q = _queue.value
        if (currentIndex in q.indices) {
            _queue.value = q.take(currentIndex + 1)
            _lastJamAction.value = "Upcoming queue cleared 🗑️"
            scheduleActionDismiss()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val params = PlaybackParameters(speed)
        playerA.playbackParameters = params
        playerB.playbackParameters = params
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesRemaining.value = minutes
        if (minutes != null && minutes > 0) {
            sleepTimerJob = scope.launch {
                var remaining = minutes
                while (remaining > 0) {
                    _sleepTimerMinutesRemaining.value = remaining
                    delay(60_000L)
                    remaining--
                }
                _sleepTimerMinutesRemaining.value = null
                // Gentle fade out over 10 seconds
                for (step in 10 downTo 0) {
                    val vol = step / 10f
                    activePlayer.volume = vol
                    delay(1000L)
                }
                pause()
                activePlayer.volume = 1.0f
                _lastJamAction.value = "Sleep timer finished 🌙"
                scheduleActionDismiss()
            }
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        if (_repeatMode.value == RepeatMode.ONE) {
            activePlayer.seekTo(0L)
            activePlayer.play()
            _currentPositionMs.value = 0L
            _currentTrack.value?.let { jamClient.broadcastPlaybackState(it, 0L, true, action = "repeat_one") }
            return
        }

        val nextIdx = when {
            _isShuffleEnabled.value && q.size > 1 -> {
                var rand = Random.nextInt(q.size)
                if (rand == currentIndex) rand = (rand + 1) % q.size
                rand
            }
            currentIndex + 1 < q.size -> currentIndex + 1
            _repeatMode.value == RepeatMode.ALL -> 0
            else -> -1
        }

        if (nextIdx != -1) {
            val nextTrack = q[nextIdx]
            currentIndex = nextIdx
            jamClient.broadcastPlaybackState(nextTrack, 0L, true, action = "next_track")
            startCrossfadeTo(nextTrack)
        } else if (settingsPreferences.infiniteRadioAutoplay.value) {
            // Queue has reached the end! Autoplay similar songs automatically
            triggerInfiniteRadioAutoplay()
        }
    }

    fun triggerInfiniteRadioAutoplay(anchorTrack: Track? = null) {
        if (isFetchingRadio) return
        val track = anchorTrack ?: _currentTrack.value ?: return
        isFetchingRadio = true

        scope.launch {
            try {
                val videoId = track.getYouTubeVideoId() ?: track.id.removePrefix("yt_")
                val related = youTubeMusicApi.getRelatedTracks(videoId)
                val currentQ = _queue.value
                val newTracks = related.filter { rel -> currentQ.none { it.id == rel.id } }

                if (newTracks.isNotEmpty()) {
                    _queue.value = currentQ + newTracks
                    if (currentIndex + 1 < _queue.value.size) {
                        val nextTrack = _queue.value[currentIndex + 1]
                        currentIndex += 1
                        jamClient.broadcastPlaybackState(nextTrack, 0L, true, action = "radio_autoplay")
                        startCrossfadeTo(nextTrack)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Infinite radio autoplay failed", e)
            } finally {
                isFetchingRadio = false
            }
        }
    }

    fun skipPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (activePlayer.currentPosition > 3000L) {
            // Restart current track
            activePlayer.seekTo(0L)
            _currentPositionMs.value = 0L
            _currentTrack.value?.let { jamClient.broadcastPlaybackState(it, 0L, activePlayer.isPlaying, action = "restart_track") }
            return
        }
        val prevIdx = if (currentIndex - 1 >= 0) {
            currentIndex - 1
        } else if (_repeatMode.value == RepeatMode.ALL) {
            q.size - 1
        } else {
            -1
        }

        if (prevIdx >= 0) {
            val prevTrack = q[prevIdx]
            currentIndex = prevIdx
            jamClient.broadcastPlaybackState(prevTrack, 0L, true, action = "prev_track")
            startCrossfadeTo(prevTrack)
        }
    }

    private fun startCrossfadeTo(nextTrack: Track) {
        val crossfadeMs = (audioEffectManager.currentProfile.crossfadeDurationSec * 1000L).coerceIn(1000L, 12000L)

        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            isCrossfading = true

            val resolvedNext = if (nextTrack.isYouTubeTrack() || !nextTrack.mediaUrl.startsWith("http")) {
                val resolvedUrl = streamResolver.resolveStreamUrl(nextTrack)
                nextTrack.copy(mediaUrl = resolvedUrl)
            } else {
                nextTrack
            }

            // Prepare standby player with next song at 0 volume
            standbyPlayer.stop()
            standbyPlayer.volume = 0.0f
            loadMediaToPlayer(standbyPlayer, resolvedNext)
            standbyPlayer.prepare()
            standbyPlayer.play()

            _currentTrack.value = resolvedNext

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

            applySponsorBlockIntroSkip(resolvedNext)
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
            jamClient.broadcastPlaybackState(track, positionMs, activePlayer.isPlaying, action = "seek")
        }
    }

    fun togglePlayPause() {
        if (activePlayer.isPlaying) {
            activePlayer.pause()
            _isPlaying.value = false
            _currentTrack.value?.let { track ->
                jamClient.broadcastPlaybackState(track, activePlayer.currentPosition, false, action = "pause")
            }
        } else {
            activePlayer.play()
            _isPlaying.value = true
            _currentTrack.value?.let { track ->
                jamClient.broadcastPlaybackState(track, activePlayer.currentPosition, true, action = "play")
            }
        }
    }

    fun applySoundProfile(profile: SoundProfile) {
        audioEffectManager.applyProfile(profile)
    }
}
