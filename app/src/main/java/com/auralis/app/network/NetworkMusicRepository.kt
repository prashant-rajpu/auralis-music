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
        // 1. Try JioSaavn Trending (320kbps full tracks)
        try {
            val jioSaavnTracks = fetchJioSaavnTrending()
            if (jioSaavnTracks.isNotEmpty()) {
                Log.d("NetworkMusicRepository", "Fetched ${jioSaavnTracks.size} tracks from JioSaavn")
                return@withContext enrichWithDownloadStatus(jioSaavnTracks)
            }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "JioSaavn trending failed, trying Audius", e)
        }

        // 2. Try Audius Trending (320kbps full MP3 tracks)
        try {
            val audiusTracks = fetchAudiusTrending()
            if (audiusTracks.isNotEmpty()) {
                Log.d("NetworkMusicRepository", "Fetched ${audiusTracks.size} tracks from Audius")
                return@withContext enrichWithDownloadStatus(audiusTracks)
            }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "Audius trending failed, trying Deezer", e)
        }

        // 3. Try Deezer Trending (Charts & Previews)
        try {
            val deezerTracks = fetchDeezerTrending()
            if (deezerTracks.isNotEmpty()) {
                Log.d("NetworkMusicRepository", "Fetched ${deezerTracks.size} tracks from Deezer")
                return@withContext enrichWithDownloadStatus(deezerTracks)
            }
        } catch (e: Exception) {
            Log.w("NetworkMusicRepository", "Deezer trending failed", e)
        }

        // 4. Offline Fallback: If all networks fail, serve cached offline tracks!
        val offlineTracks = fetchLocalTracks()
        if (offlineTracks.isNotEmpty()) {
            Log.d("NetworkMusicRepository", "Serving ${offlineTracks.size} offline downloaded tracks")
            return@withContext offlineTracks
        }

        throw IllegalStateException("No tracks available online or offline. Please check your connection.")
    }

    override suspend fun searchTracks(query: String): List<Track> {
        return searchTracks(query, "All")
    }

    override suspend fun searchTracks(query: String, source: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<Track>()

        val searchAll = source.equals("All", ignoreCase = true)
        val searchYouTube = searchAll || source.contains("YouTube", ignoreCase = true)
        val searchJio = searchAll || source.contains("Jio", ignoreCase = true)
        val searchAudius = searchAll || source.contains("Audius", ignoreCase = true)

        // 1. YouTube Music search (huge library coverage)
        if (searchYouTube) {
            try {
                val ytResults = youTubeMusicApi.searchTracks(query)
                results.addAll(ytResults)
            } catch (e: Exception) {
                Log.w("NetworkMusicRepository", "YouTube search failed for query: $query", e)
            }
        }

        // 2. Search JioSaavn (320kbps uncompressed stream)
        if (searchJio) {
            try {
                val jioSaavnResults = searchJioSaavn(query)
                results.addAll(jioSaavnResults)
            } catch (e: Exception) {
                Log.w("NetworkMusicRepository", "JioSaavn search failed for query: $query", e)
            }
        }

        // 3. Search Audius
        if (searchAudius) {
            try {
                val audiusResults = searchAudius(query)
                results.addAll(audiusResults)
            } catch (e: Exception) {
                Log.w("NetworkMusicRepository", "Audius search failed for query: $query", e)
            }
        }

        // 4. Search local offline matches
        val localMatches = trackDao.getAllTracks()
            .filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
            .map { it.toDomainModel() }
        results.addAll(0, localMatches)

        enrichWithDownloadStatus(results.distinctBy { it.id })
    }

    private suspend fun fetchJioSaavnTrending(): List<Track> {
        val response = jioSaavnApi.getTrendingPlaylist()
        return response.list?.mapNotNull { mapJioSaavnDtoToTrack(it) } ?: emptyList()
    }

    private suspend fun searchJioSaavn(query: String): List<Track> {
        val response = jioSaavnApi.searchSongs(query)
        return response.results?.mapNotNull { mapJioSaavnDtoToTrack(it) } ?: emptyList()
    }

    private fun mapJioSaavnDtoToTrack(dto: JioSaavnSongDto): Track? {
        val mediaUrl = JioSaavnDecryptor.decryptMediaUrl(dto.moreInfo?.encryptedMediaUrl)
        if (mediaUrl.isNullOrBlank()) return null

        val durationSec = dto.moreInfo?.duration?.toLongOrNull() ?: 180L
        val artist = dto.moreInfo?.artistMap?.primaryArtists?.firstOrNull()?.name
            ?: dto.subtitle
            ?: "Unknown Artist"

        val art = dto.image?.replace("150x150", "500x500") ?: dto.image

        return Track(
            id = "jio_${dto.id ?: dto.title.hashCode()}",
            title = dto.title?.replace("&quot;", "\"")?.replace("&#039;", "'") ?: "Unknown",
            artist = artist.replace("&quot;", "\"").replace("&#039;", "'"),
            albumArtUrl = art,
            mediaUrl = mediaUrl,
            durationMs = durationSec * 1000L,
            source = "JioSaavn",
            qualityBadge = "320 kbps Master"
        )
    }

    private suspend fun fetchAudiusTrending(): List<Track> {
        val response = audiusApi.getTrendingTracks(limit = 25)
        return response.data?.mapNotNull { mapAudiusDtoToTrack(it) } ?: emptyList()
    }

    private suspend fun searchAudius(query: String): List<Track> {
        val response = audiusApi.searchTracks(query = query, limit = 20)
        return response.data?.mapNotNull { mapAudiusDtoToTrack(it) } ?: emptyList()
    }

    private fun mapAudiusDtoToTrack(dto: AudiusTrackDto): Track? {
        val id = dto.id ?: return null
        val streamUrl = "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=Auralis"
        val art = dto.artwork?.art480 ?: dto.artwork?.art150
        val duration = (dto.duration ?: 180L) * 1000L

        return Track(
            id = "audius_$id",
            title = dto.title ?: "Unknown",
            artist = dto.user?.name ?: "Audius Artist",
            albumArtUrl = art,
            mediaUrl = streamUrl,
            durationMs = duration,
            source = "Audius",
            qualityBadge = "320 kbps MP3"
        )
    }

    private suspend fun fetchDeezerTrending(): List<Track> {
        val response = deezerApi.getTrendingTracks()
        return response.data?.filter { !it.preview.isNullOrBlank() }?.map { dto ->
            Track(
                id = "deezer_${dto.id}",
                title = dto.title ?: "Unknown",
                artist = dto.artist?.name ?: "Unknown Artist",
                albumArtUrl = dto.album?.coverXl ?: "",
                mediaUrl = dto.preview!!,
                durationMs = 30000L,
                source = "Deezer",
                qualityBadge = "HQ Preview"
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
