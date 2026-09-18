package com.auralis.app.lyrics

import com.auralis.app.domain.model.LyricLine
import com.auralis.app.network.LyricsOvhApi
import com.auralis.app.playback.LyricsProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsOvhLyricsSource @Inject constructor(
    private val api: LyricsOvhApi
) : LyricsSource {
    override val provider = LyricsProvider.LYRICS_OVH
    override val priority = 10

    override suspend fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val raw = api.getLyrics(artist = artist, title = title)?.lyrics
        return if (raw.isNullOrBlank()) emptyList() else PlainLyrics.toTimedLines(raw, durationMs)
    }
}
