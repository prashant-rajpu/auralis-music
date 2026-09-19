package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClockSyncTest {

    /**
     * The whole exercise in one line: this phone's clock reads `localAtSend`, the server's reads
     * `serverAtMidpoint`, and the round trip is symmetric, so the offset is exactly the difference.
     */
    @Test
    fun `a symmetric exchange measures the offset exactly`() {
        val sync = ClockSync()
        // Local 1000 out, back at 1020 — midpoint 1010. Server said 5010 at that moment.
        val sample = sync.observe(sentAtMs = 1000, serverMs = 5010, receivedAtMs = 1020)

        assertNotNull(sample)
        assertEquals(20L, sample!!.roundTripMs)
        assertEquals(4000L, sample.offsetMs)
        assertEquals(4000L, sync.offsetMs)
        assertEquals(9000L, sync.serverNow(5000))
        assertEquals(5000L, sync.localTimeOf(9000))
    }

    @Test
    fun `an odd round trip does not lose a millisecond to integer division`() {
        val sync = ClockSync()
        // Midpoint of 1000 and 1003 is 1001.5; rounding it down would report 4000 instead of 3999.
        val sample = sync.observe(sentAtMs = 1000, serverMs = 5001, receivedAtMs = 1003)
        assertEquals(4000L, sample!!.offsetMs)
    }

    @Test
    fun `a clock that went backwards mid-exchange is discarded`() {
        val sync = ClockSync()
        assertNull(sync.observe(sentAtMs = 1000, serverMs = 5000, receivedAtMs = 900))
        assertEquals(0, sync.sampleCount)
    }

    @Test
    fun `it is not synced until several samples agree`() {
        val sync = ClockSync()
        assertFalse(sync.isSynced)
        sync.observe(1000, 5010, 1020)
        assertFalse(sync.isSynced)
        sync.observe(2000, 6010, 2020)
        assertFalse(sync.isSynced)
        sync.observe(3000, 7010, 3020)
        assertTrue(sync.isSynced)
    }

    /**
     * The reason for the min-RTT gate. A congested exchange is not noise around the true offset —
     * the delay lands on one leg, so the midpoint is biased by half of it. Averaging it in would
     * drag the offset by hundreds of milliseconds, which is more than the whole drift budget.
     */
    @Test
    fun `a congested sample is discarded rather than smoothed in`() {
        val sync = ClockSync()
        sync.observe(sentAtMs = 1000, serverMs = 5010, receivedAtMs = 1020)
        assertEquals(4000L, sync.offsetMs)

        // 600 ms round trip, all of it on the return leg: the midpoint is 300 ms off the truth.
        val rejected = sync.observe(sentAtMs = 2000, serverMs = 5700, receivedAtMs = 2600)

        assertNull(rejected)
        assertEquals(4000L, sync.offsetMs)
        assertEquals(1, sync.sampleCount)
    }

    @Test
    fun `a slightly slower sample is still within tolerance`() {
        val sync = ClockSync()
        sync.observe(sentAtMs = 1000, serverMs = 5010, receivedAtMs = 1020)
        // 40 ms against a 20 ms best: inside 20 * 1.5 + 20.
        assertNotNull(sync.observe(sentAtMs = 2000, serverMs = 6020, receivedAtMs = 2040))
        assertEquals(2, sync.sampleCount)
    }

    @Test
    fun `a fast best does not reject every ordinary sample after it`() {
        val sync = ClockSync(rttSlackMs = 20)
        sync.observe(sentAtMs = 1000, serverMs = 5002, receivedAtMs = 1004)
        // 4 ms best. Without the absolute slack a 26 ms round trip would be thrown away forever.
        assertNotNull(sync.observe(sentAtMs = 2000, serverMs = 6013, receivedAtMs = 2026))
    }

    @Test
    fun `a path that got permanently worse is relearned rather than blocking every sample`() {
        val sync = ClockSync(windowSize = 4)
        sync.observe(sentAtMs = 0, serverMs = 4010, receivedAtMs = 20)

        // Wi-Fi to cellular: every round trip is 300 ms now, and none of them is close to the old best.
        var accepted = 0
        for (i in 1..12) {
            val sent = i * 1000L
            val sample = sync.observe(sentAtMs = sent, serverMs = sent + 4150, receivedAtMs = sent + 300)
            if (sample != null) accepted++
        }

        assertTrue("the new normal should be learned once the old best ages out", accepted > 0)
    }

    @Test
    fun `the offset converges on the truth as samples arrive`() {
        val sync = ClockSync()
        val truth = 7_500L
        // Jitter that lands symmetrically either side: exactly what the smoothing is for.
        val jitter = listOf(0L, 6L, -4L, 8L, -6L, 2L, -2L, 4L, -8L, 0L)
        for ((i, skew) in jitter.withIndex()) {
            val sent = 1000L + i * 1000
            val received = sent + 30
            val midpoint = sent + 15
            sync.observe(sentAtMs = sent, serverMs = midpoint + truth + skew, receivedAtMs = received)
        }
        assertTrue(
            "offset was ${sync.offsetMs}, expected within 5 ms of $truth",
            abs(sync.offsetMs - truth) <= 5,
        )
    }

    @Test
    fun `the best round trip is the floor, not the average`() {
        val sync = ClockSync()
        sync.observe(sentAtMs = 0, serverMs = 4050, receivedAtMs = 100)
        sync.observe(sentAtMs = 1000, serverMs = 5030, receivedAtMs = 1060)
        assertEquals(60L, sync.bestRoundTripMs)
    }

    @Test
    fun `a reconnect starts over instead of drifting toward a path that is gone`() {
        val sync = ClockSync()
        sync.observe(1000, 5010, 1020)
        sync.observe(2000, 6010, 2020)
        sync.observe(3000, 7010, 3020)
        assertTrue(sync.isSynced)

        sync.reset()

        assertFalse(sync.isSynced)
        assertEquals(0L, sync.offsetMs)
        assertEquals(0, sync.sampleCount)
    }

    @Test
    fun `an offset worth telling the user about is more than a second`() {
        assertFalse(ClockSync.isLargeOffset(900))
        assertTrue(ClockSync.isLargeOffset(-4_000))
    }
}
