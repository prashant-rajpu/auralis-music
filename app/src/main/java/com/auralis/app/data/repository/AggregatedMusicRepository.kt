package com.auralis.app.data.repository

import android.util.Log
import com.auralis.app.data.local.OfflineDownloader
import com.auralis.app.data.local.TrackDao
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.domain.source.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AggregatedMusicRepository @Inject constructor(
    sources: Set<@JvmSuppressWildcards MusicSource>,
    private val trackDao: TrackDao,
    private val downloader: OfflineDownloader
) : MusicRepository {

    private val onlineSources = sources
        .filter { it.isEnabled && it.provider != Provider.LOCAL }
        .sortedBy { it.priority }
    private val localSources = sources.filter { it.isEnabled && it.provider == Provider.LOCAL }

    override val availableProviders: List<Provider> = onlineSources.map { it.provider }

    override suspend fun fetchLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val downloads = trackDao.getAllTracks().map { it.toDomainModel() }
        val device = localSources.flatMap { source ->
            try {
                source.library()
            } catch (e: Exception) {
                Log.w("AggregatedMusicRepository", "Local library failed", e)
                emptyList()
            }
        }
        (downloads + device).distinctBy { it.id }
    }

    override suspend fun fetchServerTracks(filter: Provider?): List<Track> = withContext(Dispatchers.IO) {
        val targets = onlineSources
            .filter { filter == null || it.provider == filter }
            .sortedBy { it.trendingPriority }
        for (source in targets) {
            try {
                val tracks = source.trending()
                if (tracks.isNotEmpty()) {
                    Log.d("AggregatedMusicRepository", "Trending from ${source.provider}: ${tracks.size}")
                    return@withContext enrichWithDownloadStatus(tracks)
                }
            } catch (e: Exception) {
                Log.w("AggregatedMusicRepository", "Trending from ${source.provider} failed", e)
            }
        }

        // Nothing online: serve the library so the app still opens to something
        if (filter == null) fetchLocalTracks() else emptyList()
    }

    override suspend fun searchTracks(query: String, filter: Provider?): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val targets = onlineSources.filter { filter == null || it.provider == filter }
        val remote = coroutineScope {
            targets.map { source ->
                async {
                    try {
                        source.search(query)
                    } catch (e: Exception) {
                        Log.w("AggregatedMusicRepository", "${source.provider} search failed for: $query", e)
                        emptyList()
                    }
                }
            }.awaitAll()
        }.flatten()

        val local = if (filter == null || filter == Provider.LOCAL) {
            // Downloads are a small table, but the device library is queried rather than
            // loaded in full and filtered in memory
            val downloads = trackDao.getAllTracks().map { it.toDomainModel() }.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            }
            val device = localSources.flatMap { source ->
                try {
                    source.search(query)
                } catch (e: Exception) {
                    Log.w("AggregatedMusicRepository", "Local search failed", e)
                    emptyList()
                }
            }
            (downloads + device).distinctBy { it.id }
        } else {
            emptyList()
        }

        enrichWithDownloadStatus((local + remote).distinctBy { it.id })
    }

    override suspend fun relatedTracks(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val own = onlineSources.firstOrNull { it.provider == seed.provider }
        val related = try {
            own?.related(seed).orEmpty()
        } catch (e: Exception) {
            Log.w("AggregatedMusicRepository", "Related from ${seed.provider} failed", e)
            emptyList()
        }
        if (related.isNotEmpty()) {
            related
        } else {
            searchTracks(seed.artist).filter { it.id != seed.id && it.provider != Provider.LOCAL }
        }
    }

    override suspend fun downloadTrack(track: Track) {
        downloader.downloadTrack(track)
    }

    override suspend fun deleteDownloadedTrack(trackId: String) {
        downloader.deleteTrack(trackId)
    }

    override suspend fun isTrackDownloaded(trackId: String): Boolean = withContext(Dispatchers.IO) {
        trackDao.getTrackById(trackId) != null
    }

    private fun enrichWithDownloadStatus(tracks: List<Track>): List<Track> {
        val downloadedIds = trackDao.getAllTracks().map { it.id }.toSet()
        return tracks.map { track ->
            if (track.id in downloadedIds) track.copy(isDownloaded = true) else track
        }
    }
}
