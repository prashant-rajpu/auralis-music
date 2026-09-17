package com.auralis.app.di

import android.content.Context
import androidx.room.Room
import com.auralis.app.data.local.AuralisDatabase
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
        ).build()
    }

    @Provides
    @Singleton
    fun provideTrackDao(database: AuralisDatabase): TrackDao {
        return database.trackDao
    }
}
