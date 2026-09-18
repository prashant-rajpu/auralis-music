package com.auralis.app.lyrics

import com.auralis.app.domain.model.LyricLine

import com.auralis.app.playback.LyricsProvider
import javax.inject.Inject
import javax.inject.Singleton
import com.auralis.app.data.remote.netease.NetEaseLyricsApi

@Singleton
class NetEaseLyricsSource @Inject constructor(
    private val api: NetEaseLyricsApi
) : LyricsSource {
    override val provider = LyricsProvider.NETEASE
    override val priority = 20

    override suspend fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val songId = api.searchSong("$title $artist")?.result?.songs?.firstOrNull()?.id ?: return emptyList()
        val rawLrc = api.getSongLyric(songId)?.lrc?.lyric
        return if (rawLrc.isNullOrBlank()) emptyList() else LrcParser.parse(rawLrc)
    }
}
