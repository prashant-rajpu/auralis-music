package com.auralis.app.data.source

import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.MusicSource
import com.auralis.app.network.YouTubeMusicApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicSource @Inject constructor(
    private val api: YouTubeMusicApi
) : MusicSource {
    override val provider = Provider.YOUTUBE
    override val priority = 0

    // Trending is approximated by a search, so real charts from other sources come first
    override val trendingPriority = 90

    override suspend fun search(query: String): List<Track> = api.searchTracks(query)

    override suspend fun trending(): List<Track> = api.searchTracks("Top Global Hits 2026")

    override suspend fun related(seed: Track): List<Track> = api.getRelatedTracks(seed.providerId)
}
