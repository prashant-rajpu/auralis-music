package com.auralis.app.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Genre and mood entry points. Each one is a query the catalogs already understand. */
enum class ExploreMood(val label: String, val query: String, val emoji: String) {
    ENERGIZE("Energize", "energetic upbeat", "⚡"),
    WORKOUT("Workout", "workout gym", "🏋️"),
    RELAX("Relax", "relaxing calm", "🌿"),
    FOCUS("Focus", "focus instrumental study", "🎧"),
    PARTY("Party", "party dance", "🪩"),
    ROMANCE("Romance", "romantic love songs", "💗"),
    LOFI("Lo-fi", "lofi chill beats", "🌙"),
    ACOUSTIC("Acoustic", "acoustic unplugged", "🎸")
}

sealed interface ExploreState {
    data object Idle : ExploreState
    data object Loading : ExploreState
    data class Results(val tracks: List<Track>) : ExploreState
    data class Error(val message: String) : ExploreState
}

private const val SEARCH_DEBOUNCE_MS = 400L

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    val availableProviders: List<Provider> = repository.availableProviders

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _mood = MutableStateFlow<ExploreMood?>(null)
    val mood: StateFlow<ExploreMood?> = _mood.asStateFlow()

    private val _providerFilter = MutableStateFlow<Provider?>(null)
    val providerFilter: StateFlow<Provider?> = _providerFilter.asStateFlow()

    private val _state = MutableStateFlow<ExploreState>(ExploreState.Loading)
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTrending()
    }

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            reload()
            return
        }
        _mood.value = null
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            search(value)
        }
    }

    fun selectMood(mood: ExploreMood?) {
        _mood.value = mood
        _query.value = ""
        searchJob?.cancel()
        reload()
    }

    fun selectProvider(provider: Provider?) {
        _providerFilter.value = provider
        reload()
    }

    fun retry() = reload()

    private fun reload() {
        val mood = _mood.value
        val query = _query.value
        when {
            query.isNotBlank() -> search(query)
            mood != null -> search(mood.query)
            else -> loadTrending()
        }
    }

    private fun loadTrending() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = ExploreState.Loading
            _state.value = runCatching { repository.fetchServerTracks(_providerFilter.value) }
                .fold(
                    onSuccess = { tracks ->
                        if (tracks.isEmpty()) {
                            ExploreState.Error("Nothing trending right now. Try a search.")
                        } else {
                            ExploreState.Results(tracks)
                        }
                    },
                    onFailure = { ExploreState.Error(it.message ?: "Could not reach the catalogs.") }
                )
        }
    }

    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = ExploreState.Loading
            _state.value = runCatching { repository.searchTracks(query, _providerFilter.value) }
                .fold(
                    onSuccess = { tracks ->
                        if (tracks.isEmpty()) {
                            ExploreState.Error("No results for \"$query\".")
                        } else {
                            ExploreState.Results(tracks)
                        }
                    },
                    onFailure = { ExploreState.Error(it.message ?: "Search failed.") }
                )
        }
    }

    fun play(track: Track) {
        val within = (_state.value as? ExploreState.Results)?.tracks ?: listOf(track)
        playbackManager.playTrack(track, within)
    }

    fun playNext(track: Track) = playbackManager.playNext(track)

    fun addToQueue(track: Track) = playbackManager.addToQueue(track)

    fun startRadio(track: Track) = playbackManager.startRadio(track)

    fun toggleDownload(track: Track) {
        viewModelScope.launch {
            if (track.isDownloaded) {
                repository.deleteDownloadedTrack(track.id)
            } else {
                repository.downloadTrack(track)
            }
            reload()
        }
    }
}
