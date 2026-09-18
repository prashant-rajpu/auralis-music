package com.auralis.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, StreamCacheEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AuralisDatabase : RoomDatabase() {
    abstract val trackDao: TrackDao
    abstract val streamCacheDao: StreamCacheDao
}
