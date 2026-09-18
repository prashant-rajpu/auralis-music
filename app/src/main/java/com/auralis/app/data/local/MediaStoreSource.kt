package com.auralis.app.data.local

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.MusicSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Music already on the device, read through MediaStore. */
@Singleton
class MediaStoreSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicSource {

    override val provider = Provider.LOCAL
    override val priority = -10

    /** Very short files are almost always notification sounds rather than music. */
    private val minDurationMs = 30_000L

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun search(query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        val escaped = query.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return query(
            selection = "${MediaStore.Audio.Media.TITLE} LIKE ? ESCAPE '!' OR " +
                "${MediaStore.Audio.Media.ARTIST} LIKE ? ESCAPE '!'",
            selectionArgs = arrayOf("%$escaped%", "%$escaped%")
        )
    }

    override suspend fun library(): List<Track> = query()

    private fun query(selection: String? = null, selectionArgs: Array<String>? = null): List<Track> {
        if (!hasPermission()) return emptyList()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val isMusic = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val where = if (selection == null) isMusic else "$isMusic AND ($selection)"
        val args = arrayOf(minDurationMs.toString()) + (selectionArgs ?: emptyArray())

        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                where,
                args,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        add(
                            Track(
                                id = "local_$id",
                                title = cursor.getString(titleColumn) ?: "Unknown",
                                artist = cursor.getString(artistColumn)
                                    ?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                                    ?: "Unknown artist",
                                albumArtUrl = albumArtUri(cursor.getLong(albumIdColumn)),
                                mediaUrl = ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                                ).toString(),
                                durationMs = cursor.getLong(durationColumn),
                                source = cursor.getString(albumColumn) ?: provider.displayName,
                                qualityBadge = "On device",
                                isDownloaded = true
                            )
                        )
                    }
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.w("MediaStoreSource", "MediaStore query failed", e)
            emptyList()
        }
    }

    private fun albumArtUri(albumId: Long): String? =
        if (albumId <= 0) null else "content://media/external/audio/albumart/$albumId"
}
