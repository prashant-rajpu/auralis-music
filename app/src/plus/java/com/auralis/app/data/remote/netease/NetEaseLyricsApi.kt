package com.auralis.app.data.remote.netease

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface NetEaseLyricsApi {
    @GET("api/search/get/web?csrf_token=&type=1&offset=0&total=true&limit=1")
    suspend fun searchSong(
        @Query("s") query: String
    ): NetEaseSearchResponse?

    @GET("api/song/lyric?os=pc&lv=-1&kv=-1&tv=-1")
    suspend fun getSongLyric(
        @Query("id") songId: Long
    ): NetEaseLyricResponse?
}

data class NetEaseSearchResponse(
    @SerializedName("result") val result: NetEaseSearchResultDto?
)

data class NetEaseSearchResultDto(
    @SerializedName("songs") val songs: List<NetEaseSongDto>?
)

data class NetEaseSongDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String?
)

data class NetEaseLyricResponse(
    @SerializedName("lrc") val lrc: NetEaseLrcDto?
)

data class NetEaseLrcDto(
    @SerializedName("lyric") val lyric: String?
)
