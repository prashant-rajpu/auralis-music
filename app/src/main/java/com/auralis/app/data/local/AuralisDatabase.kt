package com.auralis.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class AuralisDatabase : RoomDatabase() {
    abstract val trackDao: TrackDao
}
