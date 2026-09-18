package com.auralis.app.di

import com.auralis.app.BuildConfig
import com.auralis.app.data.remote.jamendo.JamendoApi
import com.auralis.app.network.AudiusApi
import com.auralis.app.network.LrclibApi
import com.auralis.app.network.LyricsOvhApi
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

    private val userAgent = "Auralis/${BuildConfig.VERSION_NAME} (Android)"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS })
                }
            }
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent") != null) {
                    chain.proceed(request)
                } else {
                    chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun retrofit(okHttpClient: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideAudiusApi(okHttpClient: OkHttpClient): AudiusApi =
        retrofit(okHttpClient, "https://discoveryprovider.audius.co/").create(AudiusApi::class.java)

    @Provides
    @Singleton
    fun provideJamendoApi(okHttpClient: OkHttpClient): JamendoApi =
        retrofit(okHttpClient, "https://api.jamendo.com/").create(JamendoApi::class.java)

    @Provides
    @Singleton
    fun provideLrclibApi(okHttpClient: OkHttpClient): LrclibApi =
        retrofit(okHttpClient, "https://lrclib.net/").create(LrclibApi::class.java)

    @Provides
    @Singleton
    fun provideLyricsOvhApi(okHttpClient: OkHttpClient): LyricsOvhApi =
        retrofit(okHttpClient, "https://api.lyrics.ovh/").create(LyricsOvhApi::class.java)
}
