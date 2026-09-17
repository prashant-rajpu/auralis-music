package com.auralis.app.presentation.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.Track
import com.auralis.app.network.ArtistProfile
import com.auralis.app.network.YouTubeMusicApi
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

sealed interface ArtistUiState {
    object Loading : ArtistUiState
    data class Success(val profile: ArtistProfile) : ArtistUiState
    data class Error(val message: String) : ArtistUiState
}

@HiltViewModel
class ArtistProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youTubeMusicApi: YouTubeMusicApi,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val rawArtistName: String = savedStateHandle["artistName"] ?: "Artist"
    val artistName: String = try {
        URLDecoder.decode(rawArtistName, StandardCharsets.UTF_8.name())
    } catch (e: Exception) {
        rawArtistName
    }

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        loadArtistProfile()
    }

    fun loadArtistProfile() {
        _uiState.value = ArtistUiState.Loading
        viewModelScope.launch {
            try {
                val profile = youTubeMusicApi.getArtistProfile(artistName)
                if (profile != null) {
                    _uiState.value = ArtistUiState.Success(profile)
                } else {
                    _uiState.value = ArtistUiState.Error("Could not load artist profile.")
                }
            } catch (e: Exception) {
                _uiState.value = ArtistUiState.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track>) {
        playbackManager.playTrack(track, queue)
    }

    fun playAllTopSongs() {
        val state = _uiState.value
        if (state is ArtistUiState.Success && state.profile.topSongs.isNotEmpty()) {
            playbackManager.playPlaylist(state.profile.topSongs)
        }
    }

    fun startArtistRadio() {
        val state = _uiState.value
        if (state is ArtistUiState.Success && state.profile.topSongs.isNotEmpty()) {
            playbackManager.startRadio(state.profile.topSongs.first())
        }
    }
}
