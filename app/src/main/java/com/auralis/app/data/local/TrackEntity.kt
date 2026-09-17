package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.auralis.app.domain.model.LyricLine
import com.auralis.app.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val mediaUrl: String,
    val durationMs: Long,
    val source: String = "Offline",
    val qualityBadge: String = "Offline HQ",
    val syncedLyricsJson: String? = null
) {
    fun toDomainModel(): Track {
        val parsedLyrics = syncedLyricsJson?.let { parseLyricsJson(it) }
        return Track(
            id = id,
            title = title,
            artist = artist,
            albumArtUrl = albumArtUrl,
            mediaUrl = mediaUrl,
            durationMs = durationMs,
            source = source,
            qualityBadge = qualityBadge,
            isDownloaded = true,
            lyrics = parsedLyrics
        )
    }

    companion object {
        fun fromDomainModel(track: Track, localPath: String): TrackEntity {
            return TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                albumArtUrl = track.albumArtUrl,
                mediaUrl = localPath,
                durationMs = track.durationMs,
                source = "Offline",
                qualityBadge = track.qualityBadge,
                syncedLyricsJson = track.lyrics?.let { encodeLyricsJson(it) }
            )
        }

        fun parseLyricsJson(jsonStr: String): List<LyricLine> {
            val list = mutableListOf<LyricLine>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(LyricLine(obj.getLong("time"), obj.getString("text")))
                }
            } catch (e: Exception) {
                // Ignore parsing errors on corrupt string
            }
            return list
        }

        fun encodeLyricsJson(lyrics: List<LyricLine>): String {
            val array = JSONArray()
            lyrics.forEach { line ->
                val obj = JSONObject()
                obj.put("time", line.timestampMs)
                obj.put("text", line.text)
                array.put(obj)
            }
            return array.toString()
        }
    }
}
