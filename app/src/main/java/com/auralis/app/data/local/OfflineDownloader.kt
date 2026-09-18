package com.auralis.app.data.local

import android.content.Context
import android.util.Log
import com.auralis.app.domain.model.Track
import com.auralis.app.lyrics.LyricsRepository
import com.auralis.app.network.JamProtocolHelper
import com.auralis.app.network.YouTubeStreamResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val okHttpClient: OkHttpClient,
    private val lyricsRepository: LyricsRepository,
    private val streamResolver: YouTubeStreamResolver
) {
    suspend fun downloadTrack(track: Track): Boolean = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = if (JamProtocolHelper.needsStreamResolution(track)) {
                streamResolver.resolveStreamUrl(track)
            } else {
                track.mediaUrl
            }
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val musicDir = File(context.filesDir, "music")
                    if (!musicDir.exists()) {
                        musicDir.mkdirs()
                    }

                    val ext = if (downloadUrl.contains(".mp4") || downloadUrl.contains("audio/mp4")) "mp4" else if (downloadUrl.contains("webm") || downloadUrl.contains("opus")) "webm" else "mp3"
                    val safeFilename = "${track.id}_${track.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.$ext"
                    val file = File(musicDir, safeFilename)

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(file)

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Fetch and cache synced lyrics for offline playback
                    val lyrics = lyricsRepository.getLyrics(track)
                    val lyricsJson = if (lyrics.isNotEmpty()) TrackEntity.encodeLyricsJson(lyrics) else null

                    val localUri = file.absolutePath
                    trackDao.insertTrack(
                        TrackEntity(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            albumArtUrl = track.albumArtUrl,
                            mediaUrl = localUri,
                            durationMs = track.durationMs,
                            source = "Offline",
                            qualityBadge = track.qualityBadge,
                            syncedLyricsJson = lyricsJson
                        )
                    )
                    Log.d("OfflineDownloader", "Successfully downloaded track ${track.title} with lyrics to $localUri")
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("OfflineDownloader", "Failed to download track ${track.title}", e)
            false
        }
    }

    suspend fun deleteTrack(trackId: String): Unit = withContext(Dispatchers.IO) {
        try {
            val trackEntity = trackDao.getAllTracks().find { it.id == trackId }
            if (trackEntity != null) {
                val file = File(trackEntity.mediaUrl)
                if (file.exists()) {
                    file.delete()
                }
                trackDao.deleteTrack(trackId)
            }
            Unit
        } catch (e: Exception) {
            Log.e("OfflineDownloader", "Failed to delete track $trackId", e)
            Unit
        }
    }
}
