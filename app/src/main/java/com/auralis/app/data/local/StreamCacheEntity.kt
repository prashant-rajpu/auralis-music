package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A resolved stream URL, kept so the app does not have to ask a provider again on every play.
 * Keyed by track and quality because the same track resolves to a different file per bitrate.
 */
@Entity(tableName = "stream_cache")
data class StreamCacheEntity(
    @PrimaryKey val cacheKey: String,
    val trackId: String,
    val quality: String,
    val url: String,
    val mimeType: String?,
    val expiresAtMs: Long,
    val resolvedAtMs: Long
) {
    companion object {
        fun keyOf(trackId: String, quality: String) = "$trackId::$quality"
    }
}
