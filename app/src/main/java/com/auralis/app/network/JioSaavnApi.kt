package com.auralis.app.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApi {
    @GET("api.php?__call=search.getResults&_format=json&_marker=0&ctx=web6dot0&api_version=4")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("n") count: Int = 25,
        @Query("p") page: Int = 1
    ): JioSaavnSearchResponse

    @GET("api.php?__call=playlist.getDetails&_format=json&_marker=0&ctx=web6dot0&api_version=4&listid=110858205")
    suspend fun getTrendingPlaylist(): JioSaavnPlaylistResponse
}

data class JioSaavnSearchResponse(
    @SerializedName("results") val results: List<JioSaavnSongDto>?
)

data class JioSaavnPlaylistResponse(
    @SerializedName("title") val title: String?,
    @SerializedName("list") val list: List<JioSaavnSongDto>?
)

data class JioSaavnSongDto(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("subtitle") val subtitle: String?,
    @SerializedName("image") val image: String?,
    @SerializedName("more_info") val moreInfo: JioSaavnMoreInfoDto?
)

data class JioSaavnMoreInfoDto(
    @SerializedName("album") val album: String?,
    @SerializedName("encrypted_media_url") val encryptedMediaUrl: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("artistMap") val artistMap: JioSaavnArtistMapDto?
)

data class JioSaavnArtistMapDto(
    @SerializedName("primary_artists") val primaryArtists: List<JioSaavnArtistItemDto>?
)

data class JioSaavnArtistItemDto(
    @SerializedName("name") val name: String?
)
