package com.auralis.app.lyrics

import com.auralis.app.data.local.TrackDao
import com.auralis.app.data.local.TrackEntity
import com.auralis.app.domain.model.LyricLine
import com.auralis.app.domain.model.Track
import com.auralis.app.network.JioSaavnApi
import com.auralis.app.network.LrclibApi
import com.auralis.app.network.NetEaseLyricsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lrclibApi: LrclibApi,
    private val netEaseApi: NetEaseLyricsApi,
    private val jioSaavnApi: JioSaavnApi,
    private val trackDao: TrackDao
) {
    private val memoryCache = mutableMapOf<String, List<LyricLine>>()

    suspend fun getLyrics(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        val cacheKey = "${track.title.lowercase().trim()}_${track.artist.lowercase().trim()}"

        // 1. In-memory session cache
        memoryCache[cacheKey]?.let { if (it.isNotEmpty()) return@withContext it }

        // 2. Pre-populated lyrics on track model
        track.lyrics?.let {
            if (it.isNotEmpty()) {
                memoryCache[cacheKey] = it
                return@withContext it
            }
        }

        // 3. Local Room database (offline downloaded tracks)
        try {
            val localEntity = trackDao.getTrackById(track.id)
            if (localEntity?.syncedLyricsJson != null && localEntity.syncedLyricsJson.isNotEmpty()) {
                val parsed = TrackEntity.parseLyricsJson(localEntity.syncedLyricsJson)
                if (parsed.isNotEmpty()) {
                    memoryCache[cacheKey] = parsed
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            // Ignore DB error
        }

        val cleanTitle = cleanSearchQuery(track.title)
        val cleanArtist = cleanSearchQuery(track.artist)

        // 4. LRCLIB (Primary synced LRC provider)
        try {
            val durationSec = if (track.durationMs > 0) (track.durationMs / 1000).toInt() else null
            val resp = lrclibApi.getLyrics(cleanTitle, cleanArtist, durationSec)
            if (resp?.syncedLyrics != null && resp.syncedLyrics.isNotBlank()) {
                val parsed = LrcParser.parse(resp.syncedLyrics)
                if (parsed.isNotEmpty()) {
                    memoryCache[cacheKey] = parsed
                    return@withContext parsed
                }
            } else if (resp?.plainLyrics != null && resp.plainLyrics.isNotBlank()) {
                val plain = parsePlainLyricsToLines(resp.plainLyrics, track.durationMs)
                if (plain.isNotEmpty()) {
                    memoryCache[cacheKey] = plain
                    return@withContext plain
                }
            }
        } catch (e: Exception) {
            // Fallback to next provider
        }

        // 5. NetEase Lyrics API
        try {
            val query = "$cleanTitle $cleanArtist"
            val searchRes = netEaseApi.searchSong(query)
            val songId = searchRes?.result?.songs?.firstOrNull()?.id
            if (songId != null) {
                val lyricRes = netEaseApi.getSongLyric(songId)
                val rawLrc = lyricRes?.lrc?.lyric
                if (!rawLrc.isNullOrBlank()) {
                    val parsed = LrcParser.parse(rawLrc)
                    if (parsed.isNotEmpty()) {
                        memoryCache[cacheKey] = parsed
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        // 6. JioSaavn lyrics API
        try {
            val jioResp = jioSaavnApi.getLyrics(track.id)
            val rawLyrics = jioResp.lyrics
            if (!rawLyrics.isNullOrBlank()) {
                val clean = rawLyrics.replace("<br>", "\n").replace("<br/>", "\n")
                val parsed = if (clean.contains("[")) {
                    LrcParser.parse(clean)
                } else {
                    parsePlainLyricsToLines(clean, track.durationMs)
                }
                if (parsed.isNotEmpty()) {
                    memoryCache[cacheKey] = parsed
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            // End of providers
        }

        emptyList()
    }

    private fun cleanSearchQuery(text: String): String {
        return text
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("(?i)feat\\..*"), "")
            .replace(Regex("(?i)ft\\..*"), "")
            .trim()
    }

    private fun parsePlainLyricsToLines(plainText: String, durationMs: Long): List<LyricLine> {
        val lines = plainText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val safeDuration = if (durationMs > 0) durationMs else 180000L
        val interval = (safeDuration - 5000L).coerceAtLeast(1000L) / lines.size.coerceAtLeast(1)

        return lines.mapIndexed { index, line ->
            LyricLine(timestampMs = index * interval, text = line)
        }
    }
}
