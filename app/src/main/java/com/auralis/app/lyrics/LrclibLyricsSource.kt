package com.auralis.app.lyrics

import com.auralis.app.domain.model.LyricLine
import com.auralis.app.network.LrclibApi
import com.auralis.app.playback.LyricsProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrclibLyricsSource @Inject constructor(
    private val api: LrclibApi
) : LyricsSource {
    override val provider = LyricsProvider.LRCLIB
    override val priority = 0

    override suspend fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else null
        val resp = api.getLyrics(title, artist, durationSec) ?: return emptyList()
        resp.syncedLyrics?.takeIf { it.isNotBlank() }?.let { synced ->
            val parsed = LrcParser.parse(synced)
            if (parsed.isNotEmpty()) return parsed
        }
        return resp.plainLyrics?.takeIf { it.isNotBlank() }
            ?.let { PlainLyrics.toTimedLines(it, durationMs) }
            ?: emptyList()
    }
}
