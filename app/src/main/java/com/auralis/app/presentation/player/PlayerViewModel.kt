package com.auralis.app.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.LyricLine
import com.auralis.app.domain.model.SoundProfile
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.lyrics.LyricsRepository
import com.auralis.app.playback.AudioEffectManager
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _isLyricsVisible = MutableStateFlow(false)
    val isLyricsVisible = _isLyricsVisible.asStateFlow()

    private val _isSoundProfilesVisible = MutableStateFlow(false)
    val isSoundProfilesVisible = _isSoundProfilesVisible.asStateFlow()

    private val _soundProfile = MutableStateFlow(audioEffectManager.currentProfile)
    val soundProfile = _soundProfile.asStateFlow()

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

    fun toggleLyrics() {
        _isLyricsVisible.value = !_isLyricsVisible.value
    }

    fun setSoundProfilesVisible(visible: Boolean) {
        _isSoundProfilesVisible.value = visible
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
