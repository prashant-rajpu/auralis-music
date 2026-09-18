package com.auralis.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.ByteArrayOutputStream
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
import kotlinx.coroutines.flow.StateFlow
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

    // Full Audio Focus & Becoming Noisy handling for daily driver stability on Android & One UI
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private var playerA: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var playerB: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
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

    private val _isInfiniteRadioLoading = MutableStateFlow(false)
    val isInfiniteRadioLoading: StateFlow<Boolean> = _isInfiniteRadioLoading.asStateFlow()
    val isInfiniteRadioAutoplayEnabled: StateFlow<Boolean> = settingsPreferences.infiniteRadioAutoplay

    private val sessionPlayedTrackIds = mutableSetOf<String>()

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
    var mediaSession: MediaSession? = null
        private set

    val activePlayerInstance: ExoPlayer
        get() = activePlayer

    init {
        setupPlayer(playerA)
        setupPlayer(playerB)
        startPositionTicker()
        observeJamState()
        audioEffectManager.attachAudioSession(activePlayer.audioSessionId)
        setupMediaSession()
    }

    private fun createForwardingPlayer(player: ExoPlayer): AuralisQueueForwardingPlayer {
        return AuralisQueueForwardingPlayer(
            player = player,
            onSkipNext = { skipNext() },
            onSkipPrevious = { skipPrevious() }
        )
    }

    private fun setupMediaSession() {
        try {
            val intent = Intent(context, com.auralis.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val sessionCallback = object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .setAvailablePlayerCommands(playerCommands)
                        .build()
                }
            }

            mediaSession = MediaSession.Builder(context, createForwardingPlayer(activePlayer))
                .setId("AuralisTogetherMediaSession")
                .setSessionActivity(pendingIntent)
                .setCallback(sessionCallback)
                .build()
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to build MediaSession", e)
        }
    }

    fun ensureMediaServiceStarted() {
        try {
            val intent = Intent(context, AuralisMediaSessionService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed to start AuralisMediaSessionService", e)
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

                    // Proactive Infinite Radio queue buffering every ~5 seconds (25 * 200ms)
                    if (tickCounter % 25 == 0) {
                        checkAndPrefetchRadioBuffer()
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
                            if (com.auralis.app.network.JamProtocolHelper.shouldSeek(activePlayer.currentPosition, state.position)) {
                                activePlayer.seekTo(state.position)
                                _currentPositionMs.value = state.position
                            }
                        }
                    }


                    is JamState.RequestSync -> {
                        val session = jamClient.currentSession.value
                        val current = _currentTrack.value
                        if (session != null && current != null) {
                            jamClient.broadcastPlaybackState(
                                track = current,
                                position = activePlayer.currentPosition,
                                isPlaying = activePlayer.isPlaying,
                                action = "sync_response"
                            )
                        }
                    }

                    is JamState.UserJoined -> {
                        val session = jamClient.currentSession.value
                        val current = _currentTrack.value
                        if (session != null && current != null) {
                            jamClient.broadcastPlaybackState(
                                track = current,
                                position = activePlayer.currentPosition,
                                isPlaying = activePlayer.isPlaying,
                                action = "sync_response"
                            )
                        }
                        _lastJamAction.value = "${state.username} joined the Jam 💗"
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
            val resolvedTrack = if (track.isYouTubeTrack() || !track.mediaUrl.startsWith("http") || track.mediaUrl.contains("googlevideo.com")) {
                val resolvedUrl = streamResolver.resolveStreamUrl(track, forceRefresh = true)
                track.copy(mediaUrl = resolvedUrl)
            } else {
                track
            }

            standbyPlayer.stop()
            standbyPlayer.volume = 1.0f

            activePlayer.stop()
            activePlayer.volume = 1.0f

            ensureMediaServiceStarted()
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
            personalizationManager.recordTrackPlay(resolvedTrack)
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

        sessionPlayedTrackIds.add(track.id)
        ensureMediaServiceStarted()

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

            // Proactively check if queue needs radio buffer
            checkAndPrefetchRadioBuffer()
        }
    }

    fun playPlaylist(playlist: List<Track>, startIndex: Int = 0) {
        if (playlist.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, playlist.size - 1)
        playTrack(playlist[safeIndex], playlist)
    }

    fun startRadio(anchorTrack: Track) {
        val cleanAnchor = anchorTrack.copy(isAutoplayRecommendation = false)
        playTrack(cleanAnchor, listOf(cleanAnchor))
        triggerInfiniteRadioAutoplay(anchorTrack = cleanAnchor, forceImmediateStart = false)
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
            sessionPlayedTrackIds.add(target.id)
            jamClient.broadcastPlaybackState(target, 0L, true, action = "change_track")
            startCrossfadeTo(target)
            checkAndPrefetchRadioBuffer()
        }
    }

    fun playNext(track: Track) {
        val q = _queue.value.toMutableList()
        val cleanTrack = track.copy(isAutoplayRecommendation = false)
        if (q.isEmpty() || currentIndex == -1) {
            playTrack(cleanTrack, listOf(cleanTrack))
        } else {
            val existingIndex = q.indexOfFirst { it.id == cleanTrack.id }
            if (existingIndex > currentIndex) {
                q.removeAt(existingIndex)
            }
            val insertIndex = (currentIndex + 1).coerceAtMost(q.size)
            q.add(insertIndex, cleanTrack)
            _queue.value = q
        }
        _lastJamAction.value = "Playing next: ${cleanTrack.title} 🎶"
        scheduleActionDismiss()
    }

    fun addToQueue(track: Track) {
        val q = _queue.value.toMutableList()
        val cleanTrack = track.copy(isAutoplayRecommendation = false)
        if (q.isEmpty() || currentIndex == -1) {
            playTrack(cleanTrack, listOf(cleanTrack))
        } else {
            if (q.none { it.id == cleanTrack.id }) {
                // Prioritize user tracks: insert BEFORE the first upcoming autoplay recommendation
                val firstAutoplayIndex = q.indexOfFirst { it.isAutoplayRecommendation && q.indexOf(it) > currentIndex }
                if (firstAutoplayIndex != -1) {
                    q.add(firstAutoplayIndex, cleanTrack)
                } else {
                    q.add(cleanTrack)
                }
                _queue.value = q
            }
        }
        _lastJamAction.value = "Added to queue: ${cleanTrack.title} 🎵"
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
            checkAndPrefetchRadioBuffer()
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

    fun clearAutoplayRecommendations() {
        val q = _queue.value.toMutableList()
        val currentTrackId = _currentTrack.value?.id
        val filtered = q.filterIndexed { index, track ->
            index <= currentIndex || !track.isAutoplayRecommendation
        }
        _queue.value = filtered
        currentIndex = filtered.indexOfFirst { it.id == currentTrackId }
        _lastJamAction.value = "Autoplay recommendations cleared"
        scheduleActionDismiss()
    }

    fun refreshInfiniteRadio() {
        val q = _queue.value.toMutableList()
        val currentTrackId = _currentTrack.value?.id
        val filtered = q.filterIndexed { index, track ->
            index <= currentIndex || !track.isAutoplayRecommendation
        }
        _queue.value = filtered
        currentIndex = filtered.indexOfFirst { it.id == currentTrackId }
        triggerInfiniteRadioAutoplay(anchorTrack = _currentTrack.value, forceImmediateStart = false)
        _lastJamAction.value = "Refreshing Infinite Radio 🔄"
        scheduleActionDismiss()
    }

    fun setInfiniteRadioAutoplay(enabled: Boolean) {
        settingsPreferences.setInfiniteRadioAutoplay(enabled)
        if (!enabled) {
            clearAutoplayRecommendations()
        } else {
            checkAndPrefetchRadioBuffer(force = true)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val params = PlaybackParameters(speed)
        playerA.playbackParameters = params
        playerB.playbackParameters = params
        _lastJamAction.value = "Speed: ${speed}x ⚡"
        scheduleActionDismiss()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesRemaining.value = minutes

        if (minutes == null || minutes <= 0) {
            _lastJamAction.value = "Sleep timer disabled"
            scheduleActionDismiss()
            return
        }

        _lastJamAction.value = "Sleep timer set: $minutes min 🌙"
        scheduleActionDismiss()

        sleepTimerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                _sleepTimerMinutesRemaining.value = if (remaining > 0) remaining else null
            }

            // Gentle 10-second volume fade-out before pausing
            val steps = 20
            val fadeInterval = 500L
            val startVol = activePlayer.volume
            for (i in steps downTo 0) {
                val vol = startVol * (i.toFloat() / steps.toFloat())
                activePlayer.volume = vol
                delay(fadeInterval)
            }

            pause()
            activePlayer.volume = startVol
            _sleepTimerMinutesRemaining.value = null
            _lastJamAction.value = "Goodnight! Sleep timer finished 🌙"
            scheduleActionDismiss()
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
            sessionPlayedTrackIds.add(nextTrack.id)
            jamClient.broadcastPlaybackState(nextTrack, 0L, true, action = "next_track")
            startCrossfadeTo(nextTrack)
            checkAndPrefetchRadioBuffer()
        } else if (settingsPreferences.infiniteRadioAutoplay.value) {
            // Queue has reached the end! Autoplay similar songs automatically
            triggerInfiniteRadioAutoplay(forceImmediateStart = true)
        }
    }

    fun checkAndPrefetchRadioBuffer(force: Boolean = false) {
        if (!settingsPreferences.infiniteRadioAutoplay.value && !force) return
        if (isFetchingRadio) return
        val q = _queue.value
        if (q.isEmpty() || currentIndex == -1) return

        val upcomingCount = (q.size - 1) - currentIndex
        // Proactive buffer: if 2 or fewer upcoming songs remain, pre-fetch
        if (upcomingCount <= 2 || force) {
            val seed = q.lastOrNull() ?: _currentTrack.value
            triggerInfiniteRadioAutoplay(anchorTrack = seed, forceImmediateStart = false)
        }
    }

    fun triggerInfiniteRadioAutoplay(anchorTrack: Track? = null, forceImmediateStart: Boolean = false) {
        if (isFetchingRadio) return
        val current = _currentTrack.value ?: return
        val seed = anchorTrack ?: _queue.value.lastOrNull() ?: current
        isFetchingRadio = true
        _isInfiniteRadioLoading.value = true

        scope.launch {
            try {
                val videoId = seed.getYouTubeVideoId() ?: seed.id.removePrefix("yt_")
                var related = youTubeMusicApi.getRelatedTracks(videoId)
                if (related.isEmpty()) {
                    val searchFallback = youTubeMusicApi.searchTracks("${seed.artist} song")
                    related = searchFallback.filter { it.id != seed.id }
                }

                val currentQ = _queue.value
                val newTracks = related
                    .filter { rel -> currentQ.none { it.id == rel.id } && rel.id !in sessionPlayedTrackIds }
                    .take(8)
                    .map { it.copy(isAutoplayRecommendation = true) }

                if (newTracks.isNotEmpty()) {
                    _queue.value = currentQ + newTracks
                    Log.d("PlaybackManager", "Appended ${newTracks.size} Infinite Radio tracks to queue")

                    if (forceImmediateStart || currentIndex >= currentQ.size - 1) {
                        if (currentIndex + 1 < _queue.value.size) {
                            val nextTrack = _queue.value[currentIndex + 1]
                            currentIndex += 1
                            sessionPlayedTrackIds.add(nextTrack.id)
                            jamClient.broadcastPlaybackState(nextTrack, 0L, true, action = "radio_autoplay")
                            startCrossfadeTo(nextTrack)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Infinite radio autoplay failed", e)
            } finally {
                isFetchingRadio = false
                _isInfiniteRadioLoading.value = false
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

            mediaSession?.setPlayer(createForwardingPlayer(activePlayer))

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
                    .setDisplayTitle(track.title)
                    .setSubtitle(track.artist)
                    .setArtworkUri(track.albumArtUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        fetchArtworkBitmap(player, track)
    }

    private fun fetchArtworkBitmap(player: ExoPlayer, track: Track) {
        val artUrl = track.albumArtUrl ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val loader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(artUrl)
                    .allowHardware(false)
                    .build()
                val result = (loader.execute(request) as? coil.request.SuccessResult)?.drawable
                val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null && _currentTrack.value?.id == track.id) {
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, stream)
                    val byteArray = stream.toByteArray()
                    withContext(Dispatchers.Main) {
                        if (_currentTrack.value?.id == track.id) {
                            val currentItem = player.currentMediaItem
                            if (currentItem != null && currentItem.mediaId == track.id) {
                                val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                                    .setArtworkData(byteArray, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build()
                                val updatedItem = currentItem.buildUpon()
                                    .setMediaMetadata(updatedMetadata)
                                    .build()
                                player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Failed to fetch artwork byte array for notification", e)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        activePlayer.seekTo(positionMs.coerceAtLeast(0L))
        _currentPositionMs.value = positionMs
        _currentTrack.value?.let { track ->
            jamClient.broadcastPlaybackState(track, positionMs, activePlayer.isPlaying, action = "seek")
        }
    }

    fun pause() {
        if (activePlayer.isPlaying) {
            activePlayer.pause()
            _isPlaying.value = false
            _currentTrack.value?.let { track ->
                jamClient.broadcastPlaybackState(track, activePlayer.currentPosition, false, action = "pause")
            }
        }
    }

    fun play() {
        ensureMediaServiceStarted()
        if (!activePlayer.isPlaying) {
            activePlayer.play()
            _isPlaying.value = true
            _currentTrack.value?.let { track ->
                jamClient.broadcastPlaybackState(track, activePlayer.currentPosition, true, action = "play")
            }
        }
    }

    fun togglePlayPause() {
        if (activePlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun applySoundProfile(profile: SoundProfile) {
        audioEffectManager.applyProfile(profile)
    }
}
