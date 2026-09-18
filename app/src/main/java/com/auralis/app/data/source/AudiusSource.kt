package com.auralis.app.data.source

import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.MusicSource
import com.auralis.app.network.AudiusApi
import com.auralis.app.network.AudiusTrackDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiusSource @Inject constructor(
    private val api: AudiusApi
) : MusicSource {
    override val provider = Provider.AUDIUS
    override val priority = 20

    override suspend fun search(query: String): List<Track> =
        api.searchTracks(query).data.orEmpty().mapNotNull { it.toTrack() }

    override suspend fun trending(): List<Track> =
        api.getTrendingTracks(limit = 25).data.orEmpty().mapNotNull { it.toTrack() }

    override suspend fun related(seed: Track): List<Track> =
        search(seed.artist).filter { it.id != seed.id }

    private fun AudiusTrackDto.toTrack(): Track? {
        val id = id ?: return null
        return Track(
            id = "auralis_global_$id",
            title = title ?: "Unknown",
            artist = user?.name ?: "Artist",
            albumArtUrl = artwork?.art480 ?: artwork?.art150,
            mediaUrl = "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=Auralis",
            durationMs = (duration ?: 180L) * 1000L,
            source = provider.displayName,
            qualityBadge = "320 kbps MP3"
        )
    }
}
