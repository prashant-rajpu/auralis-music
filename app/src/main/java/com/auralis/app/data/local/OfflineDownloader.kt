package com.auralis.app.data.local

import android.content.Context
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
    suspend fun downloadTrack(track: Track) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(track.mediaUrl).build()
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        // Ensure directory exists
                        val musicDir = File(context.filesDir, "music")
                        if (!musicDir.exists()) {
                            musicDir.mkdirs()
                        }

                        // Sanitize filename
                        val filename = "${track.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.mp3"
                        val file = File(musicDir, filename)

                        val inputStream = body.byteStream()
                        val outputStream = FileOutputStream(file)

                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Save to database with local URI
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
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
