package com.auralis.app.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val repository: MusicRepository
) : ViewModel() {
    
    val currentTrack = playbackManager.currentTrack
    val isPlaying = playbackManager.isPlaying
    
    fun togglePlayPause() {
        playbackManager.togglePlayPause()
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
