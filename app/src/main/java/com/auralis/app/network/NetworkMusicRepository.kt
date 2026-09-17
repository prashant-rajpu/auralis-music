package com.auralis.app.network

import android.util.Log
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Singleton
class NetworkMusicRepository @Inject constructor(
    private val api: OpenSourceMusicApi,
    private val trackDao: com.auralis.app.data.local.TrackDao,
    private val downloader: com.auralis.app.data.local.OfflineDownloader
) : MusicRepository {

    override suspend fun fetchLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getAllTracks().map { it.toDomainModel() }
    }

    override suspend fun downloadTrack(track: Track) {
        downloader.downloadTrack(track)
    }

    override suspend fun fetchServerTracks(): List<Track> {
        return try {
            val response = api.getTrendingTracks()
            response.data.filter { !it.preview.isNullOrBlank() }.map { dto ->
                Track(
                    id = dto.id.toString(),
                    title = dto.title,
                    artist = dto.artist.name,
                    albumArtUrl = dto.album.coverXl ?: "",
                    mediaUrl = dto.preview!!,
                    durationMs = 30000L // Deezer previews are 30 seconds
                )
            }
        } catch (e: Exception) {
            Log.e("NetworkMusicRepository", "Failed to fetch tracks", e)
            emptyList()
        }
    }
}
