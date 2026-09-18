package com.auralis.app.data.repository

import com.auralis.app.domain.model.ArtistProfile
import com.auralis.app.domain.repository.ArtistRepository
import javax.inject.Inject
import javax.inject.Singleton
import com.auralis.app.data.remote.youtube.YouTubeMusicApi

@Singleton
class YouTubeArtistRepository @Inject constructor(
    private val api: YouTubeMusicApi
) : ArtistRepository {
    override suspend fun getArtistProfile(artistName: String): ArtistProfile? = api.getArtistProfile(artistName)
}
