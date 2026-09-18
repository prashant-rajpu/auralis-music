package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.auralis.app.domain.model.Track

/**
 * Every track the app has ever seen, from any source.
 *
 * A playlist row or a history row only stores a track id; without this table, reopening a playlist
 * would mean re-querying every catalog for tracks the user already picked. Deliberately *not* a
 * foreign key target: a missing catalog row should degrade to "title unknown", never crash an
 * insert or cascade a delete through someone's playlist.
 */
@Entity(tableName = "catalog_tracks")
data class CatalogTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val mediaUrl: String,
    val durationMs: Long,
    val source: String,
    val qualityBadge: String,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long
) {
    fun toTrack(isDownloaded: Boolean = false): Track = Track(
        id = id,
        title = title,
        artist = artist,
        albumArtUrl = albumArtUrl,
        mediaUrl = mediaUrl,
        durationMs = durationMs,
        source = source,
        qualityBadge = qualityBadge,
        isDownloaded = isDownloaded
    )

    companion object {
        fun from(track: Track, nowMs: Long): CatalogTrackEntity = CatalogTrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            albumArtUrl = track.albumArtUrl,
            mediaUrl = track.mediaUrl,
            durationMs = track.durationMs,
            source = track.source,
            qualityBadge = track.qualityBadge,
            firstSeenAtMs = nowMs,
            lastSeenAtMs = nowMs
        )
    }
}

/** Replaces the in-memory set in PlayerViewModel, which lost every like on process death. */
@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAtMs: Long
)

@Entity(tableName = "disliked_tracks")
data class DislikedTrackEntity(
    @PrimaryKey val trackId: String,
    val dislikedAtMs: Long
)

@Entity(
    tableName = "playlists",
    indices = [Index("sortIndex")]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Artwork shown for the playlist; null falls back to the first track's cover. */
    val coverTrackId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val sortIndex: Int
)

/**
 * Position is explicit rather than implied by insertion order, because reordering is a first-class
 * action. Deleting a playlist cascades here so a rename-delete cycle cannot leave orphan rows.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAtMs: Long
)
