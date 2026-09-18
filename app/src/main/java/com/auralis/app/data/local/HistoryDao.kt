package com.auralis.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A track plus how often it has been played, for the Most Played shelf. */
data class TrackPlayCount(
    val trackId: String,
    val playCount: Int,
    val lastPlayedAtMs: Long
)

/** An artist and how many plays they account for. */
data class ArtistPlayCount(
    val artist: String,
    val playCount: Int
)

/** A track both partners heard together, ranked by how often. */
data class SharedTrackCount(
    val trackId: String,
    val sharedCount: Int,
    val lastSharedAtMs: Long
)

@Dao
interface HistoryDao {

    @Insert
    suspend fun record(entry: PlayHistoryEntity): Long

    @Query(
        """
        SELECT c.* FROM catalog_tracks c
        INNER JOIN play_history h ON h.trackId = c.id
        GROUP BY c.id
        ORDER BY MAX(h.startedAtMs) DESC
        LIMIT :limit
        """
    )
    fun recentlyPlayed(limit: Int = 50): Flow<List<CatalogTrackEntity>>

    /** Skips are excluded: a track you keep skipping is not a track you play a lot. */
    @Query(
        """
        SELECT trackId, COUNT(*) AS playCount, MAX(startedAtMs) AS lastPlayedAtMs
        FROM play_history
        WHERE skipped = 0
        GROUP BY trackId
        ORDER BY playCount DESC, lastPlayedAtMs DESC
        LIMIT :limit
        """
    )
    fun mostPlayed(limit: Int = 50): Flow<List<TrackPlayCount>>

    /** Most played, already joined to the catalog so callers get whole tracks, not bare ids. */
    @Query(
        """
        SELECT c.* FROM catalog_tracks c
        INNER JOIN play_history h ON h.trackId = c.id
        WHERE h.skipped = 0
        GROUP BY c.id
        ORDER BY COUNT(*) DESC, MAX(h.startedAtMs) DESC
        LIMIT :limit
        """
    )
    fun mostPlayedTracks(limit: Int = 50): Flow<List<CatalogTrackEntity>>

    /**
     * Artists ranked by plays, not by distinct tracks: someone with one song on repeat should
     * outrank someone whose album you played once.
     */
    @Query(
        """
        SELECT c.artist AS artist, COUNT(*) AS playCount
        FROM play_history h
        INNER JOIN catalog_tracks c ON c.id = h.trackId
        WHERE h.skipped = 0 AND TRIM(c.artist) != ''
        GROUP BY c.artist
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    fun topArtists(limit: Int = 30): Flow<List<ArtistPlayCount>>

    @Query("SELECT * FROM play_history ORDER BY startedAtMs DESC LIMIT :limit OFFSET :offset")
    fun history(limit: Int, offset: Int): Flow<List<PlayHistoryEntity>>

    @Query("SELECT COUNT(*) FROM play_history")
    fun historyCount(): Flow<Int>

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    @Query("DELETE FROM play_history WHERE trackId = :trackId")
    suspend fun forgetTrack(trackId: String)

    // ---- Shared (Together) plays ---------------------------------------------------------------

    @Insert
    suspend fun recordShared(entry: SharedPlayEntity): Long

    /** "Our Songs": what the two of you actually listened to together, most-played first. */
    @Query(
        """
        SELECT trackId, COUNT(*) AS sharedCount, MAX(playedAtMs) AS lastSharedAtMs
        FROM shared_plays
        GROUP BY trackId
        ORDER BY sharedCount DESC, lastSharedAtMs DESC
        LIMIT :limit
        """
    )
    fun ourSongs(limit: Int = 100): Flow<List<SharedTrackCount>>

    /**
     * Distinct days that had at least one shared play, newest first. The streak is computed in
     * Kotlin from this: SQLite date arithmetic across time zones is a trap, and the two partners
     * are by definition in different ones.
     */
    @Query("SELECT DISTINCT playedAtMs FROM shared_plays ORDER BY playedAtMs DESC LIMIT :limit")
    suspend fun sharedPlayTimestamps(limit: Int = 2000): List<Long>

    @Query("SELECT COUNT(*) FROM shared_plays")
    fun sharedPlayCount(): Flow<Int>
}
