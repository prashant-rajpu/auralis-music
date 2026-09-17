package com.auralis.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.network.JamWebSocketClient
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab {
    Trending,
    Downloaded
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackManager: PlaybackManager,
    private val jamClient: JamWebSocketClient
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(HomeTab.Trending)
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTrendingTracks()
    }

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
        if (tab == HomeTab.Downloaded) {
            loadOfflineTracks()
        } else {
            if (_searchQuery.value.isNotBlank()) {
                performSearch(_searchQuery.value)
            } else {
                loadTrendingTracks()
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            if (_selectedTab.value == HomeTab.Downloaded) {
                loadOfflineTracks()
            } else {
                loadTrendingTracks()
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // 400ms debounce
            performSearch(query)
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val tracks = repository.searchTracks(query)
                if (tracks.isEmpty()) {
                    _uiState.value = HomeUiState.Error("No tracks found for '$query'")
                } else {
                    _uiState.value = HomeUiState.Success(tracks)
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun loadTrendingTracks() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val tracks = repository.fetchServerTracks()
                if (tracks.isEmpty()) {
                    // Fallback to offline tracks if online list is empty
                    val offline = repository.fetchLocalTracks()
                    if (offline.isNotEmpty()) {
                        _selectedTab.value = HomeTab.Downloaded
                        _uiState.value = HomeUiState.Success(offline)
                    } else {
                        _uiState.value = HomeUiState.Error("No tracks found. Check your internet connection.")
                    }
                } else {
                    _uiState.value = HomeUiState.Success(tracks)
                }
            } catch (e: Exception) {
                // If network fails completely, automatically fallback to offline tracks
                val offline = repository.fetchLocalTracks()
                if (offline.isNotEmpty()) {
                    _selectedTab.value = HomeTab.Downloaded
                    _uiState.value = HomeUiState.Success(offline)
                } else {
                    _uiState.value = HomeUiState.Error(e.message ?: "Failed to connect. No offline tracks saved.")
                }
            }
        }
    }

    fun loadOfflineTracks() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val tracks = repository.fetchLocalTracks()
                if (tracks.isEmpty()) {
                    _uiState.value = HomeUiState.Error("No downloaded tracks yet. Tap the download icon on any song to listen offline!")
                } else {
                    _uiState.value = HomeUiState.Success(tracks)
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to load local tracks: ${e.message}")
            }
        }
    }

    fun toggleDownload(track: Track) {
        viewModelScope.launch {
            if (track.isDownloaded) {
                repository.deleteDownloadedTrack(track.id)
            } else {
                repository.downloadTrack(track)
            }
            // Refresh state
            if (_selectedTab.value == HomeTab.Downloaded) {
                loadOfflineTracks()
            } else if (_searchQuery.value.isNotBlank()) {
                performSearch(_searchQuery.value)
            } else {
                loadTrendingTracks()
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
    data class Success(val tracks: List<Track>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
