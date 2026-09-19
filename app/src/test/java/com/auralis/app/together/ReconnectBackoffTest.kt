package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReconnectBackoffTest {

    /** Jitter off, so the schedule itself is visible. */
    private val plain = ReconnectBackoff(jitter = 0.0)

    @Test
    fun `it backs off exponentially`() {
        assertEquals(500L, plain.delayForMs(0))
        assertEquals(1_000L, plain.delayForMs(1))
        assertEquals(2_000L, plain.delayForMs(2))
        assertEquals(4_000L, plain.delayForMs(3))
    }

    @Test
    fun `it stops growing at the cap rather than overflowing`() {
        assertEquals(30_000L, plain.delayForMs(20))
        assertEquals(30_000L, plain.delayForMs(1_000))
    }

    @Test
    fun `the first retry is quick enough to feel like nothing happened`() {
        assertTrue(plain.delayForMs(0) <= 500)
    }

    @Test
    fun `jitter only ever shortens the wait, and never past zero`() {
        val jittered = ReconnectBackoff(jitter = 0.25)
        repeat(200) {
            val delay = jittered.delayForMs(3, Random(it))
            assertTrue("$delay", delay in 3_000..4_000)
        }
    }

    @Test
    fun `two phones dropped by the same restart do not come back together`() {
        val jittered = ReconnectBackoff(jitter = 0.25)
        val delays = (0 until 50).map { jittered.delayForMs(5, Random(it)) }.toSet()
        assertTrue("every phone picked the same moment", delays.size > 20)
    }

    @Test
    fun `a negative attempt is treated as the first`() {
        assertEquals(plain.delayForMs(0), plain.delayForMs(-3))
    }
}
