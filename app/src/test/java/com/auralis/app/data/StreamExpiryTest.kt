package com.auralis.app.data

import com.auralis.app.data.source.StreamExpiry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamExpiryTest {

    private val now = 1_700_000_000_000L

    @Test
    fun googlevideoDeadlineIsHonouredWithASafetyMargin() {
        val expiresAtSec = (now / 1000) + 6 * 3600
        val url = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=$expiresAtSec&itag=251"

        val result = StreamExpiry.expiresAtMs(url, now)

        assertTrue("must not outlive the URL", result < expiresAtSec * 1000L)
        assertEquals(expiresAtSec * 1000L - 5 * 60 * 1000L, result)
    }

    @Test
    fun aDeadlineAlreadyPastFallsBackToTheDefaultSoTheCallerJustReResolves() {
        val stale = (now / 1000) - 3600
        val url = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=$stale"

        assertEquals(now + StreamExpiry.DEFAULT_TTL_MS, StreamExpiry.expiresAtMs(url, now))
    }

    @Test
    fun urlsWithoutADeadlineGetTheDefaultTtl() {
        assertEquals(
            now + StreamExpiry.DEFAULT_TTL_MS,
            StreamExpiry.expiresAtMs("https://aac.saavncdn.com/077/abc_320.mp4", now)
        )
    }

    @Test
    fun theExpireParameterIsReadWhereverItAppearsInTheQuery() {
        val sec = (now / 1000) + 7200
        val first = StreamExpiry.expiresAtMs("https://x.googlevideo.com/v?expire=$sec&a=1", now)
        val later = StreamExpiry.expiresAtMs("https://x.googlevideo.com/v?a=1&expire=$sec", now)
        assertEquals(first, later)
    }

    @Test
    fun aNumberThatIsNotAnExpiryIsIgnored() {
        // "expires" is a different parameter, and a short number is not a unix timestamp
        assertEquals(
            now + StreamExpiry.DEFAULT_TTL_MS,
            StreamExpiry.expiresAtMs("https://x.googlevideo.com/v?expire=123", now)
        )
    }

    @Test
    fun freshnessIsRelativeToNow() {
        assertTrue(StreamExpiry.isFresh(now + 1, now))
        assertFalse(StreamExpiry.isFresh(now, now))
        assertFalse(StreamExpiry.isFresh(now - 1, now))
    }
}
