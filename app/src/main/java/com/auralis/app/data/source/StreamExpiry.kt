package com.auralis.app.data.source

/** Works out how long a resolved stream URL stays usable. */
object StreamExpiry {

    /** googlevideo links carry their own deadline; nothing else advertises one. */
    private val EXPIRE_PARAM = Regex("""[?&]expire=(\d{10,})""")

    val DEFAULT_TTL_MS = 12 * 60 * 60 * 1000L

    /** A margin so a URL is never handed to the player moments before it dies. */
    private val SAFETY_MARGIN_MS = 5 * 60 * 1000L

    fun expiresAtMs(url: String, nowMs: Long, defaultTtlMs: Long = DEFAULT_TTL_MS): Long {
        val stated = EXPIRE_PARAM.find(url)?.groupValues?.get(1)?.toLongOrNull()
        if (stated != null) {
            val statedMs = stated * 1000L
            // Ignore a deadline already in the past; the caller will just re-resolve
            if (statedMs > nowMs) return (statedMs - SAFETY_MARGIN_MS).coerceAtLeast(nowMs)
        }
        return nowMs + defaultTtlMs
    }

    fun isFresh(expiresAtMs: Long, nowMs: Long): Boolean = expiresAtMs > nowMs
}
