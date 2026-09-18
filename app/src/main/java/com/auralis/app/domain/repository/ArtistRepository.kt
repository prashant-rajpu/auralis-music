package com.auralis.app.domain.repository

import com.auralis.app.domain.model.ArtistProfile

interface ArtistRepository {
    suspend fun getArtistProfile(artistName: String): ArtistProfile?
}
