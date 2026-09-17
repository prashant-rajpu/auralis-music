package com.auralis.app.domain.repository

import com.auralis.app.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun fetchLocalTracks(): List<Track>
    suspend fun fetchServerTracks(): List<Track>
    suspend fun downloadTrack(track: Track)
}
