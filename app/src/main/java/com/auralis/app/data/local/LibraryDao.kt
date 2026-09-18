package com.auralis.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // ---- Catalog ------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogTracks(tracks: List<CatalogTrackEntity>)

    @Query("SELECT * FROM catalog_tracks WHERE id = :trackId LIMIT 1")
    suspend fun catalogTrack(trackId: String): CatalogTrackEntity?

    @Query("SELECT * FROM catalog_tracks WHERE id IN (:trackIds)")
    suspend fun catalogTracks(trackIds: List<String>): List<CatalogTrackEntity>

    // ---- Likes --------------------------------------------------------------------------------

    @Query("SELECT trackId FROM liked_tracks")
    fun likedTrackIds(): Flow<List<String>>

    @Query(
        """
        SELECT c.* FROM catalog_tracks c
        INNER JOIN liked_tracks l ON l.trackId = c.id
        ORDER BY l.likedAtMs DESC
        """
    )
    fun likedTracks(): Flow<List<CatalogTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(entity: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun unlike(trackId: String)

    @Query("SELECT trackId FROM disliked_tracks")
    fun dislikedTrackIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dislike(entity: DislikedTrackEntity)

    @Query("DELETE FROM disliked_tracks WHERE trackId = :trackId")
    suspend fun undislike(trackId: String)

    // ---- Playlists ----------------------------------------------------------------------------

    @Query("SELECT * FROM playlists ORDER BY sortIndex ASC, createdAtMs ASC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun playlist(playlistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("UPDATE playlists SET name = :name, updatedAtMs = :nowMs WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, nowMs: Long)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM playlists")
    suspend fun nextPlaylistSortIndex(): Int

    @Query(
        """
        SELECT c.* FROM catalog_tracks c
        INNER JOIN playlist_tracks pt ON pt.trackId = c.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    fun playlistTracks(playlistId: String): Flow<List<CatalogTrackEntity>>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun playlistTrackCount(playlistId: String): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPositionIn(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeFromPlaylist(playlistId: String, trackId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun playlistEntries(playlistId: String): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceEntries(entries: List<PlaylistTrackEntity>)

    /**
     * Rewrites every position in one transaction. Reordering by shifting neighbours leaves gaps and
     * duplicate positions after a few drags; renumbering from the caller's list cannot.
     */
    @Transaction
    suspend fun reorderPlaylist(playlistId: String, orderedTrackIds: List<String>, nowMs: Long) {
        val existing = playlistEntries(playlistId).associateBy { it.trackId }
        replaceEntries(
            orderedTrackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = index,
                    addedAtMs = existing[trackId]?.addedAtMs ?: nowMs
                )
            }
        )
    }
}
