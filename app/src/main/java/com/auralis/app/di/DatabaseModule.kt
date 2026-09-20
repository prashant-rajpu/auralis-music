package com.auralis.app.di

import android.content.Context
import androidx.room.Room
import com.auralis.app.data.local.AuralisDatabase
import com.auralis.app.data.local.HistoryDao
import com.auralis.app.data.local.JamDao
import com.auralis.app.data.local.TogetherMessageDao
import com.auralis.app.data.local.LibraryDao
import com.auralis.app.data.local.MIGRATION_2_3
import com.auralis.app.data.local.MIGRATION_3_4
import com.auralis.app.data.local.MIGRATION_4_5
import com.auralis.app.data.local.PlaybackStateDao
import com.auralis.app.data.local.StreamCacheDao
import com.auralis.app.data.local.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAuralisDatabase(@ApplicationContext context: Context): AuralisDatabase {
        return Room.databaseBuilder(
            context,
            AuralisDatabase::class.java,
            "auralis_db"
        )
            // No destructive fallback: a schema bump must never wipe someone's downloads
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    @Provides
    @Singleton
    fun provideTrackDao(database: AuralisDatabase): TrackDao {
        return database.trackDao
    }

    @Provides
    @Singleton
    fun provideStreamCacheDao(database: AuralisDatabase): StreamCacheDao {
        return database.streamCacheDao
    }

    @Provides
    @Singleton
    fun provideLibraryDao(database: AuralisDatabase): LibraryDao = database.libraryDao

    @Provides
    @Singleton
    fun provideHistoryDao(database: AuralisDatabase): HistoryDao = database.historyDao

    @Provides
    @Singleton
    fun providePlaybackStateDao(database: AuralisDatabase): PlaybackStateDao = database.playbackStateDao

    @Provides
    @Singleton
    fun provideJamDao(database: AuralisDatabase): JamDao = database.jamDao

    @Provides
    @Singleton
    fun provideTogetherMessageDao(database: AuralisDatabase): TogetherMessageDao =
        database.togetherMessageDao
}
