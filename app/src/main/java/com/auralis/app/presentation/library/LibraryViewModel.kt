package com.auralis.app.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.data.local.MediaStoreSource
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySection(val label: String) {
    DOWNLOADS("Downloads"),
    ON_DEVICE("On this device"),
    RECENT("Recently played"),
    ARTISTS("Artists")
}

data class LibraryState(
    val isLoading: Boolean = true,
    val downloads: List<Track> = emptyList(),
    val onDevice: List<Track> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackManager: PlaybackManager,
    private val mediaStoreSource: MediaStoreSource
) : ViewModel() {

    /** Runtime permission that unlocks on-device music, and whether it has been granted. */
    val localAudioPermission: String = mediaStoreSource.permission

    private val _hasLocalAudioPermission = MutableStateFlow(mediaStoreSource.hasPermission())
    val hasLocalAudioPermission: StateFlow<Boolean> = _hasLocalAudioPermission.asStateFlow()

    private val _section = MutableStateFlow(LibrarySection.DOWNLOADS)
    val section: StateFlow<LibrarySection> = _section.asStateFlow()

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    val recentTracks: StateFlow<List<Track>> = playbackManager.personalizationManager.recentTracks
    val topArtists: StateFlow<List<String>> = playbackManager.personalizationManager.topArtists

    init {
        refresh()
    }

    fun selectSection(section: LibrarySection) {
        _section.value = section
    }

    /** The permission can be granted in system settings while the app is in the background. */
    fun refreshLocalAudioPermission() {
        val granted = mediaStoreSource.hasPermission()
        val changed = granted != _hasLocalAudioPermission.value
        _hasLocalAudioPermission.value = granted
        if (changed && granted) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { repository.fetchLocalTracks() }
                .onSuccess { tracks ->
                    // fetchLocalTracks returns downloads and device files together; the library
                    // splits them because they are two different things to the user.
                    val (onDevice, downloads) = tracks.partition { it.provider == Provider.LOCAL }
                    _state.value = LibraryState(
                        isLoading = false,
                        downloads = downloads,
                        onDevice = onDevice
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "Could not read your library."
                    )
                }
        }
    }

    fun play(track: Track, within: List<Track>) {
        playbackManager.playTrack(track, within.ifEmpty { listOf(track) })
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
            refresh()
        }
    }
}
