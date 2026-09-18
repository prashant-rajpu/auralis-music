package com.auralis.app.data.repository

import com.auralis.app.domain.model.ArtistProfile
import com.auralis.app.domain.repository.ArtistRepository
import com.auralis.app.domain.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Artist page built from a catalog search, for builds without an artist-browse backend. */
@Singleton
class SearchArtistRepository @Inject constructor(
    private val musicRepository: MusicRepository
) : ArtistRepository {
    override suspend fun getArtistProfile(artistName: String): ArtistProfile? {
        val name = artistName.trim()
        if (name.isEmpty()) return null
        val tracks = musicRepository.searchTracks(name)
            .filter { it.artist.contains(name, ignoreCase = true) }
            .ifEmpty { musicRepository.searchTracks(name) }
        return ArtistProfile(
            id = "artist_${name.hashCode()}",
            name = name,
            bannerUrl = tracks.firstOrNull()?.albumArtUrl,
            monthlyListeners = null,
            bio = null,
            topSongs = tracks.take(20),
            releases = emptyList()
        )
    }
}
