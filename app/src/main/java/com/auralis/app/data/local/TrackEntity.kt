package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.auralis.app.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val mediaUrl: String,
    val durationMs: Long
) {
    fun toDomainModel(): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            albumArtUrl = albumArtUrl,
            mediaUrl = mediaUrl,
            durationMs = durationMs
        )
    }
}
