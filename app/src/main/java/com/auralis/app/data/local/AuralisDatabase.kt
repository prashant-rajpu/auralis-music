package com.auralis.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        StreamCacheEntity::class,
        CatalogTrackEntity::class,
        LikedTrackEntity::class,
        DislikedTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayHistoryEntity::class,
        SharedPlayEntity::class,
        QueueItemEntity::class,
        PlayerStateEntity::class,
        JamSessionEntity::class,
        JamEventEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AuralisDatabase : RoomDatabase() {
    abstract val trackDao: TrackDao
    abstract val streamCacheDao: StreamCacheDao
    abstract val libraryDao: LibraryDao
    abstract val historyDao: HistoryDao
    abstract val playbackStateDao: PlaybackStateDao
    abstract val jamDao: JamDao
}
