package com.auralis.app.data.remote.jamendo

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface JamendoApi {
    @GET("v3.0/tracks/")
    suspend fun tracks(
        @Query("client_id") clientId: String,
        @Query("search") search: String? = null,
        @Query("order") order: String? = null,
        @Query("limit") limit: Int = 25,
        @Query("format") format: String = "json",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse

    @GET("v3.0/tracks/similar/")
    suspend fun similar(
        @Query("client_id") clientId: String,
        @Query("id") trackId: String,
        @Query("limit") limit: Int = 20,
        @Query("format") format: String = "json",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse
}

data class JamendoResponse(
    @SerializedName("results") val results: List<JamendoTrackDto>?
)

data class JamendoTrackDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("duration") val durationSec: Long?,
    @SerializedName("artist_name") val artistName: String?,
    @SerializedName("album_name") val albumName: String?,
    @SerializedName("album_image") val albumImage: String?,
    @SerializedName("image") val image: String?,
    @SerializedName("audio") val audio: String?
)
