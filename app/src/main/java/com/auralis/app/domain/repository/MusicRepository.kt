package com.auralis.app.domain.repository

import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track

interface MusicRepository {
    /** Online catalogs available in this build, in priority order. */
    val availableProviders: List<Provider>

    /** Downloads plus music stored on the device. */
    suspend fun fetchLocalTracks(): List<Track>
    suspend fun fetchServerTracks(filter: Provider? = null): List<Track>
    suspend fun searchTracks(query: String, filter: Provider? = null): List<Track>
    suspend fun relatedTracks(seed: Track): List<Track>
    suspend fun downloadTrack(track: Track)
    suspend fun deleteDownloadedTrack(trackId: String)
    suspend fun isTrackDownloaded(trackId: String): Boolean
}
