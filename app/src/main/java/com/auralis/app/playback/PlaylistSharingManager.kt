package com.auralis.app.playback

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.auralis.app.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class SavedPlaylist(
    val id: String,
    val title: String,
    val trackCount: Int,
    val tracks: List<Track>,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Singleton
class PlaylistSharingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auralis_playlists_storage", Context.MODE_PRIVATE)

    private val _userPlaylists = MutableStateFlow<List<SavedPlaylist>>(loadSavedPlaylists())
    val userPlaylists: StateFlow<List<SavedPlaylist>> = _userPlaylists.asStateFlow()

    /**
     * Exports a playlist to a shareable Base64 URL-safe token.
     * Output format: auralis://playlist/<base64_payload>
     */
    fun exportPlaylistToBase64(title: String, tracks: List<Track>): String {
        val root = JsonObject().apply {
            addProperty("title", title)
            addProperty("v", 1)
            val array = JsonArray()
            tracks.forEach { track ->
                val item = JsonObject().apply {
                    addProperty("id", track.id)
                    addProperty("t", track.title)
                    addProperty("a", track.artist)
                    addProperty("d", track.durationMs)
                    track.albumArtUrl?.let { addProperty("art", it) }
                    addProperty("s", track.source)
                }
                array.add(item)
            }
            add("tracks", array)
        }

        val jsonBytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        val base64 = Base64.encodeToString(jsonBytes, Base64.URL_SAFE or Base64.NO_WRAP)
        return "auralis://playlist/$base64"
    }

    /**
     * Imports a playlist from:
     * 1. auralis://playlist/<base64> or raw Base64 string
     * 2. Plain text / CSV with lines like "Artist - Title" or "Title, Artist"
     */
    fun importPlaylistFromInput(rawInput: String): Pair<String, List<Track>>? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Case 1: Auralis Base64 Share Link or Token
        val base64Payload = when {
            trimmed.startsWith("auralis://playlist/") -> trimmed.removePrefix("auralis://playlist/").trim()
            trimmed.startsWith("https://auralis.app/playlist/") -> trimmed.removePrefix("https://auralis.app/playlist/").trim()
            else -> trimmed
        }

        try {
            val decodedBytes = Base64.decode(base64Payload, Base64.URL_SAFE or Base64.DEFAULT)
            val jsonString = String(decodedBytes, StandardCharsets.UTF_8)
            val root = gson.fromJson(jsonString, JsonObject::class.java)

            if (root.has("tracks")) {
                val title = if (root.has("title")) root.get("title").asString else "Imported Playlist"
                val tracks = mutableListOf<Track>()
                val array = root.getAsJsonArray("tracks")
                for (element in array) {
                    val obj = element.asJsonObject
                    val id = if (obj.has("id")) obj.get("id").asString else "yt_${System.nanoTime()}"
                    val trackTitle = if (obj.has("t")) obj.get("t").asString else "Unknown Track"
                    val artist = if (obj.has("a")) obj.get("a").asString else "Unknown Artist"
                    val duration = if (obj.has("d")) obj.get("d").asLong else 180000L
                    val art = if (obj.has("art") && !obj.get("art").isJsonNull) obj.get("art").asString else null
                    val src = if (obj.has("s")) obj.get("s").asString else "YouTube"

                    tracks.add(
                        Track(
                            id = id,
                            title = trackTitle,
                            artist = artist,
                            albumArtUrl = art ?: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
                            mediaUrl = if (id.startsWith("http")) id else "yt:$id",
                            durationMs = duration,
                            source = src,
                            qualityBadge = "Imported HQ"
                        )
                    )
                }

                if (tracks.isNotEmpty()) {
                    return Pair(title, tracks)
                }
            }
        } catch (e: Exception) {
            Log.d("PlaylistSharingManager", "Not a valid Base64 payload, testing text parser...", e)
        }

        // Case 2: Plain text list or CSV
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isNotEmpty()) {
            val tracks = mutableListOf<Track>()
            for (line in lines) {
                // Ignore comments or empty header rows
                if (line.startsWith("#") || line.startsWith("//") || line.equals("title,artist", ignoreCase = true)) continue

                var artist = "Various Artists"
                var title = line

                if (line.contains(" - ")) {
                    val parts = line.split(" - ", limit = 2)
                    artist = parts[0].trim()
                    title = parts[1].trim()
                } else if (line.contains(",")) {
                    val parts = line.split(",", limit = 2)
                    title = parts[0].trim()
                    artist = parts[1].trim()
                }

                val pseudoId = "yt_import_${title.hashCode()}_${artist.hashCode()}"
                tracks.add(
                    Track(
                        id = pseudoId,
                        title = title,
                        artist = artist,
                        albumArtUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
                        mediaUrl = "yt:$title $artist",
                        durationMs = 210000L,
                        source = "Imported",
                        qualityBadge = "320 kbps Master"
                    )
                )
            }

            if (tracks.isNotEmpty()) {
                return Pair("Imported Mix (${tracks.size} Songs)", tracks)
            }
        }

        return null
    }

    /**
     * Saves a playlist locally so user can access it anytime under Library / Playlists
     */
    fun savePlaylist(title: String, tracks: List<Track>): SavedPlaylist {
        val id = "pl_${System.currentTimeMillis()}"
        val newPlaylist = SavedPlaylist(
            id = id,
            title = title,
            trackCount = tracks.size,
            tracks = tracks
        )
        val current = _userPlaylists.value.toMutableList()
        current.add(0, newPlaylist)
        _userPlaylists.value = current
        persistPlaylists(current)
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        val current = _userPlaylists.value.filter { it.id != playlistId }
        _userPlaylists.value = current
        persistPlaylists(current)
    }

    private fun persistPlaylists(list: List<SavedPlaylist>) {
        val array = JsonArray()
        for (pl in list) {
            val obj = JsonObject().apply {
                addProperty("id", pl.id)
                addProperty("title", pl.title)
                addProperty("createdAt", pl.createdAtMs)
                val tracksArr = JsonArray()
                pl.tracks.forEach { t ->
                    val tObj = JsonObject().apply {
                        addProperty("id", t.id)
                        addProperty("title", t.title)
                        addProperty("artist", t.artist)
                        addProperty("durationMs", t.durationMs)
                        t.albumArtUrl?.let { addProperty("art", it) }
                        addProperty("mediaUrl", t.mediaUrl)
                        addProperty("source", t.source)
                    }
                    tracksArr.add(tObj)
                }
                add("tracks", tracksArr)
            }
            array.add(obj)
        }
        prefs.edit().putString(KEY_SAVED_PLAYLISTS, array.toString()).apply()
    }

    private fun loadSavedPlaylists(): List<SavedPlaylist> {
        val jsonStr = prefs.getString(KEY_SAVED_PLAYLISTS, null) ?: return emptyList()
        val result = mutableListOf<SavedPlaylist>()
        try {
            val array = gson.fromJson(jsonStr, JsonArray::class.java)
            for (elem in array) {
                val obj = elem.asJsonObject
                val id = obj.get("id").asString
                val title = obj.get("title").asString
                val createdAt = if (obj.has("createdAt")) obj.get("createdAt").asLong else 0L

                val tracks = mutableListOf<Track>()
                if (obj.has("tracks")) {
                    for (tElem in obj.getAsJsonArray("tracks")) {
                        val tObj = tElem.asJsonObject
                        tracks.add(
                            Track(
                                id = tObj.get("id").asString,
                                title = tObj.get("title").asString,
                                artist = tObj.get("artist").asString,
                                albumArtUrl = if (tObj.has("art") && !tObj.get("art").isJsonNull) tObj.get("art").asString else null,
                                mediaUrl = tObj.get("mediaUrl").asString,
                                durationMs = tObj.get("durationMs").asLong,
                                source = if (tObj.has("source")) tObj.get("source").asString else "Custom",
                                qualityBadge = "320 kbps Master"
                            )
                        )
                    }
                }
                result.add(SavedPlaylist(id, title, tracks.size, tracks, createdAt))
            }
        } catch (e: Exception) {
            Log.e("PlaylistSharingManager", "Failed to parse saved playlists", e)
        }
        return result
    }

    companion object {
        private const val KEY_SAVED_PLAYLISTS = "auralis_saved_playlists_json"
    }
}
