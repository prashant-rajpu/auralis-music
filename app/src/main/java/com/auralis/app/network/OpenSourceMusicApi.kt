package com.auralis.app.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenSourceMusicApi {
    @GET("search/track")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): DeezerResponse

    @GET("chart/0/tracks")
    suspend fun getTrendingTracks(): DeezerResponse
}

data class DeezerResponse(
    @SerializedName("data") val data: List<DeezerTrackDto>?
)

data class DeezerTrackDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String?,
    @SerializedName("preview") val preview: String?,
    @SerializedName("duration") val duration: Long,
    @SerializedName("artist") val artist: DeezerArtist?,
    @SerializedName("album") val album: DeezerAlbum?
)

data class DeezerArtist(
    @SerializedName("name") val name: String?
)

data class DeezerAlbum(
    @SerializedName("cover_xl") val coverXl: String?
)
