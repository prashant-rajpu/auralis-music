package com.auralis.app.network

/**
 * Bounded set of recently seen message ids. The Jam client subscribes to the same ntfy topic over
 * WebSocket and an HTTP stream at once, so every message can arrive twice.
 */
class RecentMessageIds(private val capacity: Int = 200) {
    private val ids = LinkedHashSet<String>()

    /** Returns true the first time [id] is seen, false for any repeat within the window. */
    @Synchronized
    fun markSeen(id: String): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > capacity) {
            val oldest = ids.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }
}
