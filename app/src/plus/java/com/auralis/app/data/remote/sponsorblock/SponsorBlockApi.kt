package com.auralis.app.data.remote.sponsorblock

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class SponsorSegment(
    @SerializedName("category") val category: String,
    @SerializedName("actionType") val actionType: String,
    @SerializedName("segment") val segment: List<Double>,
    @SerializedName("UUID") val uuid: String? = null
) {
    val startMs: Long
        get() = ((segment.getOrNull(0) ?: 0.0) * 1000).toLong()

    val endMs: Long
        get() = ((segment.getOrNull(1) ?: 0.0) * 1000).toLong()
}

interface SponsorBlockApi {
    @GET("api/skipSegments")
    suspend fun getSkipSegments(
        @Query("videoID") videoId: String,
        @Query("category") categories: List<String> = listOf(
            "sponsor", "selfpromo", "interaction", "intro", "outro", "music_offtopic"
        ),
        @Query("actionType") actionType: String = "skip"
    ): List<SponsorSegment>
}
