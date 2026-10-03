package com.auralis.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundHoldTest {

    @Test
    fun `a playing player keeps the placeholder, because it may be all that holds the service up`() {
        assertFalse(ForegroundHold.shouldRelease(playWhenReady = true, isPlaying = true, shown = true, shownIsPlaceholder = true))
        assertFalse(ForegroundHold.shouldRelease(playWhenReady = true, isPlaying = false, shown = false, shownIsPlaceholder = false))
    }

    @Test
    fun `buffering counts as playing, since that is when the notification is first due`() {
        // playWhenReady is true while a slow stream resolves; releasing then would drop the
        // service just before playback starts.
        assertFalse(ForegroundHold.shouldRelease(playWhenReady = true, isPlaying = false, shown = true, shownIsPlaceholder = true))
    }

    @Test
    fun `a paused player with the real notification up is left alone`() {
        // The useful state: resuming from the lock screen depends on this notification surviving.
        assertFalse(ForegroundHold.shouldRelease(playWhenReady = false, isPlaying = false, shown = true, shownIsPlaceholder = false))
    }

    @Test
    fun `a paused player showing only the placeholder lets it go`() {
        // The partner-paused case: the service was started and the player immediately paused, so
        // Media3 never took over. Leaving a "starting" notification up forever is worse than none.
        assertTrue(ForegroundHold.shouldRelease(playWhenReady = false, isPlaying = false, shown = true, shownIsPlaceholder = true))
    }

    @Test
    fun `with notifications off and nothing playing there is nothing real to keep`() {
        assertTrue(ForegroundHold.shouldRelease(playWhenReady = false, isPlaying = false, shown = false, shownIsPlaceholder = false))
    }
}
