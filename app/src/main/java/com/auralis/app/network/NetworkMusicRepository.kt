package com.auralis.app.network

import android.util.Log
import com.auralis.app.data.local.OfflineDownloader
import com.auralis.app.data.local.TrackDao
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMusicRepository @Inject constructor(
    private val youTubeMusicApi: YouTubeMusicApi,
    private val jioSaavnApi: JioSaavnApi,
    private val audiusApi: AudiusApi,
    private val deezerApi: OpenSourceMusicApi,
    private val trackDao: TrackDao,
    private val downloader: OfflineDownloader
) : MusicRepository {

    override suspend fun fetchLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getAllTracks().map { it.toDomainModel() }
    }

    override suspend fun downloadTrack(track: Track) {
        downloader.downloadTrack(track)
    }

    override suspend fun deleteDownloadedTrack(trackId: String) {
        downloader.deleteTrack(trackId)
    }

    override suspend fun isTrackDownloaded(trackId: String): Boolean = withContext(Dispatchers.IO) {
        trackDao.getAllTracks().any { it.id == trackId }
    }

    override suspend fun fetchServerTracks(): List<Track> = withContext(Dispatchers.IO) {
        // 1. Trending Master Audio (320kbps lossless tracks)
        try {
            val masterTracks = fetchMasterTrending()
            if (masterTracks.isNotEmpty()) {
                Log.d("NetworkMusicRepository", "Fetched ${masterTracks.size} master tracks")
                return@withContext enrichWithDownloadStatus(masterTracks)
            }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "Master trending failed, trying global fallback", e)
        }

        // 2. Global Trending
        try {
            val globalTracks = fetchGlobalTrending()
            if (globalTracks.isNotEmpty()) {
                return@withContext enrichWithDownloadStatus(globalTracks)
            }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "Global trending failed", e)
        }

        // 3. YouTube Music Top Hits fallback
        try {
            val ytTracks = youTubeMusicApi.searchTracks("Top Global Hits 2026")
            if (ytTracks.isNotEmpty()) {
                Log.d("NetworkMusicRepository", "Fetched ${ytTracks.size} YouTube trending tracks")
                return@withContext enrichWithDownloadStatus(ytTracks)
            }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "YouTube trending fallback failed", e)
        }

        // 4. Offline Fallback: If all networks fail, serve cached offline tracks!
        val offlineTracks = fetchLocalTracks()
        if (offlineTracks.isNotEmpty()) {
            Log.d("NetworkMusicRepository", "Serving ${offlineTracks.size} offline downloaded tracks")
            return@withContext offlineTracks
        }

        // 5. Curated Guaranteed Catalog Fallback
        Log.d("NetworkMusicRepository", "Serving curated starter tracks")
        return@withContext enrichWithDownloadStatus(com.auralis.app.playback.CuratedCatalog.getStarterTracks())
    }

    override suspend fun searchTracks(query: String): List<Track> {
        return searchTracks(query, "All")
    }

    override suspend fun searchTracks(query: String, source: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<Track>()

        // 1. YouTube Music search (vast global catalog, live covers, singles, remixes)
        try {
            val ytResults = youTubeMusicApi.searchTracks(query)
            results.addAll(ytResults)
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "YouTube search failed for query: $query", e)
        }

        // 2. Search 320 kbps master audio catalog
        try {
            val masterResults = searchMasterCatalog(query)
            for (mTrack in masterResults) {
                if (results.none { it.id == mTrack.id }) {
                    results.add(mTrack)
                }
            }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "Master audio search failed for query: $query", e)
        }

        // 3. Search local offline matches
        val localMatches = trackDao.getAllTracks()
            .filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
            .map { it.toDomainModel() }
        results.addAll(0, localMatches)

        enrichWithDownloadStatus(results.distinctBy { it.id })
    }

    private suspend fun fetchMasterTrending(): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val response = jioSaavnApi.getTrendingPlaylist()
            response.list?.mapNotNull { mapDtoToTrack(it) }?.let { list.addAll(it) }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "Trending playlist failed", e)
        }

        if (list.size < 10) {
            try {
                val topHits = jioSaavnApi.searchSongs("Top Hits", count = 25)
                topHits.results?.mapNotNull { mapDtoToTrack(it) }?.let { topSongs ->
                    for (song in topSongs) {
                        if (list.none { it.id == song.id }) {
                            list.add(song)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("NetworkMusicRepository", "Top Hits search fallback failed", e)
            }
        }
        return list
    }

    private suspend fun searchMasterCatalog(query: String): List<Track> {
        val response = jioSaavnApi.searchSongs(query)
        return response.results?.mapNotNull { mapDtoToTrack(it) } ?: emptyList()
    }

    private fun mapDtoToTrack(dto: JioSaavnSongDto): Track? {
        val mediaUrl = JioSaavnDecryptor.decryptMediaUrl(dto.moreInfo?.encryptedMediaUrl)
        if (mediaUrl.isNullOrBlank()) return null

        val durationSec = dto.moreInfo?.duration?.toLongOrNull() ?: 180L
        val artist = dto.moreInfo?.artistMap?.primaryArtists?.firstOrNull()?.name
            ?: dto.subtitle
            ?: "Artist"

        val art = dto.image?.replace("150x150", "500x500") ?: dto.image

        return Track(
            id = "auralis_${dto.id ?: dto.title.hashCode()}",
            title = dto.title?.replace("&quot;", "\"")?.replace("&#039;", "'") ?: "Unknown",
            artist = artist.replace("&quot;", "\"").replace("&#039;", "'"),
            albumArtUrl = art,
            mediaUrl = mediaUrl,
            durationMs = durationSec * 1000L,
            source = "Auralis Master",
            qualityBadge = "320 kbps Lossless"
        )
    }

    private suspend fun fetchGlobalTrending(): List<Track> {
        val response = audiusApi.getTrendingTracks(limit = 25)
        return response.data?.mapNotNull { dto ->
            val id = dto.id ?: return@mapNotNull null
            val streamUrl = "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=Auralis"
            val art = dto.artwork?.art480 ?: dto.artwork?.art150
            val duration = (dto.duration ?: 180L) * 1000L

            Track(
                id = "auralis_global_$id",
                title = dto.title ?: "Unknown",
                artist = dto.user?.name ?: "Artist",
                albumArtUrl = art,
                mediaUrl = streamUrl,
                durationMs = duration,
                source = "Auralis Global",
                qualityBadge = "Lossless Audio"
            )
        } ?: emptyList()
    }

    private suspend fun enrichWithDownloadStatus(tracks: List<Track>): List<Track> {
        val downloadedIds = trackDao.getAllTracks().map { it.id }.toSet()
        return tracks.map { track ->
            if (downloadedIds.contains(track.id)) {
                track.copy(isDownloaded = true)
            } else {
                track
            }
        }
    }
}
