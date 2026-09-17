package com.auralis.app.di

import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.network.NetworkMusicRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMusicRepository(
        networkMusicRepository: NetworkMusicRepository
    ): MusicRepository
}
