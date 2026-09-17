package com.auralis.app.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface AudiusApi {
    @GET("v1/tracks/trending?app_name=Auralis")
    suspend fun getTrendingTracks(
        @Query("limit") limit: Int = 25
    ): AudiusResponse

    @GET("v1/tracks/search?app_name=Auralis")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("limit") limit: Int = 25
    ): AudiusResponse
}

data class AudiusResponse(
    @SerializedName("data") val data: List<AudiusTrackDto>?
)

data class AudiusTrackDto(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("artwork") val artwork: AudiusArtworkDto?,
    @SerializedName("user") val user: AudiusUserDto?
)

data class AudiusArtworkDto(
    @SerializedName("480x480") val art480: String?,
    @SerializedName("150x150") val art150: String?
)

data class AudiusUserDto(
    @SerializedName("name") val name: String?
)
