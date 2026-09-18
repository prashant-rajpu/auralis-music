package com.auralis.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StreamCacheDao {

    @Query("SELECT * FROM stream_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun get(cacheKey: String): StreamCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: StreamCacheEntity)

    @Query("DELETE FROM stream_cache WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: String)

    @Query("DELETE FROM stream_cache WHERE expiresAtMs <= :nowMs")
    suspend fun deleteExpired(nowMs: Long)

    @Query("SELECT COUNT(*) FROM stream_cache")
    suspend fun count(): Int
}
