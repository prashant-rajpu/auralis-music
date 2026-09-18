package com.auralis.app.di

import com.auralis.app.data.repository.AggregatedMusicRepository
import com.auralis.app.data.source.AudiusSource
import com.auralis.app.data.source.DirectStreamResolver
import com.auralis.app.data.source.JamendoSource
import com.auralis.app.data.source.LocalFileResolver
import com.auralis.app.data.source.SearchFallbackResolver
import com.auralis.app.domain.repository.MusicRepository
import com.auralis.app.domain.source.MusicSource
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.lyrics.LrclibLyricsSource
import com.auralis.app.lyrics.LyricsOvhLyricsSource
import com.auralis.app.lyrics.LyricsSource
import com.auralis.app.playback.SegmentSkipper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/** Sources available in every flavor. Flavor source sets contribute more via their own modules. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourcesModule {

    @Multibinds
    abstract fun musicSources(): Set<@JvmSuppressWildcards MusicSource>

    @Multibinds
    abstract fun streamResolvers(): Set<@JvmSuppressWildcards StreamResolver>

    @Multibinds
    abstract fun lyricsSources(): Set<@JvmSuppressWildcards LyricsSource>

    @Multibinds
    abstract fun segmentSkippers(): Set<@JvmSuppressWildcards SegmentSkipper>

    @Binds
    @IntoSet
    abstract fun audiusSource(source: AudiusSource): MusicSource

    @Binds
    @IntoSet
    abstract fun jamendoSource(source: JamendoSource): MusicSource

    @Binds
    @IntoSet
    abstract fun localFileResolver(resolver: LocalFileResolver): StreamResolver

    @Binds
    @IntoSet
    abstract fun directStreamResolver(resolver: DirectStreamResolver): StreamResolver

    @Binds
    @IntoSet
    abstract fun searchFallbackResolver(resolver: SearchFallbackResolver): StreamResolver

    @Binds
    @IntoSet
    abstract fun lrclibLyrics(source: LrclibLyricsSource): LyricsSource

    @Binds
    @IntoSet
    abstract fun lyricsOvhLyrics(source: LyricsOvhLyricsSource): LyricsSource

    @Binds
    abstract fun musicRepository(repository: AggregatedMusicRepository): MusicRepository
}
