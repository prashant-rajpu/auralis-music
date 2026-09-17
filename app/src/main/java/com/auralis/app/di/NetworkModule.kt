package com.auralis.app.di

import com.auralis.app.network.AudiusApi
import com.auralis.app.network.JioSaavnApi
import com.auralis.app.network.LrclibApi
import com.auralis.app.network.NetEaseLyricsApi
import com.auralis.app.network.OpenSourceMusicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDeezerApi(okHttpClient: OkHttpClient): OpenSourceMusicApi {
        return Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenSourceMusicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideJioSaavnApi(okHttpClient: OkHttpClient): JioSaavnApi {
        return Retrofit.Builder()
            .baseUrl("https://www.jiosaavn.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JioSaavnApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAudiusApi(okHttpClient: OkHttpClient): AudiusApi {
        return Retrofit.Builder()
            .baseUrl("https://discoveryprovider.audius.co/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AudiusApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLrclibApi(okHttpClient: OkHttpClient): LrclibApi {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrclibApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNetEaseLyricsApi(okHttpClient: OkHttpClient): NetEaseLyricsApi {
        return Retrofit.Builder()
            .baseUrl("https://music.163.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NetEaseLyricsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLyricsOvhApi(okHttpClient: OkHttpClient): com.auralis.app.network.LyricsOvhApi {
        return Retrofit.Builder()
            .baseUrl("https://api.lyrics.ovh/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.auralis.app.network.LyricsOvhApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSponsorBlockApi(okHttpClient: OkHttpClient): com.auralis.app.network.SponsorBlockApi {
        return Retrofit.Builder()
            .baseUrl("https://sponsor.ajay.app/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.auralis.app.network.SponsorBlockApi::class.java)
    }
}

