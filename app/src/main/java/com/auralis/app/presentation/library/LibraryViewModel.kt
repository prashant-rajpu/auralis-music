package com.auralis.app.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.data.local.MediaStoreSource
import com.auralis.app.data.repository.LibraryRepository
import com.auralis.app.data.repository.PlaylistSummary
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySection(val label: String) {
    PLAYLISTS("Playlists"),
    LIKED("Liked"),
    DOWNLOADS("Downloads"),
    ON_DEVICE("On this device"),
    RECENT("Recently played"),
    MOST_PLAYED("Most played"),
    ARTISTS("Artists")
}

data class LibraryFilesState(
    val isLoading: Boolean = true,
    val downloads: List<Track> = emptyList(),
    val onDevice: List<Track> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager,
    private val mediaStoreSource: MediaStoreSource
) : ViewModel() {

    /** Runtime permission that unlocks on-device music, and whether it has been granted. */
    val localAudioPermission: String = mediaStoreSource.permission

    private val _hasLocalAudioPermission = MutableStateFlow(mediaStoreSource.hasPermission())
    val hasLocalAudioPermission: StateFlow<Boolean> = _hasLocalAudioPermission.asStateFlow()

    private val _section = MutableStateFlow(LibrarySection.PLAYLISTS)
    val section: StateFlow<LibrarySection> = _section.asStateFlow()

    private val _filesState = MutableStateFlow(LibraryFilesState())
    val filesState: StateFlow<LibraryFilesState> = _filesState.asStateFlow()

    private val started = SharingStarted.WhileSubscribed(5_000)

    val playlists: StateFlow<List<PlaylistSummary>> =
        libraryRepository.playlists.stateIn(viewModelScope, started, emptyList())

    val likedTracks: StateFlow<List<Track>> =
        libraryRepository.likedTracks.stateIn(viewModelScope, started, emptyList())

    val likedTrackIds: StateFlow<Set<String>> =
        libraryRepository.likedTrackIds.stateIn(viewModelScope, started, emptySet())

    val recentlyPlayed: StateFlow<List<Track>> =
        libraryRepository.recentlyPlayed().stateIn(viewModelScope, started, emptyList())

    val mostPlayed: StateFlow<List<Track>> =
        libraryRepository.mostPlayed().stateIn(viewModelScope, started, emptyList())

    val topArtists: StateFlow<List<String>> =
        libraryRepository.topArtists().stateIn(viewModelScope, started, emptyList())

    init {
        refreshFiles()
    }

    fun selectSection(section: LibrarySection) {
        _section.value = section
    }

    /** The permission can be granted in system settings while the app is in the background. */
    fun refreshLocalAudioPermission() {
        val granted = mediaStoreSource.hasPermission()
        val changed = granted != _hasLocalAudioPermission.value
        _hasLocalAudioPermission.value = granted
        if (changed && granted) refreshFiles()
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _filesState.value = _filesState.value.copy(isLoading = true, error = null)
            runCatching { repository.fetchLocalTracks() }
                .onSuccess { tracks ->
                    libraryRepository.remember(tracks)
                    // fetchLocalTracks returns downloads and device files together; the library
                    // splits them because they are two different things to the user.
                    val (onDevice, downloads) = tracks.partition { it.provider == Provider.LOCAL }
                    _filesState.value = LibraryFilesState(false, downloads, onDevice)
                }
                .onFailure { error ->
                    _filesState.value = _filesState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Could not read your library."
                    )
                }
        }
    }

    // ---- Playlists ----------------------------------------------------------------------------

    fun playlistTracks(playlistId: String): StateFlow<List<Track>> =
        libraryRepository.playlistTracks(playlistId)
            .stateIn(viewModelScope, started, emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch { libraryRepository.createPlaylist(name.trim()) }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch { libraryRepository.renamePlaylist(playlistId, name.trim()) }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { libraryRepository.deletePlaylist(playlistId) }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { libraryRepository.removeFromPlaylist(playlistId, trackId) }
    }

    fun reorderPlaylist(playlistId: String, orderedTrackIds: List<String>) {
        viewModelScope.launch { libraryRepository.reorderPlaylist(playlistId, orderedTrackIds) }
    }

    // ---- Playback -----------------------------------------------------------------------------

    fun play(track: Track, within: List<Track>) {
        viewModelScope.launch { libraryRepository.remember(within.ifEmpty { listOf(track) }) }
        playbackManager.playTrack(track, within.ifEmpty { listOf(track) })
    }

    fun playAll(tracks: List<Track>) {
        tracks.firstOrNull()?.let { play(it, tracks) }
    }

    fun playNext(track: Track) = playbackManager.playNext(track)

    fun addToQueue(track: Track) = playbackManager.addToQueue(track)

    fun startRadio(track: Track) = playbackManager.startRadio(track)

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            libraryRepository.setLiked(track, liked = track.id !in likedTrackIds.value)
        }
    }

    fun toggleDownload(track: Track) {
        viewModelScope.launch {
            if (track.isDownloaded) {
                repository.deleteDownloadedTrack(track.id)
            } else {
                repository.downloadTrack(track)
            }
            refreshFiles()
        }
    }

    fun clearHistory() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }
}
