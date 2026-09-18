package com.auralis.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.media3.session.SessionCommands
import com.auralis.app.data.source.StreamResolverRegistry
import com.auralis.app.domain.model.SoundProfile
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.network.JamProtocolHelper
import com.auralis.app.network.JamState
import com.auralis.app.network.JamWebSocketClient
import com.auralis.app.playback.queue.QueueOps
import com.auralis.app.playback.queue.QueueState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RepeatMode {
    OFF, ALL, ONE
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val jamClient: JamWebSocketClient,
    val audioEffectManager: AudioEffectManager,
    private val streamResolver: StreamResolverRegistry,
    val settingsPreferences: AuralisSettingsPreferences,
    private val musicRepository: MusicRepository,
    private val segmentSkippers: Set<@JvmSuppressWildcards SegmentSkipper>,
    val personalizationManager: PersonalizationManager,
    private val historyRecorder: PlaybackHistoryRecorder
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

    // Bounded so a long listening session cannot grow it forever and starve Infinite Radio of candidates
    private val sessionPlayedTrackIds = LinkedHashSet<String>()

    private fun markPlayed(trackId: String) {
        if (sessionPlayedTrackIds.add(trackId) && sessionPlayedTrackIds.size > MAX_SESSION_PLAYED_IDS) {
            sessionPlayedTrackIds.remove(sessionPlayedTrackIds.first())
        }
    }

    // The queue lives in one immutable value transformed by QueueOps; the flows above are
    // projections of it, so index arithmetic is testable without a player.
    private var queueState = QueueState()

    private val currentIndex: Int
        get() = queueState.currentIndex

    private fun updateQueue(transform: (QueueState) -> QueueState) {
        val updated = transform(queueState)
        queueState = updated
        _queue.value = updated.tracks
        _currentQueueIndex.value = updated.currentIndex
        _isShuffleEnabled.value = updated.isShuffled
        _repeatMode.value = updated.repeatMode
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
        setupMediaSession()
        historyRecorder.attach(scope, _currentTrack, _currentPositionMs, _durationMs)
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
                    val trusted = controller.packageName == context.packageName ||
                        controller.isTrusted ||
                        session.isMediaNotificationController(controller) ||
                        session.isAutomotiveController(controller) ||
                        session.isAutoCompanionController(controller)
                    if (!trusted) {
                        // Unknown apps may observe playback state but never control it
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(SessionCommands.EMPTY)
                            .setAvailablePlayerCommands(Player.Commands.EMPTY)
                            .build()
                    }

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

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffectManager.attachAudioSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player == activePlayer) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = player.duration.coerceAtLeast(0L)
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
                                val freshUrl = streamResolver.resolve(current, forceRefresh = true)
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

                    // Non-music segment auto-skip during playback
                    val curTrack = _currentTrack.value
                    if (curTrack != null && settingsPreferences.sponsorBlockEnabled.value) {
                        val skipTarget = segmentSkipperFor(curTrack)?.skipTargetMs(curTrack, pos)
                        if (skipTarget != null && skipTarget > pos) {
                            Log.d("PlaybackManager", "Segment auto-skip: jumping from $pos to $skipTarget ms")
                            activePlayer.seekTo(skipTarget)
                            _currentPositionMs.value = skipTarget
                        }
                    }

                    // Check for automatic crossfade trigger before song ends
                    val crossfadeDurationMs = audioEffectManager.currentProfile.crossfadeDurationSec * 1000L
                    if (dur > crossfadeDurationMs && (dur - pos) <= crossfadeDurationMs && !isCrossfading) {
                        if (_repeatMode.value != RepeatMode.ONE) {
                            if (QueueOps.nextIndex(queueState) != null) {
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
                        updateQueue { QueueOps.addToQueue(it, state.track) }
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

        updateQueue { q ->
            val withTrack = if (q.tracks.none { it.id == track.id }) {
                q.copy(tracks = q.tracks + track)
            } else {
                q
            }
            withTrack.copy(currentIndex = withTrack.tracks.indexOfFirst { it.id == track.id })
        }

        scope.launch {
            val resolvedTrack = if (JamProtocolHelper.needsStreamResolution(track)) {
                val resolvedUrl = streamResolver.resolve(track, forceRefresh = true)
                track.copy(mediaUrl = resolvedUrl)
            } else {
                track
            }

            if (!JamProtocolHelper.isPlayableDirectStreamUrl(resolvedTrack.mediaUrl)) {
                Log.e("PlaybackManager", "Cannot play track from Jam: stream URL unresolvable for ${track.title}")
                _lastJamAction.value = "Cannot play ${track.title} ⚠️"
                scheduleActionDismiss()
                return@launch
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

            applySponsorBlockIntroSkip(resolvedTrack)
            personalizationManager.recordTrackPlay(resolvedTrack)
        }
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        updateQueue { QueueOps.play(it, track, newQueue) }

        crossfadeJob?.cancel()
        isCrossfading = false

        _currentTrack.value = track
        _isPlaying.value = true
        _currentPositionMs.value = 0L
        _durationMs.value = track.durationMs

        markPlayed(track.id)
        ensureMediaServiceStarted()

        scope.launch {
            val resolvedTrack = if (JamProtocolHelper.needsStreamResolution(track)) {
                val resolvedUrl = streamResolver.resolve(track)
                track.copy(mediaUrl = resolvedUrl)
            } else {
                track
            }

            if (!JamProtocolHelper.isPlayableDirectStreamUrl(resolvedTrack.mediaUrl)) {
                Log.e("PlaybackManager", "Cannot play track: stream URL unresolvable for ${track.title}")
                _lastJamAction.value = "Cannot play ${track.title} ⚠️"
                scheduleActionDismiss()
                return@launch
            }

            standbyPlayer.stop()
            standbyPlayer.volume = 1.0f

            activePlayer.stop()
            activePlayer.volume = 1.0f

            loadMediaToPlayer(activePlayer, resolvedTrack)
            activePlayer.prepare()
            activePlayer.play()

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

    private fun segmentSkipperFor(track: Track): SegmentSkipper? =
        segmentSkippers.firstOrNull { it.supports(track) }

    private fun applySponsorBlockIntroSkip(track: Track) {
        if (!settingsPreferences.sponsorBlockEnabled.value) return
        val skipper = segmentSkipperFor(track) ?: return

        scope.launch {
            try {
                skipper.prepare(track)
                val introSkip = skipper.introSkipTargetMs(track)
                if (introSkip != null && introSkip > 1500L && activePlayer.currentPosition < introSkip) {
                    Log.d("PlaybackManager", "Auto-skipped intro to ${introSkip}ms")
                    activePlayer.seekTo(introSkip)
                    _currentPositionMs.value = introSkip
                }
            } catch (e: Exception) {
                // Silently ignore
            }
        }
    }

    fun playTrackAtIndex(index: Int) {
        val q = queueState.tracks
        if (index in q.indices) {
            val target = q[index]
            updateQueue { QueueOps.playAt(it, index) }
            markPlayed(target.id)
            jamClient.broadcastPlaybackState(target, 0L, true, action = "change_track")
            startCrossfadeTo(target)
            checkAndPrefetchRadioBuffer()
        }
    }

    fun playNext(track: Track) {
        val wasEmpty = queueState.isEmpty || queueState.currentIndex < 0
        val cleanTrack = track.copy(isAutoplayRecommendation = false)
        if (wasEmpty) {
            playTrack(cleanTrack, listOf(cleanTrack))
        } else {
            updateQueue { QueueOps.playNext(it, cleanTrack) }
        }
        _lastJamAction.value = "Playing next: ${cleanTrack.title} 🎶"
        scheduleActionDismiss()
    }

    fun addToQueue(track: Track) {
        val wasEmpty = queueState.isEmpty || queueState.currentIndex < 0
        val cleanTrack = track.copy(isAutoplayRecommendation = false)
        if (wasEmpty) {
            playTrack(cleanTrack, listOf(cleanTrack))
        } else {
            updateQueue { QueueOps.addToQueue(it, cleanTrack) }
        }
        _lastJamAction.value = "Added to queue: ${cleanTrack.title} 🎵"
        scheduleActionDismiss()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        updateQueue { QueueOps.move(it, fromIndex, toIndex) }
    }

    fun removeQueueItem(index: Int) {
        updateQueue { QueueOps.remove(it, index) }
        checkAndPrefetchRadioBuffer()
    }

    fun clearUpcomingQueue() {
        updateQueue { QueueOps.clearUpcoming(it) }
        _lastJamAction.value = "Upcoming queue cleared 🗑️"
        scheduleActionDismiss()
    }

    fun clearAutoplayRecommendations() {
        updateQueue { QueueOps.clearRecommendations(it) }
        _lastJamAction.value = "Autoplay recommendations cleared"
        scheduleActionDismiss()
    }

    fun refreshInfiniteRadio() {
        updateQueue { QueueOps.clearRecommendations(it) }
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
        updateQueue { QueueOps.setShuffled(it, !it.isShuffled) }
    }

    fun toggleRepeat() {
        updateQueue { it.copy(repeatMode = QueueOps.nextRepeatMode(it.repeatMode)) }
    }

    fun skipNext() {
        if (queueState.isEmpty) return

        if (queueState.repeatMode == RepeatMode.ONE) {
            activePlayer.seekTo(0L)
            activePlayer.play()
            _currentPositionMs.value = 0L
            _currentTrack.value?.let { jamClient.broadcastPlaybackState(it, 0L, true, action = "repeat_one") }
            return
        }

        val nextIdx = QueueOps.nextIndex(queueState)
        if (nextIdx != null) {
            val nextTrack = queueState.tracks[nextIdx]
            updateQueue { QueueOps.playAt(it, nextIdx) }
            markPlayed(nextTrack.id)
            jamClient.broadcastPlaybackState(nextTrack, 0L, true, action = "next_track")
            startCrossfadeTo(nextTrack)
            checkAndPrefetchRadioBuffer()
        } else if (settingsPreferences.infiniteRadioAutoplay.value) {
            // Queue has reached the end: let the radio extend it
            triggerInfiniteRadioAutoplay(forceImmediateStart = true)
        }
    }

    fun checkAndPrefetchRadioBuffer(force: Boolean = false) {
        if (!settingsPreferences.infiniteRadioAutoplay.value && !force) return
        if (isFetchingRadio) return
        if (queueState.isEmpty || queueState.currentIndex < 0) return

        // Keep a couple of tracks buffered ahead so the radio never stalls playback
        if (queueState.upcomingCount <= RADIO_BUFFER_THRESHOLD || force) {
            triggerInfiniteRadioAutoplay(anchorTrack = QueueOps.radioSeed(queueState), forceImmediateStart = false)
        }
    }

    fun triggerInfiniteRadioAutoplay(anchorTrack: Track? = null, forceImmediateStart: Boolean = false) {
        if (isFetchingRadio) return
        val current = _currentTrack.value ?: return
        val seed = anchorTrack ?: QueueOps.radioSeed(queueState) ?: current
        isFetchingRadio = true
        _isInfiniteRadioLoading.value = true

        scope.launch {
            try {
                val related = musicRepository.relatedTracks(seed)
                val hadNoUpcoming = queueState.upcomingCount == 0

                var appended = 0
                updateQueue { q ->
                    val grown = QueueOps.appendRecommendations(q, related, sessionPlayedTrackIds)
                    appended = grown.tracks.size - q.tracks.size
                    grown
                }

                if (appended > 0) {
                    Log.d("PlaybackManager", "Appended $appended Infinite Radio tracks to queue")

                    // Only jump straight in when the queue had actually run dry
                    if (forceImmediateStart || hadNoUpcoming) {
                        val nextIdx = QueueOps.nextIndex(queueState)
                        if (nextIdx != null && nextIdx != queueState.currentIndex) {
                            val nextTrack = queueState.tracks[nextIdx]
                            updateQueue { QueueOps.playAt(it, nextIdx) }
                            markPlayed(nextTrack.id)
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
        if (queueState.isEmpty) return
        if (activePlayer.currentPosition > RESTART_THRESHOLD_MS) {
            // Far enough in that "previous" means restart, as every other player does
            activePlayer.seekTo(0L)
            _currentPositionMs.value = 0L
            _currentTrack.value?.let { jamClient.broadcastPlaybackState(it, 0L, activePlayer.isPlaying, action = "restart_track") }
            return
        }

        val prevIdx = QueueOps.previousIndex(queueState) ?: return
        val prevTrack = queueState.tracks[prevIdx]
        updateQueue { QueueOps.playAt(it, prevIdx) }
        jamClient.broadcastPlaybackState(prevTrack, 0L, true, action = "prev_track")
        startCrossfadeTo(prevTrack)
    }

    private fun startCrossfadeTo(nextTrack: Track) {
        val crossfadeMs = (audioEffectManager.currentProfile.crossfadeDurationSec * 1000L).coerceIn(1000L, 12000L)

        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val thisJob = coroutineContext[Job]
            isCrossfading = true
            try {
                val resolvedNext = if (JamProtocolHelper.needsStreamResolution(nextTrack)) {
                    val resolvedUrl = streamResolver.resolve(nextTrack)
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
                _isPlaying.value = true

                applySponsorBlockIntroSkip(resolvedNext)
            } finally {
                // A cancelled fade must not leave the flag set, but a newer fade owns it now
                if (crossfadeJob === thisJob) isCrossfading = false
            }
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

    private companion object {
        const val MAX_SESSION_PLAYED_IDS = 500
        const val RADIO_BUFFER_THRESHOLD = 2
        const val RESTART_THRESHOLD_MS = 3000L
    }
}
