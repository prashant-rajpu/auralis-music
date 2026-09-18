package com.auralis.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun queue(): List<QueueItemEntity>

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(items: List<QueueItemEntity>)

    @Query("SELECT * FROM player_state WHERE id = :id LIMIT 1")
    suspend fun playerState(id: Int = PlayerStateEntity.SINGLETON_ID): PlayerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlayerState(state: PlayerStateEntity)

    /**
     * Queue and position are saved together or not at all: a queue written without its matching
     * index restores to the wrong track, which is worse than restoring to nothing.
     */
    @Transaction
    suspend fun saveSnapshot(items: List<QueueItemEntity>, state: PlayerStateEntity) {
        clearQueue()
        insertQueue(items)
        savePlayerState(state)
    }

    @Transaction
    suspend fun clearSnapshot() {
        clearQueue()
        clearPlayerState()
    }

    @Query("DELETE FROM player_state")
    suspend fun clearPlayerState()
}
