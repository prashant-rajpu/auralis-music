package com.auralis.app.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.auralis.app.domain.model.Track
import com.auralis.app.ui.theme.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

enum class PinkAccentTheme(val id: String, val displayName: String, val color: Color, val hex: String) {
    BABY_ROSE("baby_rose", "Baby Rose (Original)", BabyPinkPrimary, "#FFB6C1"),
    SAKURA_BLOSSOM("sakura", "Sakura Blossom", Color(0xFFFFC0CB), "#FFC0CB"),
    COTTON_CANDY("cotton_candy", "Cotton Candy Pink", Color(0xFFFF99B8), "#FF99B8"),
    ROSE_GOLD("rose_gold", "Sunset Rose Gold", Color(0xFFFFAEBA), "#FFAEBA")
}

@Singleton
class PersonalizationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auralis_personalization_v2", Context.MODE_PRIVATE)

    private val _recentTracks = MutableStateFlow<List<Track>>(loadRecentTracks())
    val recentTracks: StateFlow<List<Track>> = _recentTracks.asStateFlow()

    private val _topArtists = MutableStateFlow<List<String>>(loadTopArtists())
    val topArtists: StateFlow<List<String>> = _topArtists.asStateFlow()

    private val _accentTheme = MutableStateFlow(loadAccentTheme())
    val accentTheme: StateFlow<PinkAccentTheme> = _accentTheme.asStateFlow()

    fun recordTrackPlay(track: Track) {
        // Increment play count
        val currentPlayCount = prefs.getInt("play_count_${track.id}", 0) + 1
        val artistCount = prefs.getInt("artist_count_${track.artist}", 0) + 1

        prefs.edit()
            .putInt("play_count_${track.id}", currentPlayCount)
            .putInt("artist_count_${track.artist}", artistCount)
            .putLong("last_played_${track.id}", System.currentTimeMillis())
            .apply()

        // Update Recent Tracks (Max 30, unique, newest first)
        val current = _recentTracks.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)
        val trimmed = current.take(30)
        _recentTracks.value = trimmed
        saveRecentTracks(trimmed)

        // Update Top Artists
        updateTopArtists()
    }

    fun getPlayCount(trackId: String): Int {
        return prefs.getInt("play_count_$trackId", 0)
    }

    fun setAccentTheme(theme: PinkAccentTheme) {
        prefs.edit().putString("accent_theme_id", theme.id).apply()
        _accentTheme.value = theme
    }

    private fun loadAccentTheme(): PinkAccentTheme {
        val id = prefs.getString("accent_theme_id", PinkAccentTheme.BABY_ROSE.id)
        return PinkAccentTheme.values().find { it.id == id } ?: PinkAccentTheme.BABY_ROSE
    }

    private fun updateTopArtists() {
        val allKeys = prefs.all
        val artistMap = mutableMapOf<String, Int>()
        allKeys.forEach { (key, value) ->
            if (key.startsWith("artist_count_") && value is Int) {
                val artistName = key.removePrefix("artist_count_")
                artistMap[artistName] = value
            }
        }
        val sorted = artistMap.entries.sortedByDescending { it.value }.map { it.key }.take(5)
        _topArtists.value = sorted
    }

    private fun loadTopArtists(): List<String> {
        val allKeys = prefs.all
        val artistMap = mutableMapOf<String, Int>()
        allKeys.forEach { (key, value) ->
            if (key.startsWith("artist_count_") && value is Int) {
                val artistName = key.removePrefix("artist_count_")
                artistMap[artistName] = value
            }
        }
        return artistMap.entries.sortedByDescending { it.value }.map { it.key }.take(5)
    }

    fun getTimeOfDayGreeting(): Pair<String, String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> Pair("Good Morning ☀️", "Morning Acoustic & Fresh Beats")
            in 12..16 -> Pair("Good Afternoon ☕", "Afternoon Energy & Focus")
            in 17..21 -> Pair("Good Evening 🌇", "Evening Chill & Mood Mix")
            else -> Pair("Late Night Romance 🌙", "Dreamy Lo-Fi & Gentle Melodies")
        }
    }

    fun getTimeOfDaySuggestedMood(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Acoustic"
            in 12..16 -> "Energize"
            in 17..21 -> "Relax"
            else -> "Romance"
        }
    }

    private fun saveRecentTracks(tracks: List<Track>) {
        val array = JSONArray()
        tracks.forEach { track ->
            val obj = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("albumArtUrl", track.albumArtUrl.orEmpty())
                put("mediaUrl", track.mediaUrl)
                put("durationMs", track.durationMs)
                put("source", track.source)
                put("qualityBadge", track.qualityBadge)
            }
            array.put(obj)
        }
        prefs.edit().putString("recent_tracks_json", array.toString()).apply()
    }

    private fun loadRecentTracks(): List<Track> {
        val json = prefs.getString("recent_tracks_json", null) ?: return emptyList()
        val result = mutableListOf<Track>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    Track(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        albumArtUrl = obj.optString("albumArtUrl").ifEmpty { null },
                        mediaUrl = obj.getString("mediaUrl"),
                        durationMs = obj.optLong("durationMs", 210000L),
                        source = obj.optString("source", "Auralis"),
                        qualityBadge = obj.optString("qualityBadge", "320 kbps Master")
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore corrupted JSON
        }
        return result
    }
}
