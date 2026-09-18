package com.auralis.app.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.LyricLine
import com.auralis.app.domain.model.SoundProfile
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.lyrics.LyricsRepository
import com.auralis.app.network.JamSession
import com.auralis.app.playback.AudioEffectManager
import com.auralis.app.playback.PlaybackManager
import com.auralis.app.playback.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlayerScreenTab {
    ARTWORK,
    UP_NEXT,
    LYRICS,
    RELATED
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val repository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val audioEffectManager: AudioEffectManager
) : ViewModel() {

    val currentTrack = playbackManager.currentTrack
    val isPlaying = playbackManager.isPlaying
    val currentPositionMs = playbackManager.currentPositionMs
    val durationMs = playbackManager.durationMs
    val queue = playbackManager.queue
    val isShuffleEnabled = playbackManager.isShuffleEnabled
    val repeatMode = playbackManager.repeatMode
    val jamSession = playbackManager.jamSession
    val jamState = playbackManager.jamState
    val lastJamAction = playbackManager.lastJamAction
    val lastReaction = playbackManager.lastReaction
    val lastMemoryQuote = playbackManager.lastMemoryQuote
    val currentQueueIndex = playbackManager.currentQueueIndex
    val playbackSpeed = playbackManager.playbackSpeed
    val sleepTimerMinutesRemaining = playbackManager.sleepTimerMinutesRemaining

    fun sendJamReaction(emoji: String) {
        playbackManager.sendJamReaction(emoji)
    }

    fun sendMemoryQuote(quote: String = "I love you jaanaa 💋") {
        playbackManager.sendMemoryQuote(quote)
    }

    fun clearMemoryQuote() {
        playbackManager.clearMemoryQuote()
    }

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _activeTab = MutableStateFlow(PlayerScreenTab.ARTWORK)
    val activeTab = _activeTab.asStateFlow()

    private val _isSoundProfilesVisible = MutableStateFlow(false)
    val isSoundProfilesVisible = _isSoundProfilesVisible.asStateFlow()

    private val _isJamSheetVisible = MutableStateFlow(false)
    val isJamSheetVisible = _isJamSheetVisible.asStateFlow()

    private val _soundProfile = MutableStateFlow(audioEffectManager.currentProfile)
    val soundProfile = _soundProfile.asStateFlow()

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds = _likedTrackIds.asStateFlow()

    private val _dislikedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedTrackIds = _dislikedTrackIds.asStateFlow()

    val lyricsFontSize = playbackManager.settingsPreferences.lyricsFontSize
    val lyricsAutoScroll = playbackManager.settingsPreferences.lyricsAutoScroll
    val isInfiniteRadioLoading = playbackManager.isInfiniteRadioLoading
    val isInfiniteRadioAutoplayEnabled = playbackManager.isInfiniteRadioAutoplayEnabled

    fun startRadio() {
        currentTrack.value?.let { playbackManager.startRadio(it) }
    }

    fun toggleInfiniteRadioAutoplay(enabled: Boolean) {
        playbackManager.setInfiniteRadioAutoplay(enabled)
    }

    fun refreshInfiniteRadio() {
        playbackManager.refreshInfiniteRadio()
    }

    fun clearAutoplayRecommendations() {
        playbackManager.clearAutoplayRecommendations()
    }

    init {
        viewModelScope.launch {
            currentTrack.collect { track ->
                if (track != null) {
                    _lyrics.value = emptyList()
                    val fetchedLyrics = lyricsRepository.getLyrics(track)
                    _lyrics.value = fetchedLyrics
                }
            }
        }
    }

    fun selectTab(tab: PlayerScreenTab) {
        if (_activeTab.value == tab && tab != PlayerScreenTab.ARTWORK) {
            _activeTab.value = PlayerScreenTab.ARTWORK
        } else {
            _activeTab.value = tab
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun skipNext() {
        playbackManager.skipNext()
    }

    fun skipPrevious() {
        playbackManager.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackManager.toggleRepeat()
    }

    fun playTrackFromQueue(index: Int) {
        playbackManager.playTrackAtIndex(index)
    }

    fun playNext(track: Track) {
        playbackManager.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackManager.addToQueue(track)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackManager.moveQueueItem(fromIndex, toIndex)
    }

    fun removeQueueItem(index: Int) {
        playbackManager.removeQueueItem(index)
    }

    fun clearUpcomingQueue() {
        playbackManager.clearUpcomingQueue()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackManager.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int?) {
        playbackManager.setSleepTimer(minutes)
    }

    fun toggleLike(trackId: String) {
        val current = _likedTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
            _dislikedTrackIds.value = _dislikedTrackIds.value - trackId
        }
        _likedTrackIds.value = current
    }

    fun toggleDislike(trackId: String) {
        val current = _dislikedTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
            _likedTrackIds.value = _likedTrackIds.value - trackId
        }
        _dislikedTrackIds.value = current
    }

    fun setSoundProfilesVisible(visible: Boolean) {
        _isSoundProfilesVisible.value = visible
    }

    fun setJamSheetVisible(visible: Boolean) {
        _isJamSheetVisible.value = visible
    }

    fun startJam(jamId: String, username: String) {
        playbackManager.jamClient.startJam(jamId, username)
        currentTrack.value?.let { track ->
            playbackManager.jamClient.broadcastPlaybackState(track, currentPositionMs.value, isPlaying.value)
        }
    }

    fun joinJam(jamId: String, username: String) {
        playbackManager.jamClient.joinJam(jamId, username)
    }

    val isJamConnected = playbackManager.jamClient.isConnected

    fun reconnectJam() {
        playbackManager.jamClient.reconnect()
    }

    fun leaveJam() {
        playbackManager.jamClient.disconnect()
    }

    fun updateSoundProfile(profile: SoundProfile) {
        _soundProfile.value = profile
        playbackManager.applySoundProfile(profile)
    }

    fun downloadCurrentTrack() {
        val track = currentTrack.value
        if (track != null) {
            viewModelScope.launch {
                repository.downloadTrack(track)
            }
        }
    }
}
