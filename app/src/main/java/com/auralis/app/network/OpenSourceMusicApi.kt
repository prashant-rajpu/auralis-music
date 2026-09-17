package com.auralis.app.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// Using a generic open-source music API structure. 
// Can be mapped to Jamendo, Piped API, or JioSaavn wrappers.

interface OpenSourceMusicApi {
    @GET("search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20
    ): SearchResponse

    @GET("trending")
    suspend fun getTrendingTracks(): SearchResponse
}

data class SearchResponse(
    @SerializedName("results") val results: List<ApiTrackDto>
)

data class ApiTrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist_name") val artistName: String,
    @SerializedName("album_image") val albumImage: String?,
    @SerializedName("audio_url") val audioUrl: String,
    @SerializedName("duration") val duration: Long
)
