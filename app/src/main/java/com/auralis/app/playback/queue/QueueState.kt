package com.auralis.app.playback.queue

import com.auralis.app.domain.model.Track
import com.auralis.app.playback.RepeatMode

/**
 * The play queue as one immutable value, so every mutation is a pure function that can be tested
 * without a player. [unshuffledOrder] remembers the pre-shuffle order so shuffle can be undone.
 */
data class QueueState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val unshuffledOrder: List<Track>? = null
) {
    val currentTrack: Track?
        get() = tracks.getOrNull(currentIndex)

    val upcomingCount: Int
        get() = if (currentIndex < 0) 0 else (tracks.size - 1 - currentIndex).coerceAtLeast(0)

    val isEmpty: Boolean
        get() = tracks.isEmpty()
}
