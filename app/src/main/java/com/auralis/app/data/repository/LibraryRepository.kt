package com.auralis.app.data.repository

import com.auralis.app.data.local.CatalogTrackEntity
import com.auralis.app.data.local.DislikedTrackEntity
import com.auralis.app.data.local.HistoryDao
import com.auralis.app.data.local.LibraryDao
import com.auralis.app.data.local.LikedTrackEntity
import com.auralis.app.data.local.PlayHistoryEntity
import com.auralis.app.data.local.PlaylistEntity
import com.auralis.app.data.local.PlaylistTrackEntity
import com.auralis.app.domain.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A playlist plus the count the list UI needs, without a second query per row. */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val trackCount: Int,
    val coverArtUrl: String?,
    val updatedAtMs: Long
)

/**
 * The one place the app reads and writes the user's own library.
 *
 * Screens and view models talk to this rather than to DAOs, so the storage underneath can change
 * without touching the UI — which matters because likes, playlists and history are being moved out
 * of in-memory sets and SharedPreferences blobs, and something has to hide the seam.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val historyDao: HistoryDao
) {

    // ---- Catalog ------------------------------------------------------------------------------

    /**
     * Remembers tracks so a playlist or history row can be rendered without re-querying a catalog.
     * Called whenever tracks pass through the app; cheap enough to be unconditional.
     */
    suspend fun remember(tracks: List<Track>, nowMs: Long = System.currentTimeMillis()) {
        if (tracks.isEmpty()) return
        libraryDao.upsertCatalogTracks(tracks.map { CatalogTrackEntity.from(it, nowMs) })
    }

    suspend fun remember(track: Track, nowMs: Long = System.currentTimeMillis()) =
        remember(listOf(track), nowMs)

    suspend fun track(trackId: String): Track? = libraryDao.catalogTrack(trackId)?.toTrack()

    suspend fun tracks(trackIds: List<String>): List<Track> {
        if (trackIds.isEmpty()) return emptyList()
        // Preserve the caller's order: an IN (...) query returns rows in whatever order it likes,
        // which would silently scramble a restored queue.
        val byId = libraryDao.catalogTracks(trackIds).associateBy { it.id }
        return trackIds.mapNotNull { byId[it]?.toTrack() }
    }

    // ---- Likes --------------------------------------------------------------------------------

    val likedTrackIds: Flow<Set<String>> = libraryDao.likedTrackIds().map { it.toSet() }
    val dislikedTrackIds: Flow<Set<String>> = libraryDao.dislikedTrackIds().map { it.toSet() }
    val likedTracks: Flow<List<Track>> = libraryDao.likedTracks().map { rows -> rows.map { it.toTrack() } }

    /** Liking clears a dislike and vice versa; holding both at once has no meaning. */
    suspend fun setLiked(track: Track, liked: Boolean, nowMs: Long = System.currentTimeMillis()) {
        remember(track, nowMs)
        if (liked) {
            libraryDao.like(LikedTrackEntity(track.id, nowMs))
            libraryDao.undislike(track.id)
        } else {
            libraryDao.unlike(track.id)
        }
    }

    suspend fun setDisliked(track: Track, disliked: Boolean, nowMs: Long = System.currentTimeMillis()) {
        remember(track, nowMs)
        if (disliked) {
            libraryDao.dislike(DislikedTrackEntity(track.id, nowMs))
            libraryDao.unlike(track.id)
        } else {
            libraryDao.undislike(track.id)
        }
    }

    // ---- Playlists ----------------------------------------------------------------------------

    /**
     * Each playlist carries its track count and cover art. Combining the per-playlist flows means
     * the list emits once with everything resolved, rather than every row querying for itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val playlists: Flow<List<PlaylistSummary>> = libraryDao.playlists().flatMapLatest { rows ->
        if (rows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                rows.map { playlist ->
                    libraryDao.playlistTracks(playlist.id).map { tracks ->
                        PlaylistSummary(
                            id = playlist.id,
                            name = playlist.name,
                            trackCount = tracks.size,
                            coverArtUrl = tracks.firstOrNull { it.id == playlist.coverTrackId }?.albumArtUrl
                                ?: tracks.firstOrNull()?.albumArtUrl,
                            updatedAtMs = playlist.updatedAtMs
                        )
                    }
                }
            ) { it.toList() }
        }
    }

    fun playlistTracks(playlistId: String): Flow<List<Track>> =
        libraryDao.playlistTracks(playlistId).map { rows -> rows.map { it.toTrack() } }

    suspend fun createPlaylist(name: String, nowMs: Long = System.currentTimeMillis()): String {
        val id = UUID.randomUUID().toString()
        libraryDao.upsertPlaylist(
            PlaylistEntity(
                id = id,
                name = name,
                coverTrackId = null,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
                sortIndex = libraryDao.nextPlaylistSortIndex()
            )
        )
        return id
    }

    suspend fun renamePlaylist(playlistId: String, name: String, nowMs: Long = System.currentTimeMillis()) =
        libraryDao.renamePlaylist(playlistId, name, nowMs)

    suspend fun deletePlaylist(playlistId: String) = libraryDao.deletePlaylist(playlistId)

    suspend fun addToPlaylist(playlistId: String, track: Track, nowMs: Long = System.currentTimeMillis()) {
        remember(track, nowMs)
        libraryDao.addToPlaylist(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                position = libraryDao.nextPositionIn(playlistId),
                addedAtMs = nowMs
            )
        )
    }

    suspend fun addToPlaylist(playlistId: String, tracks: List<Track>, nowMs: Long = System.currentTimeMillis()) {
        remember(tracks, nowMs)
        var position = libraryDao.nextPositionIn(playlistId)
        tracks.forEach { track ->
            libraryDao.addToPlaylist(PlaylistTrackEntity(playlistId, track.id, position++, nowMs))
        }
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) =
        libraryDao.removeFromPlaylist(playlistId, trackId)

    suspend fun reorderPlaylist(
        playlistId: String,
        orderedTrackIds: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ) = libraryDao.reorderPlaylist(playlistId, orderedTrackIds, nowMs)

    // ---- History ------------------------------------------------------------------------------

    fun recentlyPlayed(limit: Int = 50): Flow<List<Track>> =
        historyDao.recentlyPlayed(limit).map { rows -> rows.map { it.toTrack() } }

    fun mostPlayed(limit: Int = 50): Flow<List<Track>> =
        historyDao.mostPlayedTracks(limit).map { rows -> rows.map { it.toTrack() } }

    fun topArtists(limit: Int = 30): Flow<List<String>> =
        historyDao.topArtists(limit).map { rows -> rows.map { it.artist } }

    val historyCount: Flow<Int> = historyDao.historyCount()

    suspend fun recordPlay(
        track: Track,
        playedMs: Long,
        completionRatio: Float,
        skipped: Boolean,
        context: String,
        moodLabel: String? = null,
        sessionId: String? = null,
        startedAtMs: Long = System.currentTimeMillis()
    ) {
        remember(track, startedAtMs)
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = startedAtMs }
        historyDao.record(
            PlayHistoryEntity(
                trackId = track.id,
                startedAtMs = startedAtMs,
                playedMs = playedMs,
                completionRatio = completionRatio.coerceIn(0f, 1f),
                skipped = skipped,
                context = context,
                moodLabel = moodLabel,
                sessionId = sessionId,
                hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            )
        )
    }

    suspend fun clearHistory() = historyDao.clearHistory()
}
