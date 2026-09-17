package com.auralis.app.data.local

import android.content.Context
import android.util.Log
import com.auralis.app.domain.model.Track
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
    private val okHttpClient: OkHttpClient
) {
    suspend fun downloadTrack(track: Track): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(track.mediaUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val musicDir = File(context.filesDir, "music")
                    if (!musicDir.exists()) {
                        musicDir.mkdirs()
                    }

                    val ext = if (track.mediaUrl.contains(".mp4")) "mp4" else "mp3"
                    val safeFilename = "${track.id}_${track.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.$ext"
                    val file = File(musicDir, safeFilename)

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(file)

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    val localUri = file.absolutePath
                    trackDao.insertTrack(
                        TrackEntity(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            albumArtUrl = track.albumArtUrl,
                            mediaUrl = localUri,
                            durationMs = track.durationMs
                        )
                    )
                    Log.d("OfflineDownloader", "Successfully downloaded track ${track.title} to $localUri")
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("OfflineDownloader", "Failed to download track ${track.title}", e)
            false
        }
    }

    suspend fun deleteTrack(trackId: String) {
        withContext(Dispatchers.IO) {
            try {
                val trackEntity = trackDao.getAllTracks().find { it.id == trackId }
                if (trackEntity != null) {
                    val file = File(trackEntity.mediaUrl)
                    if (file.exists()) {
                        file.delete()
                    }
                    trackDao.deleteTrack(trackId)
                }
            } catch (e: Exception) {
                Log.e("OfflineDownloader", "Failed to delete track $trackId", e)
            }
        }
    }
}
