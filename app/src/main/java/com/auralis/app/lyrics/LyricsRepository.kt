package com.auralis.app.lyrics

import android.util.Log
import com.auralis.app.data.local.TrackDao
import com.auralis.app.data.local.TrackEntity
import com.auralis.app.domain.model.LyricLine
import com.auralis.app.domain.model.Track
import com.auralis.app.network.LrclibApi
import com.auralis.app.network.LyricsOvhApi
import com.auralis.app.network.NetEaseLyricsApi
import com.auralis.app.playback.AuralisSettingsPreferences
import com.auralis.app.playback.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lrclibApi: LrclibApi,
    private val lyricsOvhApi: LyricsOvhApi,
    private val netEaseApi: NetEaseLyricsApi,
    private val trackDao: TrackDao,
    private val settingsPreferences: AuralisSettingsPreferences
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

        // 3. Local Room database (offline downloaded tracks or previously cached)
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
        val preferredProvider = settingsPreferences.lyricsProvider.value

        var result: List<LyricLine> = emptyList()

        when (preferredProvider) {
            LyricsProvider.LRCLIB -> {
                result = fetchFromLrclib(cleanTitle, cleanArtist, track.durationMs)
                if (result.isEmpty()) result = fetchFromLyricsOvh(cleanTitle, cleanArtist, track.durationMs)
            }
            LyricsProvider.LYRICS_OVH -> {
                result = fetchFromLyricsOvh(cleanTitle, cleanArtist, track.durationMs)
                if (result.isEmpty()) result = fetchFromLrclib(cleanTitle, cleanArtist, track.durationMs)
            }
            LyricsProvider.NETEASE -> {
                result = fetchFromNetEase(cleanTitle, cleanArtist)
                if (result.isEmpty()) result = fetchFromLrclib(cleanTitle, cleanArtist, track.durationMs)
            }
            LyricsProvider.AUTO -> {
                // Priority: 1. LRCLIB (synced) -> 2. Lyrics.ovh (clean full lyrics) -> 3. NetEase
                result = fetchFromLrclib(cleanTitle, cleanArtist, track.durationMs)
                if (result.isEmpty()) {
                    result = fetchFromLyricsOvh(cleanTitle, cleanArtist, track.durationMs)
                }
                if (result.isEmpty()) {
                    result = fetchFromNetEase(cleanTitle, cleanArtist)
                }
            }
        }

        if (result.isNotEmpty()) {
            memoryCache[cacheKey] = result

            // Cache to local Room database if enabled and track is offline
            if (settingsPreferences.offlineLyricsEnabled.value) {
                try {
                    val localEntity = trackDao.getTrackById(track.id)
                    if (localEntity != null && localEntity.syncedLyricsJson.isNullOrEmpty()) {
                        val updated = localEntity.copy(
                            syncedLyricsJson = TrackEntity.encodeLyricsJson(result)
                        )
                        trackDao.insertTrack(updated)
                    }
                } catch (e: Exception) {
                    Log.w("LyricsRepository", "Could not cache lyrics to DB", e)
                }
            }
        }

        result
    }

    private suspend fun fetchFromLrclib(title: String, artist: String, durationMs: Long): List<LyricLine> {
        return try {
            val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else null
            val resp = lrclibApi.getLyrics(title, artist, durationSec)
            if (resp?.syncedLyrics != null && resp.syncedLyrics.isNotBlank()) {
                val parsed = LrcParser.parse(resp.syncedLyrics)
                if (parsed.isNotEmpty()) return parsed
            }
            if (resp?.plainLyrics != null && resp.plainLyrics.isNotBlank()) {
                parsePlainLyricsToLines(resp.plainLyrics, durationMs)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchFromLyricsOvh(title: String, artist: String, durationMs: Long): List<LyricLine> {
        return try {
            val resp = lyricsOvhApi.getLyrics(artist = artist, title = title)
            val raw = resp?.lyrics
            if (!raw.isNullOrBlank()) {
                parsePlainLyricsToLines(raw, durationMs)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchFromNetEase(title: String, artist: String): List<LyricLine> {
        return try {
            val query = "$title $artist"
            val searchRes = netEaseApi.searchSong(query)
            val songId = searchRes?.result?.songs?.firstOrNull()?.id ?: return emptyList()
            val lyricRes = netEaseApi.getSongLyric(songId)
            val rawLrc = lyricRes?.lrc?.lyric
            if (!rawLrc.isNullOrBlank()) {
                LrcParser.parse(rawLrc)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun cleanSearchQuery(text: String): String {
        return text
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("(?i)feat\\..*"), "")
            .replace(Regex("(?i)ft\\..*"), "")
            .replace(Regex("(?i)official.*"), "")
            .replace(Regex("(?i)lyrics?.*"), "")
            .replace(Regex("(?i)remix.*"), "")
            .replace(Regex("(?i)video.*"), "")
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
