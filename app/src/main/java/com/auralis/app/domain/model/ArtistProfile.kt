package com.auralis.app.domain.model

data class ArtistRelease(
    val browseId: String,
    val title: String,
    val year: String?,
    val coverUrl: String?,
    val type: String
)

data class ArtistProfile(
    val id: String,
    val name: String,
    val bannerUrl: String?,
    val monthlyListeners: String?,
    val bio: String?,
    val topSongs: List<Track>,
    val releases: List<ArtistRelease>
)
