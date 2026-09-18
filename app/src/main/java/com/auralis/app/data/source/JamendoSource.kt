package com.auralis.app.data.source

import com.auralis.app.BuildConfig
import com.auralis.app.data.remote.jamendo.JamendoApi
import com.auralis.app.data.remote.jamendo.JamendoTrackDto
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.MusicSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamendoSource @Inject constructor(
    private val api: JamendoApi
) : MusicSource {
    private val clientId = BuildConfig.JAMENDO_CLIENT_ID

    override val provider = Provider.JAMENDO
    override val priority = 30
    override val isEnabled = clientId.isNotBlank()

    override suspend fun search(query: String): List<Track> =
        api.tracks(clientId, search = query).toTracks()

    override suspend fun trending(): List<Track> =
        api.tracks(clientId, order = "popularity_week").toTracks()

    override suspend fun related(seed: Track): List<Track> =
        api.similar(clientId, seed.providerId).toTracks().filter { it.id != seed.id }

    private fun com.auralis.app.data.remote.jamendo.JamendoResponse.toTracks(): List<Track> =
        results.orEmpty().mapNotNull { it.toTrack() }

    private fun JamendoTrackDto.toTrack(): Track? {
        val id = id ?: return null
        val streamUrl = audio?.takeIf { it.startsWith("https://") } ?: return null
        return Track(
            id = "jamendo_$id",
            title = name ?: "Unknown",
            artist = artistName ?: "Artist",
            albumArtUrl = albumImage ?: image,
            mediaUrl = streamUrl,
            durationMs = (durationSec ?: 180L) * 1000L,
            source = provider.displayName,
            qualityBadge = "192 kbps MP3"
        )
    }
}
