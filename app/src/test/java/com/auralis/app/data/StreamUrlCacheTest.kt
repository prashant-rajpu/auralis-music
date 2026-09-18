package com.auralis.app.data

import com.auralis.app.data.local.StreamCacheDao
import com.auralis.app.data.local.StreamCacheEntity
import com.auralis.app.data.source.StreamUrlCache
import com.auralis.app.domain.model.AudioQualitySetting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamUrlCacheTest {

    private class FakeDao(var failing: Boolean = false) : StreamCacheDao {
        val rows = mutableMapOf<String, StreamCacheEntity>()

        override suspend fun get(cacheKey: String): StreamCacheEntity? {
            if (failing) throw IllegalStateException("db unavailable")
            return rows[cacheKey]
        }

        override suspend fun put(entry: StreamCacheEntity) {
            if (failing) throw IllegalStateException("db unavailable")
            rows[entry.cacheKey] = entry
        }

        override suspend fun deleteForTrack(trackId: String) {
            if (failing) throw IllegalStateException("db unavailable")
            rows.values.removeAll { it.trackId == trackId }
        }

        override suspend fun deleteExpired(nowMs: Long) {
            rows.values.removeAll { it.expiresAtMs <= nowMs }
        }

        override suspend fun count(): Int = rows.size
    }

    private val now = 1_700_000_000_000L
    private val saavn = "https://aac.saavncdn.com/077/abc_320.mp4"

    @Test
    fun aStoredUrlComesBack() = runTest {
        val cache = StreamUrlCache(FakeDao())
        cache.put("yt_a", AudioQualitySetting.HIGH, saavn, nowMs = now)

        assertEquals(saavn, cache.get("yt_a", AudioQualitySetting.HIGH, now))
    }

    @Test
    fun eachQualityIsCachedSeparately() = runTest {
        val cache = StreamUrlCache(FakeDao())
        cache.put("yt_a", AudioQualitySetting.HIGH, "high.mp4", nowMs = now)
        cache.put("yt_a", AudioQualitySetting.DATA_SAVER, "low.mp4", nowMs = now)

        assertEquals("high.mp4", cache.get("yt_a", AudioQualitySetting.HIGH, now))
        assertEquals("low.mp4", cache.get("yt_a", AudioQualitySetting.DATA_SAVER, now))
        assertNull(cache.get("yt_a", AudioQualitySetting.STANDARD, now))
    }

    @Test
    fun anExpiredEntryIsNotServedAndIsDroppedSoItCannotLinger() = runTest {
        val dao = FakeDao()
        val cache = StreamUrlCache(dao)
        val expired = (now / 1000) + 60 // inside the 5-minute safety margin
        cache.put("yt_a", AudioQualitySetting.HIGH, "https://x.googlevideo.com/v?expire=$expired", nowMs = now)

        assertNull(cache.get("yt_a", AudioQualitySetting.HIGH, now))
        assertEquals(0, dao.count())
    }

    @Test
    fun invalidateRemovesEveryQualityForATrack() = runTest {
        val dao = FakeDao()
        val cache = StreamUrlCache(dao)
        cache.put("yt_a", AudioQualitySetting.HIGH, "high.mp4", nowMs = now)
        cache.put("yt_a", AudioQualitySetting.STANDARD, "std.mp4", nowMs = now)
        cache.put("yt_b", AudioQualitySetting.HIGH, "other.mp4", nowMs = now)

        cache.invalidate("yt_a")

        assertNull(cache.get("yt_a", AudioQualitySetting.HIGH, now))
        assertNull(cache.get("yt_a", AudioQualitySetting.STANDARD, now))
        assertEquals("other.mp4", cache.get("yt_b", AudioQualitySetting.HIGH, now))
    }

    @Test
    fun purgeExpiredKeepsWhatIsStillFresh() = runTest {
        val dao = FakeDao()
        val cache = StreamUrlCache(dao)
        cache.put("fresh", AudioQualitySetting.HIGH, saavn, nowMs = now)
        cache.put("stale", AudioQualitySetting.HIGH, saavn, nowMs = now - 13 * 60 * 60 * 1000L)

        cache.purgeExpired(now)

        assertEquals(saavn, cache.get("fresh", AudioQualitySetting.HIGH, now))
        assertEquals(1, dao.count())
    }

    @Test
    fun aDatabaseFailureIsTreatedAsAMissRatherThanCrashingPlayback() = runTest {
        val cache = StreamUrlCache(FakeDao(failing = true))

        // Must not throw: the caller simply resolves again
        cache.put("yt_a", AudioQualitySetting.HIGH, saavn, nowMs = now)
        assertNull(cache.get("yt_a", AudioQualitySetting.HIGH, now))
        cache.invalidate("yt_a")
    }
}
