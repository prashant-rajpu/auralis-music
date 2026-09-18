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

    private val _sourceFilter = MutableStateFlow("All")
    val sourceFilter: StateFlow<String> = _sourceFilter.asStateFlow()

    private val _selectedMood = MutableStateFlow("All")
    val selectedMood: StateFlow<String> = _selectedMood.asStateFlow()

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
                performSearch(_searchQuery.value, _sourceFilter.value)
            } else if (_selectedMood.value != "All") {
                performSearch(_selectedMood.value, _sourceFilter.value)
            } else {
                loadTrendingTracks()
            }
        }
    }

    fun selectMood(mood: String) {
        _selectedMood.value = mood
        if (_selectedTab.value == HomeTab.Downloaded) {
            _selectedTab.value = HomeTab.Trending
        }
        if (mood == "All") {
            if (_searchQuery.value.isNotBlank()) {
                performSearch(_searchQuery.value, _sourceFilter.value)
            } else {
                loadTrendingTracks()
            }
        } else {
            performSearch(mood, _sourceFilter.value)
        }
    }

    fun selectSourceFilter(source: String) {
        _sourceFilter.value = source
        val query = if (_searchQuery.value.isNotBlank()) {
            _searchQuery.value
        } else if (_selectedMood.value != "All") {
            _selectedMood.value
        } else if (source != "All" && source != "320 kbps Master" && source != "Lossless") {
            source
        } else {
            ""
        }
        if (query.isNotBlank()) {
            performSearch(query, source)
        } else {
            loadTrendingTracks()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            if (_selectedTab.value == HomeTab.Downloaded) {
                loadOfflineTracks()
            } else if (_selectedMood.value != "All") {
                performSearch(_selectedMood.value, _sourceFilter.value)
            } else {
                loadTrendingTracks()
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // 400ms debounce
            performSearch(query, _sourceFilter.value)
        }
    }

    private fun performSearch(query: String, source: String = "All") {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val tracks = repository.searchTracks(query, source)
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
                    _uiState.value = HomeUiState.Error("No downloaded tracks yet. Tap download on any song to save for offline playback!")
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
            if (_selectedTab.value == HomeTab.Downloaded) {
                loadOfflineTracks()
            } else if (_searchQuery.value.isNotBlank()) {
                performSearch(_searchQuery.value, _sourceFilter.value)
            } else if (_selectedMood.value != "All") {
                performSearch(_selectedMood.value, _sourceFilter.value)
            } else {
                loadTrendingTracks()
            }
        }
    }

    fun playTrack(track: Track) {
        val currentList = (_uiState.value as? HomeUiState.Success)?.tracks ?: listOf(track)
        playbackManager.playTrack(track, currentList)
    }

    val jamSession = playbackManager.jamSession
    val jamState = playbackManager.jamState
    val lastJamAction = playbackManager.lastJamAction

    fun startJam(jamId: String, username: String) {
        playbackManager.jamClient.startJam(jamId, username)
        playbackManager.currentTrack.value?.let { track ->
            playbackManager.jamClient.broadcastPlaybackState(
                track,
                playbackManager.currentPositionMs.value,
                playbackManager.isPlaying.value
            )
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

    val recentTracks: StateFlow<List<Track>> = playbackManager.personalizationManager.recentTracks
    val topArtists: StateFlow<List<String>> = playbackManager.personalizationManager.topArtists

    fun getTimeOfDayGreeting(): Pair<String, String> =
        playbackManager.personalizationManager.getTimeOfDayGreeting()

    fun getTimeOfDaySuggestedMood(): String =
        playbackManager.personalizationManager.getTimeOfDaySuggestedMood()

    fun playNext(track: Track) {
        playbackManager.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackManager.addToQueue(track)
    }

    fun startRadio(track: Track) {
        playbackManager.startRadio(track)
    }

    val playbackSpeed = playbackManager.playbackSpeed
    val sleepTimerMinutesRemaining = playbackManager.sleepTimerMinutesRemaining

    fun setPlaybackSpeed(speed: Float) {
        playbackManager.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int?) {
        playbackManager.setSleepTimer(minutes)
    }

    val isInfiniteRadioAutoplayEnabled = playbackManager.isInfiniteRadioAutoplayEnabled

    fun toggleInfiniteRadioAutoplay(enabled: Boolean) {
        playbackManager.setInfiniteRadioAutoplay(enabled)
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val tracks: List<Track>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
