package com.auralis.app.playback

/**
 * Decisions about the media service's foreground notification, kept pure so they can be tested
 * without a device — which matters, because getting them wrong is not a degraded feature but
 * `ForegroundServiceDidNotStartInTimeException`, which kills the whole app.
 *
 * The contract Android imposes: once `startForegroundService()` has been called, the service has
 * about ten seconds to call `startForeground()`. Media3 does that itself, but only when it has a
 * session registered *and* the player is playing or buffering. Neither is guaranteed here — the
 * player lives in the UI process, nothing binds a controller, and a partner's paused state
 * arriving over Together starts the service and then pauses the player. So the service holds the
 * foreground slot itself with a placeholder, and Media3's real notification replaces it.
 */
object ForegroundHold {

    /** Long enough for the real notification to arrive, short enough not to strand a stale one. */
    const val GRACE_MS = 15_000L

    /**
     * Whether the placeholder should be let go of once the grace period is up.
     *
     * Never while something is playing or about to: the placeholder is then the only thing keeping
     * the service alive if Media3 failed to take over. When nothing is playing it goes only if it
     * is all that is showing. A paused player with Media3's own notification up is the normal,
     * useful state — that notification is how you resume from the lock screen — and must survive.
     *
     * [shownIsPlaceholder] is only meaningful when [shown] is true. When notifications are
     * switched off nothing is listed at all, so `shown = false` reads as "nothing real to keep".
     */
    fun shouldRelease(
        playWhenReady: Boolean,
        isPlaying: Boolean,
        shown: Boolean,
        shownIsPlaceholder: Boolean,
    ): Boolean {
        if (playWhenReady || isPlaying) return false
        return !shown || shownIsPlaceholder
    }
}
