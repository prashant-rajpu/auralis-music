package com.auralis.app.together

import kotlin.math.abs
import kotlin.math.roundToLong

/** One completed ping/pong exchange, in milliseconds. */
data class ClockSample(val roundTripMs: Long, val offsetMs: Long)

/**
 * Works out how far this phone's clock is from the relay's.
 *
 * Phone clocks drift by seconds, so "play at position 42,000" means nothing without a shared
 * reference. The relay stamps every message with its own clock; this turns that into an offset
 * we can add to `System.currentTimeMillis()` to get the room's time.
 *
 * The method is the one NTP uses. A ping carries the local send time, the pong comes back with the
 * server's time, and the offset is `serverMs − midpoint(sent, received)`. That midpoint assumes the
 * request and response legs took the same time, which is only true when the path is uncongested —
 * so samples with a round trip much worse than the best seen recently are thrown away rather than
 * smoothed in. A slow sample is not noise around the truth; it is biased, and averaging it in moves
 * the offset in whichever direction the congestion happened to fall.
 *
 * Pure: every timestamp is passed in, so a test can drive a whole session in microseconds.
 */
class ClockSync(
    /** Weight of each accepted sample. Low enough to ride out jitter, high enough to follow drift. */
    private val smoothing: Double = 0.25,
    /** A sample is accepted while its round trip is within this much of the best one recently. */
    private val rttTolerance: Double = 1.5,
    /** Absolute slack on top of the tolerance, so a 4 ms best does not reject every 8 ms sample. */
    private val rttSlackMs: Long = 20,
    /** How many round trips "recently" covers. Older ones age out, so a worse path is relearned. */
    private val windowSize: Int = 8,
    /** Below this many accepted samples the offset is a guess, not a measurement. */
    private val minSamplesForSync: Int = 3,
) {
    private val recentRoundTrips = ArrayDeque<Long>()
    private var smoothedOffset: Double = 0.0
    private var accepted = 0

    /** Server clock minus local clock. Add it to a local timestamp to get room time. */
    val offsetMs: Long get() = smoothedOffset.roundToLong()

    /** The best round trip seen recently — a fair estimate of the floor on one-way latency × 2. */
    val bestRoundTripMs: Long get() = recentRoundTrips.minOrNull() ?: 0L

    val sampleCount: Int get() = accepted

    /** False until enough samples agree; until then, do not correct playback against it. */
    val isSynced: Boolean get() = accepted >= minSamplesForSync

    /**
     * Folds in one pong. Returns the sample if it was used, or null if it was discarded as too
     * slow or impossible (a clock that went backwards mid-exchange).
     */
    fun observe(sentAtMs: Long, serverMs: Long, receivedAtMs: Long): ClockSample? {
        val roundTrip = receivedAtMs - sentAtMs
        if (roundTrip < 0) return null

        val best = recentRoundTrips.minOrNull()
        remember(roundTrip)
        if (best != null && roundTrip > best * rttTolerance + rttSlackMs) return null

        // Midpoint of the exchange in local time; the server's stamp is assumed to have been taken
        // there. Computed this way rather than as sentAt + roundTrip / 2 so it cannot lose a
        // millisecond to integer division on an odd round trip.
        val midpoint = (sentAtMs + receivedAtMs) / 2.0
        val offset = serverMs - midpoint

        smoothedOffset = if (accepted == 0) offset else smoothedOffset + smoothing * (offset - smoothedOffset)
        accepted++
        return ClockSample(roundTripMs = roundTrip, offsetMs = offset.roundToLong())
    }

    /** Room time for a local timestamp. */
    fun serverNow(localNowMs: Long): Long = localNowMs + offsetMs

    /** Local time for a room timestamp — the inverse, for scheduling something at a shared moment. */
    fun localTimeOf(serverMs: Long): Long = serverMs - offsetMs

    /** After a reconnect the path may be entirely different; start again rather than drift toward it. */
    fun reset() {
        recentRoundTrips.clear()
        smoothedOffset = 0.0
        accepted = 0
    }

    private fun remember(roundTrip: Long) {
        recentRoundTrips.addLast(roundTrip)
        while (recentRoundTrips.size > windowSize) recentRoundTrips.removeFirst()
    }

    companion object {
        /** How far apart two clocks have to be before it is worth telling the user. */
        const val NOTICEABLE_OFFSET_MS = 1_000L

        fun isLargeOffset(offsetMs: Long): Boolean = abs(offsetMs) > NOTICEABLE_OFFSET_MS
    }
}
