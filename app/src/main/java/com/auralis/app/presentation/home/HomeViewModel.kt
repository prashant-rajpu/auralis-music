package com.auralis.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One horizontally scrolling row of the Home feed. */
data class Shelf(
    val id: String,
    val title: String,
    val subtitle: String?,
    val tracks: List<Track>
)

sealed interface HomeFeedState {
    data object Loading : HomeFeedState
    data class Ready(val shelves: List<Shelf>) : HomeFeedState
    data class Empty(val message: String) : HomeFeedState
}

private const val MAX_SHELF_TRACKS = 12
private const val MIN_SHELF_TRACKS = 3

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val personalization = playbackManager.personalizationManager

    private val _feed = MutableStateFlow<HomeFeedState>(HomeFeedState.Loading)
    val feed: StateFlow<HomeFeedState> = _feed.asStateFlow()

    val recentTracks: StateFlow<List<Track>> = personalization.recentTracks
    val topArtists: StateFlow<List<String>> = personalization.topArtists

    init {
        refresh()
    }

    fun greeting(): Pair<String, String> = personalization.getTimeOfDayGreeting()

    /**
     * Every shelf is fetched independently and in parallel: one catalog being down or slow costs
     * that shelf, not the whole feed. A shelf too thin to be worth a row is dropped, and a track
     * only appears in the first shelf that claims it so the feed does not repeat itself.
     */
    fun refresh() {
        viewModelScope.launch {
            _feed.value = HomeFeedState.Loading

            val mood = personalization.getTimeOfDaySuggestedMood()
            val favouriteArtists = personalization.topArtists.value.take(2)

            val requests = buildList {
                add(
                    ShelfRequest("mood:$mood", "$mood for right now", "Picked for this time of day") {
                        repository.searchTracks(mood)
                    }
                )
                favouriteArtists.forEach { artist ->
                    add(
                        ShelfRequest("artist:$artist", "More like $artist", "Because you keep playing them") {
                            repository.searchTracks(artist)
                        }
                    )
                }
                repository.availableProviders.forEach { provider ->
                    add(
                        ShelfRequest("trending:${provider.id}", "Trending on ${provider.displayName}", null) {
                            repository.fetchServerTracks(provider)
                        }
                    )
                }
                add(
                    ShelfRequest("local", "On this device", "Your downloads and local files") {
                        repository.fetchLocalTracks()
                    }
                )
            }

            val loaded = coroutineScope {
                requests
                    .map { request -> async { request to runCatching { request.load() }.getOrDefault(emptyList()) } }
                    .awaitAll()
            }

            val claimed = mutableSetOf<String>()
            val shelves = loaded.mapNotNull { (request, tracks) ->
                val unique = tracks.asSequence()
                    .filter { it.id !in claimed }
                    .distinctBy { it.id }
                    .take(MAX_SHELF_TRACKS)
                    .toList()
                if (unique.size < MIN_SHELF_TRACKS) return@mapNotNull null
                claimed += unique.map { it.id }
                Shelf(request.id, request.title, request.subtitle, unique)
            }

            _feed.value = if (shelves.isEmpty()) {
                HomeFeedState.Empty("Nothing to show yet. Check your connection, or add music to this device.")
            } else {
                HomeFeedState.Ready(shelves)
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

    // Listen Together lives on Home because it is the feature people start a session from.
    val jamSession = playbackManager.jamSession
    val isJamConnected = playbackManager.jamClient.isConnected

    fun startJam(code: String, username: String) {
        playbackManager.jamClient.startJam(code, username)
        playbackManager.currentTrack.value?.let { track ->
            playbackManager.jamClient.broadcastPlaybackState(
                track,
                playbackManager.currentPositionMs.value,
                playbackManager.isPlaying.value
            )
        }
    }

    fun joinJam(code: String, username: String) = playbackManager.jamClient.joinJam(code, username)

    fun leaveJam() = playbackManager.jamClient.disconnect()

    fun reconnectJam() = playbackManager.jamClient.reconnect()
}

private class ShelfRequest(
    val id: String,
    val title: String,
    val subtitle: String?,
    val load: suspend () -> List<Track>
)
