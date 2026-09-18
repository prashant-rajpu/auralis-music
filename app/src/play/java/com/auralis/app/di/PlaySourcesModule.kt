package com.auralis.app.di

import com.auralis.app.data.repository.SearchArtistRepository
import com.auralis.app.domain.repository.ArtistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Play-Store edition. It contributes no extra catalogs: the sources in SourcesModule (Audius,
 * Jamendo, local files) are the whole catalog, and artist pages are built from a search.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaySourcesModule {

    @Binds
    abstract fun artistRepository(repository: SearchArtistRepository): ArtistRepository
}
