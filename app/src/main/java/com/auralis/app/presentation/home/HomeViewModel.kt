package com.auralis.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackManager: PlaybackManager,
    private val jamClient: com.auralis.app.network.JamWebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTrendingTracks()
    }

    private fun loadTrendingTracks() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val tracks = repository.fetchServerTracks()
                _uiState.value = HomeUiState.Success(tracks)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun playTrack(track: Track) {
        playbackManager.playTrack(track)
    }

    fun connectToJam(jamId: String, username: String) {
        jamClient.connect(jamId, username)
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val trendingTracks: List<Track>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
