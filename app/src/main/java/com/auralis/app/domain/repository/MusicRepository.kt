package com.auralis.app.domain.repository

import com.auralis.app.domain.model.Track

interface MusicRepository {
    suspend fun fetchLocalTracks(): List<Track>
    suspend fun fetchServerTracks(): List<Track>
    suspend fun searchTracks(query: String): List<Track>
    suspend fun searchTracks(query: String, source: String): List<Track>
    suspend fun downloadTrack(track: Track)
    suspend fun deleteDownloadedTrack(trackId: String)
    suspend fun isTrackDownloaded(trackId: String): Boolean
}
