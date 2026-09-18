package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The saved queue, one row per position. Written on every media-item transition, pause and
 * destroy, so a force-stop resumes on the same track at the same second instead of an empty app.
 *
 * Only the track id is stored; the track itself comes from `catalog_tracks`.
 */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val trackId: String,
    /** True for tracks Infinite Radio appended, so restoring keeps the user/radio partition. */
    val isRecommendation: Boolean
)

/**
 * Single-row table holding where playback was. [SINGLETON_ID] keeps it to one row without needing
 * a "delete then insert" dance on every save.
 */
@Entity(tableName = "player_state")
data class PlayerStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val currentIndex: Int,
    val positionMs: Long,
    val isShuffled: Boolean,
    /** RepeatMode.name; stored as text so reordering the enum cannot silently change meaning. */
    val repeatMode: String,
    val updatedAtMs: Long
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
