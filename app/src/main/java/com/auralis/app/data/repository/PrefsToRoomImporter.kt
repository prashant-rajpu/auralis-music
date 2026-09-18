package com.auralis.app.data.repository

import android.content.Context
import android.util.Log
import com.auralis.app.domain.model.Track
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves the library out of SharedPreferences JSON blobs and into Room, once, on first launch of
 * the version that introduced the tables.
 *
 * Runs at most once — tracked by a flag in its own preferences file — because a second run would
 * duplicate every playlist. If anything goes wrong the flag is still set: a partial import is a
 * nuisance, but retrying on every launch forever is worse, and the old keys are left in place so
 * nothing is destroyed either way.
 */
@Singleton
class PrefsToRoomImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository
) {

    suspend fun runIfNeeded() {
        val markers = context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
        if (markers.getBoolean(KEY_IMPORTED, false)) return

        try {
            importRecentTracks()
            importSavedPlaylists()
        } catch (e: Exception) {
            Log.w(TAG, "Library import did not finish cleanly; old preferences are left intact", e)
        } finally {
            markers.edit().putBoolean(KEY_IMPORTED, true).apply()
        }
    }

    /**
     * The old recently-played list was a 30-entry JSON array with no timestamps, so plays are
     * back-dated one minute apart in list order. That keeps "most recent first" correct without
     * inventing precise times that were never recorded.
     */
    private suspend fun importRecentTracks() {
        val prefs = context.getSharedPreferences(PERSONALIZATION_PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_TRACKS, null) ?: return
        val tracks = parseTrackArray(json)
        if (tracks.isEmpty()) return

        libraryRepository.remember(tracks)
        val now = System.currentTimeMillis()
        tracks.forEachIndexed { index, track ->
            libraryRepository.recordPlay(
                track = track,
                playedMs = track.durationMs,
                completionRatio = 1f,
                skipped = false,
                context = IMPORT_CONTEXT,
                startedAtMs = now - index * 60_000L
            )
        }
    }

    private suspend fun importSavedPlaylists() {
        val prefs = context.getSharedPreferences(PLAYLIST_PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SAVED_PLAYLISTS, null) ?: return
        val array = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return

        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val title = obj.optString("title") ?: continue
            val tracks = obj.getAsJsonArray("tracks")?.let { parseTrackArray(it) } ?: emptyList()
            val createdAt = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asLong
                ?: System.currentTimeMillis()
            val playlistId = libraryRepository.createPlaylist(title, createdAt)
            if (tracks.isNotEmpty()) libraryRepository.addToPlaylist(playlistId, tracks, createdAt)
        }
    }

    private fun parseTrackArray(json: String): List<Track> =
        runCatching { parseTrackArray(JsonParser.parseString(json).asJsonArray) }.getOrDefault(emptyList())

    private fun parseTrackArray(array: JsonArray): List<Track> = array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.optString("id") ?: return@mapNotNull null
        Track(
            id = id,
            title = obj.optString("title") ?: return@mapNotNull null,
            artist = obj.optString("artist").orEmpty(),
            // Recently-played wrote "albumArtUrl"; saved playlists wrote "art". Both are real
            // data in the wild, so read either rather than dropping half the covers.
            albumArtUrl = (obj.optString("albumArtUrl") ?: obj.optString("art"))?.takeIf { it.isNotBlank() },
            mediaUrl = obj.optString("mediaUrl").orEmpty(),
            durationMs = obj.get("durationMs")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            source = obj.optString("source") ?: "Auralis",
            qualityBadge = obj.optString("qualityBadge") ?: "HQ Audio"
        )
    }

    private fun JsonObject.optString(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private companion object {
        const val TAG = "PrefsToRoomImporter"

        const val MARKER_PREFS = "auralis_migration_markers"
        const val KEY_IMPORTED = "library_imported_v4"

        const val PERSONALIZATION_PREFS = "auralis_personalization_v2"
        const val KEY_RECENT_TRACKS = "recent_tracks_json"

        const val PLAYLIST_PREFS = "auralis_playlists_storage"
        const val KEY_SAVED_PLAYLISTS = "auralis_saved_playlists_json"

        const val IMPORT_CONTEXT = "imported"
    }
}
