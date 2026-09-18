package com.auralis.app.data.source

import android.util.Log
import com.auralis.app.data.local.StreamCacheDao
import com.auralis.app.data.local.StreamCacheEntity
import com.auralis.app.domain.model.AudioQualitySetting
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers resolved stream URLs across process restarts, so reopening the app does not re-run a
 * provider lookup for every track. A cache miss is always safe: the caller just resolves again.
 */
@Singleton
class StreamUrlCache @Inject constructor(
    private val dao: StreamCacheDao
) {
    suspend fun get(trackId: String, quality: AudioQualitySetting, nowMs: Long = System.currentTimeMillis()): String? {
        return try {
            val entry = dao.get(StreamCacheEntity.keyOf(trackId, quality.name)) ?: return null
            if (StreamExpiry.isFresh(entry.expiresAtMs, nowMs)) {
                entry.url
            } else {
                dao.deleteForTrack(trackId)
                null
            }
        } catch (e: Exception) {
            Log.w("StreamUrlCache", "Cache read failed for $trackId", e)
            null
        }
    }

    suspend fun put(
        trackId: String,
        quality: AudioQualitySetting,
        url: String,
        mimeType: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        try {
            dao.put(
                StreamCacheEntity(
                    cacheKey = StreamCacheEntity.keyOf(trackId, quality.name),
                    trackId = trackId,
                    quality = quality.name,
                    url = url,
                    mimeType = mimeType,
                    expiresAtMs = StreamExpiry.expiresAtMs(url, nowMs),
                    resolvedAtMs = nowMs
                )
            )
        } catch (e: Exception) {
            Log.w("StreamUrlCache", "Cache write failed for $trackId", e)
        }
    }

    suspend fun invalidate(trackId: String) {
        try {
            dao.deleteForTrack(trackId)
        } catch (e: Exception) {
            Log.w("StreamUrlCache", "Cache invalidate failed for $trackId", e)
        }
    }

    suspend fun purgeExpired(nowMs: Long = System.currentTimeMillis()) {
        try {
            dao.deleteExpired(nowMs)
        } catch (e: Exception) {
            Log.w("StreamUrlCache", "Cache purge failed", e)
        }
    }
}
