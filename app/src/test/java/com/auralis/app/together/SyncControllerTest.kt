package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncControllerTest {

    private val key = "audius:abc123"

    /**
     * The peer last reported 30 s into the track at server time 10 000. It is now 12 000, so they
     * are 32 s in. Every case below moves only where *we* are, so the drift is the interesting part.
     */
    private val now = 12_000L

    private fun remote(
        isPlaying: Boolean = true,
        speed: Float = 1f,
        atServerMs: Long = 10_000,
        positionMs: Long = 30_000,
        durationMs: Long = 0,
        peerBuffering: Boolean = false,
        trackKey: String = key,
    ) = RemotePlayback(
        trackKey = trackKey,
        positionMs = positionMs,
        isPlaying = isPlaying,
        speed = speed,
        atServerMs = atServerMs,
        durationMs = durationMs,
        peerBuffering = peerBuffering,
    )

    private fun local(
        positionMs: Long,
        isPlaying: Boolean = true,
        isBuffering: Boolean = false,
        trackKey: String = key,
    ) = LocalPlayback(trackKey, positionMs, isPlaying, isBuffering)

    // --- projection ---

    @Test
    fun `their position is projected forward from when they told us`() {
        val controller = SyncController()
        assertEquals(32_000L, controller.expectedPositionMs(remote(), now))
    }

    @Test
    fun `projection respects the speed they are playing at`() {
        val controller = SyncController()
        assertEquals(33_000L, controller.expectedPositionMs(remote(speed = 1.5f), now))
    }

    @Test
    fun `a paused peer does not advance`() {
        val controller = SyncController()
        assertEquals(30_000L, controller.expectedPositionMs(remote(isPlaying = false), now))
    }

    @Test
    fun `projection cannot run past the end of the track`() {
        val controller = SyncController()
        val late = remote(atServerMs = 0, positionMs = 30_000, durationMs = 31_000)
        assertEquals(31_000L, controller.expectedPositionMs(late, now))
    }

    // --- the drift table ---

    @Test
    fun `dead on is left alone`() {
        val decision = SyncController().decide(local(32_000), remote(), now)
        assertEquals(SyncState.IN_SYNC, decision.state)
        assertNull(decision.seekToMs)
        assertEquals(1f, decision.speed, 0f)
        assertTrue(decision.shouldPlay)
        assertEquals(0L, decision.driftMs)
    }

    @Test
    fun `under a tenth of a second is close enough to ignore`() {
        val decision = SyncController().decide(local(32_050), remote(), now)
        assertEquals(SyncState.IN_SYNC, decision.state)
        assertEquals(1f, decision.speed, 0f)
    }

    @Test
    fun `ahead of them by a fifth of a second means slow down, not jump`() {
        val decision = SyncController().decide(local(32_200), remote(), now)
        assertEquals(SyncState.CATCHING_UP, decision.state)
        assertEquals(0.98f, decision.speed, 0f)
        assertNull(decision.seekToMs)
        assertEquals(200L, decision.driftMs)
    }

    @Test
    fun `behind them by a fifth of a second means speed up`() {
        val decision = SyncController().decide(local(31_800), remote(), now)
        assertEquals(SyncState.CATCHING_UP, decision.state)
        assertEquals(1.02f, decision.speed, 0f)
        assertEquals(-200L, decision.driftMs)
    }

    @Test
    fun `half a second apart is already obviously wrong, so it seeks`() {
        val decision = SyncController().decide(local(32_500), remote(), now)
        assertEquals(SyncState.CORRECTED, decision.state)
        assertEquals(32_000L, decision.seekToMs)
        assertEquals(1f, decision.speed, 0f)
        assertTrue(decision.shouldPlay)
    }

    @Test
    fun `the boundaries land on the side the table says`() {
        val controller = SyncController()
        // 120 nudges, 119 does not; 400 nudges, 401 seeks.
        assertEquals(SyncState.IN_SYNC, controller.decide(local(32_119), remote(), now).state)
        controller.reset()
        assertEquals(SyncState.CATCHING_UP, controller.decide(local(32_120), remote(), now).state)
        controller.reset()
        assertEquals(SyncState.CATCHING_UP, controller.decide(local(32_400), remote(), now).state)
        controller.reset()
        assertEquals(SyncState.CORRECTED, controller.decide(local(32_401), remote(), now).state)
    }

    // --- the nudge, over time ---

    @Test
    fun `a nudge keeps running through the gap it is closing`() {
        val controller = SyncController()
        assertEquals(SyncState.CATCHING_UP, controller.decide(local(32_200), remote(), now).state)

        // Down to 90 ms: below the entry threshold, but releasing here would just re-trigger.
        val closing = controller.decide(local(32_090), remote(atServerMs = now, positionMs = 32_000), now)
        assertEquals(SyncState.CATCHING_UP, closing.state)
        assertEquals(0.98f, closing.speed, 0f)
        assertTrue(controller.isNudging)
    }

    @Test
    fun `a nudge stops once the gap is genuinely closed`() {
        val controller = SyncController()
        controller.decide(local(32_200), remote(), now)
        val done = controller.decide(local(32_050), remote(atServerMs = now, positionMs = 32_000), now)
        assertEquals(SyncState.IN_SYNC, done.state)
        assertEquals(1f, done.speed, 0f)
        assertFalse(controller.isNudging)
    }

    @Test
    fun `a nudge that is not working gives up and seeks`() {
        val controller = SyncController()
        controller.decide(local(32_200), remote(), now)

        // Three seconds later and still 200 ms out: two percent is not going to close this.
        val later = now + 3_500
        val decision = controller.decide(
            local(32_200 + 3_500),
            remote(atServerMs = later, positionMs = 32_000 + 3_500),
            later,
        )
        assertEquals(SyncState.CORRECTED, decision.state)
        assertEquals(35_500L, decision.seekToMs)
        assertFalse(controller.isNudging)
    }

    @Test
    fun `a nudge that expired with the gap already closed just releases`() {
        val controller = SyncController()
        controller.decide(local(32_200), remote(), now)

        val later = now + 3_500
        val decision = controller.decide(
            local(32_000 + 3_500),
            remote(atServerMs = later, positionMs = 32_000 + 3_500),
            later,
        )
        assertEquals(SyncState.IN_SYNC, decision.state)
        assertEquals(1f, decision.speed, 0f)
        assertNull(decision.seekToMs)
    }

    // --- buffering ---

    @Test
    fun `we hold rather than run ahead of a peer who is still loading`() {
        val decision = SyncController().decide(local(32_000), remote(peerBuffering = true), now)
        assertEquals(SyncState.WAITING_FOR_PEER, decision.state)
        assertFalse(decision.shouldPlay)
        assertNull(decision.seekToMs)
    }

    @Test
    fun `there is nothing to correct while we are the ones buffering`() {
        val decision = SyncController().decide(local(31_000, isBuffering = true), remote(), now)
        assertEquals(SyncState.CATCHING_UP, decision.state)
        assertNull(decision.seekToMs)
        assertEquals(1f, decision.speed, 0f)
        assertTrue(decision.shouldPlay)
    }

    @Test
    fun `a buffering peer is not projected forward`() {
        val controller = SyncController()
        assertEquals(
            30_000L,
            controller.expectedPositionMs(remote(peerBuffering = true), now),
        )
    }

    // --- pause, track changes, staleness ---

    @Test
    fun `a paused peer pauses us, and a scrub while paused still lands`() {
        val controller = SyncController()
        val together = controller.decide(local(30_000), remote(isPlaying = false), now)
        assertEquals(SyncState.PAUSED, together.state)
        assertFalse(together.shouldPlay)
        assertNull(together.seekToMs)

        val scrubbed = controller.decide(local(12_000), remote(isPlaying = false), now)
        assertEquals(30_000L, scrubbed.seekToMs)
    }

    @Test
    fun `a peer on another song tells us to load it and where to start`() {
        val decision = SyncController().decide(
            local(5_000, trackKey = "jamendo:7"),
            remote(),
            now,
        )
        assertEquals(SyncState.LOADING_TRACK, decision.state)
        assertEquals(key, decision.loadTrackKey)
        assertEquals(32_000L, decision.seekToMs)
        assertTrue(decision.shouldPlay)
    }

    @Test
    fun `a track change while the peer is buffering does not start us playing`() {
        val decision = SyncController().decide(
            local(5_000, trackKey = "jamendo:7"),
            remote(peerBuffering = true),
            now,
        )
        assertEquals(SyncState.LOADING_TRACK, decision.state)
        assertFalse(decision.shouldPlay)
    }

    @Test
    fun `an hour-old snapshot is history, not a position to chase`() {
        val controller = SyncController()
        val decision = controller.decide(local(32_000), remote(), nowServerMs = 10_000 + 60 * 60 * 1000)
        assertEquals(SyncState.STALE, decision.state)
        assertNull(decision.seekToMs)
        assertNull(decision.loadTrackKey)
    }

    @Test
    fun `a stale snapshot leaves our own playback alone`() {
        val controller = SyncController()
        val playing = controller.decide(local(32_000, isPlaying = true), remote(), 10_000 + 3_600_000)
        assertTrue(playing.shouldPlay)
        val paused = controller.decide(local(32_000, isPlaying = false), remote(), 10_000 + 3_600_000)
        assertFalse(paused.shouldPlay)
    }

    // --- the soak case the plan asks about ---

    @Test
    fun `an hour of steady jitter never lets the gap grow past the seek threshold`() {
        val controller = SyncController()
        val config = SyncConfig()
        var localPosition = 0L
        var serverTime = 0L
        var worstDrift = 0L
        var seeks = 0

        // One tick a second for an hour. The local player runs 30 ppm fast — a realistic crystal
        // error — which is exactly the slow accumulation a hard-seek-only strategy cannot see
        // coming and then has to fix audibly.
        repeat(3_600) {
            serverTime += 1_000
            localPosition += 1_000 + 30 / 1_000 + if (it % 3 == 0) 1 else 0

            val decision = controller.decide(
                local(localPosition),
                remote(positionMs = serverTime, atServerMs = serverTime),
                serverTime,
            )
            if (kotlin.math.abs(decision.driftMs) > worstDrift) worstDrift = kotlin.math.abs(decision.driftMs)
            when {
                decision.seekToMs != null -> {
                    localPosition = decision.seekToMs!!
                    seeks++
                }
                // A nudge changes the rate the local position advances at.
                decision.speed != 1f -> localPosition += ((decision.speed - 1f) * 1_000).toLong()
            }
        }

        assertTrue("worst drift was $worstDrift ms", worstDrift <= config.hardSeekMs)
        assertEquals("a steady clock error should be absorbed by nudges, not seeks", 0, seeks)
    }
}
