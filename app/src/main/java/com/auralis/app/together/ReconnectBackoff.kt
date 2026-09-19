package com.auralis.app.together

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * How long to wait before trying the relay again.
 *
 * Exponential, capped, and jittered. The jitter is the part that matters: without it, every phone
 * that dropped when the relay restarted comes back at the same instant and knocks it over again.
 *
 * Pure, so the schedule can be asserted rather than observed over thirty real seconds.
 */
class ReconnectBackoff(
    private val baseDelayMs: Long = 500,
    private val maxDelayMs: Long = 30_000,
    private val factor: Double = 2.0,
    /** Fraction of the delay that is randomised away, spreading a thundering herd. */
    private val jitter: Double = 0.25,
) {
    /** `attempt` is 0 for the first retry after a drop. */
    fun delayForMs(attempt: Int, random: Random = Random.Default): Long {
        val uncapped = baseDelayMs * factor.pow(attempt.coerceAtLeast(0))
        val capped = min(uncapped, maxDelayMs.toDouble())
        val spread = capped * jitter
        return (capped - spread * random.nextDouble()).toLong().coerceAtLeast(0L)
    }
}
