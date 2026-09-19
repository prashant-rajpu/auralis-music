package com.auralis.app.di

import com.auralis.app.together.RelayUrlProvider
import com.auralis.app.together.RelayWebSocketTransport
import com.auralis.app.together.TogetherPreferences
import com.auralis.app.together.TogetherTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the relay transport as *the* Together transport.
 *
 * The interface is the point: the ntfy client it replaces is still in the tree, and swapping the
 * binding is how a fallback gets tested rather than argued about.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TogetherModule {

    @Binds
    @Singleton
    abstract fun bindRelayUrlProvider(preferences: TogetherPreferences): RelayUrlProvider

    @Binds
    @Singleton
    abstract fun bindTogetherTransport(transport: RelayWebSocketTransport): TogetherTransport
}
