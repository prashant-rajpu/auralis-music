package com.auralis.app.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

data class LyricsOvhResponse(
    @SerializedName("lyrics") val lyrics: String? = null,
    @SerializedName("error") val error: String? = null
)

interface LyricsOvhApi {
    @GET("v1/{artist}/{title}")
    suspend fun getLyrics(
        @Path("artist") artist: String,
        @Path("title") title: String
    ): LyricsOvhResponse?
}
