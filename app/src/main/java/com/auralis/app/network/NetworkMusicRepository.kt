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
            response.data?.filter { !it.preview.isNullOrBlank() }?.map { dto ->
                Track(
                    id = dto.id.toString(),
                    title = dto.title ?: "Unknown",
                    artist = dto.artist?.name ?: "Unknown Artist",
                    albumArtUrl = dto.album?.coverXl ?: "",
                    mediaUrl = dto.preview!!,
                    durationMs = 30000L
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("NetworkMusicRepository", "Failed to fetch tracks", e)
            throw e // Throw to let ViewModel handle the error message
        }
    }
}
