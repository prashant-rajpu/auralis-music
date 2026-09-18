package com.auralis.app.lyrics

import android.util.Log
import com.auralis.app.data.local.TrackDao
import com.auralis.app.data.local.TrackEntity
import com.auralis.app.domain.model.LyricLine
import com.auralis.app.domain.model.Track
import com.auralis.app.playback.AuralisSettingsPreferences
import com.auralis.app.playback.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    sources: Set<@JvmSuppressWildcards LyricsSource>,
    private val trackDao: TrackDao,
    private val settingsPreferences: AuralisSettingsPreferences
) {
    private val orderedSources = sources.sortedBy { it.priority }
    private val memoryCache = mutableMapOf<String, List<LyricLine>>()

    /** Automatic plus every backend present in this build, for the settings picker. */
    val availableProviders: List<LyricsProvider> =
        listOf(LyricsProvider.AUTO) + orderedSources.map { it.provider }

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
        val preferred = settingsPreferences.lyricsProvider.value
        val chain = orderedSources.filter { it.provider == preferred } +
            orderedSources.filter { it.provider != preferred }

        var result: List<LyricLine> = emptyList()
        for (source in chain) {
            result = try {
                source.fetch(cleanTitle, cleanArtist, track.durationMs)
            } catch (e: Exception) {
                emptyList()
            }
            if (result.isNotEmpty()) break
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
}
