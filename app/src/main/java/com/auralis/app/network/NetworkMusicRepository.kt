package com.auralis.app.network

import com.auralis.app.domain.model.Track
import com.auralis.app.domain.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMusicRepository @Inject constructor(
    private val api: OpenSourceMusicApi
) : MusicRepository {

    override suspend fun fetchLocalTracks(): List<Track> {
        // To be implemented via MediaStore / Room
        return emptyList()
    }

    override suspend fun fetchServerTracks(): List<Track> {
        return try {
            val response = api.getTrendingTracks()
            response.results.map { dto ->
                Track(
                    id = dto.id,
                    title = dto.title,
                    artist = dto.artistName,
                    albumArtUrl = dto.albumImage,
                    mediaUrl = dto.audioUrl,
                    durationMs = dto.duration * 1000L // Convert sec to ms if needed
                )
            }
        } catch (e: Exception) {
            // Mock fallback if the API endpoint is a dummy URL
            listOf(
                Track(
                    id = "demo_1",
                    title = "Open Source Jam",
                    artist = "Auralis Demo",
                    albumArtUrl = "https://picsum.photos/300/300",
                    mediaUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    durationMs = 372000L
                )
            )
        }
    }
}
