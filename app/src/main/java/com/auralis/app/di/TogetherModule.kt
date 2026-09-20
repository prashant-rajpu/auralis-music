package com.auralis.app.di

import com.auralis.app.together.PlaybackManagerTogetherPlayer
import com.auralis.app.together.RelayUrlProvider
import com.auralis.app.together.RelayWebSocketTransport
import com.auralis.app.data.repository.CoupleRepository
import com.auralis.app.data.repository.TogetherMailboxRepository
import com.auralis.app.call.CallMediaEngine
import com.auralis.app.call.WebRtcEngine
import com.auralis.app.together.TogetherMailbox
import com.auralis.app.together.TogetherPlayer
import com.auralis.app.together.TogetherRecorder
import com.auralis.app.together.TogetherPreferences
import com.auralis.app.together.TogetherScope
import com.auralis.app.together.TogetherStore
import com.auralis.app.together.TogetherTransport
import com.auralis.app.together.WallClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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

    @Binds
    @Singleton
    abstract fun bindTogetherPlayer(player: PlaybackManagerTogetherPlayer): TogetherPlayer

    @Binds
    @Singleton
    abstract fun bindTogetherStore(preferences: TogetherPreferences): TogetherStore

    @Binds
    @Singleton
    abstract fun bindTogetherRecorder(repository: CoupleRepository): TogetherRecorder

    @Binds
    @Singleton
    abstract fun bindTogetherMailbox(repository: TogetherMailboxRepository): TogetherMailbox

    @Binds
    @Singleton
    abstract fun bindCallMediaEngine(engine: WebRtcEngine): CallMediaEngine

    companion object {
        /** Injected rather than called directly so a session can be driven by a test clock. */
        @Provides
        @Singleton
        fun provideWallClock(): WallClock = WallClock { System.currentTimeMillis() }

        @OptIn(ExperimentalCoroutinesApi::class)
        @Provides
        @Singleton
        @TogetherScope
        fun provideTogetherScope(): CoroutineScope =
            CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    }
}
