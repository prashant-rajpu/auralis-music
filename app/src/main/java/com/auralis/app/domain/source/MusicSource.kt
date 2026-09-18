package com.auralis.app.domain.source

import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track

/** One catalog the app can search and browse. Implementations are contributed per build flavor. */
interface MusicSource {
    val provider: Provider

    /** Lower runs first when ordering merged search results. */
    val priority: Int

    /** Lower is tried first when building the trending feed. */
    val trendingPriority: Int get() = priority

    val isEnabled: Boolean get() = true

    suspend fun search(query: String): List<Track>

    suspend fun trending(): List<Track> = emptyList()

    suspend fun related(seed: Track): List<Track> = emptyList()

    /** Everything the source can list without a query; only meaningful for local sources. */
    suspend fun library(): List<Track> = emptyList()
}
