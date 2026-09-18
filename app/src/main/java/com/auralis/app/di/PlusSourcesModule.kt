package com.auralis.app.di

import com.auralis.app.data.repository.YouTubeArtistRepository
import com.auralis.app.data.source.JioSaavnSource
import com.auralis.app.data.source.JioSaavnStreamResolver
import com.auralis.app.data.source.YouTubeMusicSource
import com.auralis.app.domain.repository.ArtistRepository
import com.auralis.app.domain.source.MusicSource
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.lyrics.LyricsSource
import com.auralis.app.lyrics.NetEaseLyricsSource
import com.auralis.app.network.JioSaavnApi
import com.auralis.app.network.NetEaseLyricsApi
import com.auralis.app.network.SponsorBlockApi
import com.auralis.app.network.SponsorBlockManager
import com.auralis.app.network.YouTubeStreamResolver
import com.auralis.app.playback.SegmentSkipper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/** YouTube Music, JioSaavn, NetEase and SponsorBlock: unofficial endpoints, sideload builds only. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlusSourcesModule {

    @Binds
    @IntoSet
    abstract fun youTubeMusicSource(source: YouTubeMusicSource): MusicSource

    @Binds
    @IntoSet
    abstract fun jioSaavnSource(source: JioSaavnSource): MusicSource

    @Binds
    @IntoSet
    abstract fun youTubeStreamResolver(resolver: YouTubeStreamResolver): StreamResolver

    @Binds
    @IntoSet
    abstract fun jioSaavnStreamResolver(resolver: JioSaavnStreamResolver): StreamResolver

    @Binds
    @IntoSet
    abstract fun netEaseLyrics(source: NetEaseLyricsSource): LyricsSource

    @Binds
    @IntoSet
    abstract fun sponsorBlockSkipper(skipper: SponsorBlockManager): SegmentSkipper

    @Binds
    abstract fun artistRepository(repository: YouTubeArtistRepository): ArtistRepository

    companion object {
        @Provides
        @Singleton
        fun provideJioSaavnApi(okHttpClient: OkHttpClient): JioSaavnApi =
            Retrofit.Builder()
                .baseUrl("https://www.jiosaavn.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(JioSaavnApi::class.java)

        @Provides
        @Singleton
        fun provideNetEaseLyricsApi(okHttpClient: OkHttpClient): NetEaseLyricsApi =
            Retrofit.Builder()
                .baseUrl("https://music.163.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NetEaseLyricsApi::class.java)

        @Provides
        @Singleton
        fun provideSponsorBlockApi(okHttpClient: OkHttpClient): SponsorBlockApi =
            Retrofit.Builder()
                .baseUrl("https://sponsor.ajay.app/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SponsorBlockApi::class.java)
    }
}
