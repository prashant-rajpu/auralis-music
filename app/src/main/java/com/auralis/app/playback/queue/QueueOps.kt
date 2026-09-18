package com.auralis.app.playback.queue

import com.auralis.app.domain.model.Track
import com.auralis.app.playback.RepeatMode
import kotlin.random.Random

/**
 * Pure queue transformations. Every function returns a new [QueueState]; nothing here touches a
 * player, so the index arithmetic that used to live in PlaybackManager is directly testable.
 */
object QueueOps {

    /** Start a fresh queue at [track], which need not be in [queue]. */
    fun play(state: QueueState, track: Track, queue: List<Track> = listOf(track)): QueueState {
        val tracks = if (queue.any { it.id == track.id }) queue else listOf(track)
        return state.copy(
            tracks = tracks,
            currentIndex = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
            unshuffledOrder = null,
            isShuffled = false
        )
    }

    fun playAt(state: QueueState, index: Int): QueueState =
        if (index in state.tracks.indices) state.copy(currentIndex = index) else state

    /**
     * Queue [track] directly after the current one. A copy already queued later is moved rather
     * than duplicated; one earlier in the queue is left alone so history stays intact.
     */
    fun playNext(state: QueueState, track: Track): QueueState {
        val clean = track.copy(isAutoplayRecommendation = false)
        if (state.isEmpty || state.currentIndex < 0) return play(state, clean)

        val working = state.tracks.toMutableList()
        val existing = working.indexOfFirst { it.id == clean.id }
        if (existing > state.currentIndex) working.removeAt(existing)

        val insertAt = (state.currentIndex + 1).coerceAtMost(working.size)
        working.add(insertAt, clean)
        return state.copy(tracks = working)
    }

    /**
     * Append [track], but ahead of any autoplay recommendations: something the user chose should
     * not wait behind tracks the radio guessed at.
     */
    fun addToQueue(state: QueueState, track: Track): QueueState {
        val clean = track.copy(isAutoplayRecommendation = false)
        if (state.isEmpty || state.currentIndex < 0) return play(state, clean)
        if (state.tracks.any { it.id == clean.id }) return state

        val working = state.tracks.toMutableList()
        // indexOfFirst gives the real position; the old code called indexOf inside the predicate,
        // which returns the first equal element's index and misplaced duplicates.
        val firstAutoplay = working.indexOfFirst { it.isAutoplayRecommendation }
            .takeIf { it > state.currentIndex }
        if (firstAutoplay != null) working.add(firstAutoplay, clean) else working.add(clean)
        return state.copy(tracks = working)
    }

    /** Append radio picks, skipping anything already queued or already played this session. */
    fun appendRecommendations(
        state: QueueState,
        candidates: List<Track>,
        alreadyPlayedIds: Set<String> = emptySet(),
        limit: Int = 8
    ): QueueState {
        val fresh = candidates
            .filter { candidate ->
                state.tracks.none { it.id == candidate.id } && candidate.id !in alreadyPlayedIds
            }
            .distinctBy { it.id }
            .take(limit)
            .map { it.copy(isAutoplayRecommendation = true) }
        return if (fresh.isEmpty()) state else state.copy(tracks = state.tracks + fresh)
    }

    fun move(state: QueueState, from: Int, to: Int): QueueState {
        if (from !in state.tracks.indices || to !in state.tracks.indices || from == to) return state
        val currentId = state.currentTrack?.id
        val working = state.tracks.toMutableList()
        working.add(to, working.removeAt(from))
        return state.copy(
            tracks = working,
            currentIndex = working.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        )
    }

    /** Removing the track that is playing would need a transition, so it is refused here. */
    fun remove(state: QueueState, index: Int): QueueState {
        if (index !in state.tracks.indices || index == state.currentIndex) return state
        val currentId = state.currentTrack?.id
        val working = state.tracks.toMutableList()
        working.removeAt(index)
        return state.copy(
            tracks = working,
            currentIndex = working.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        )
    }

    fun clearUpcoming(state: QueueState): QueueState =
        if (state.currentIndex in state.tracks.indices) {
            state.copy(tracks = state.tracks.take(state.currentIndex + 1))
        } else {
            state
        }

    fun clearRecommendations(state: QueueState): QueueState {
        val currentId = state.currentTrack?.id
        val kept = state.tracks.filterIndexed { index, track ->
            index <= state.currentIndex || !track.isAutoplayRecommendation
        }
        return state.copy(
            tracks = kept,
            currentIndex = kept.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        )
    }

    /**
     * Shuffle reorders only what has not played yet, so history and the current track stay put and
     * "Up Next" shows the real order. Turning it off restores the order it started from.
     */
    fun setShuffled(state: QueueState, shuffled: Boolean, random: Random = Random): QueueState {
        if (shuffled == state.isShuffled) return state

        if (!shuffled) {
            val original = state.unshuffledOrder ?: return state.copy(isShuffled = false)
            val currentId = state.currentTrack?.id
            return state.copy(
                tracks = original,
                currentIndex = original.indexOfFirst { it.id == currentId }.coerceAtLeast(0),
                isShuffled = false,
                unshuffledOrder = null
            )
        }

        val head = state.tracks.take(state.currentIndex + 1)
        val upcoming = state.tracks.drop(state.currentIndex + 1)
        return state.copy(
            tracks = head + upcoming.shuffled(random),
            isShuffled = true,
            unshuffledOrder = state.tracks
        )
    }

    /**
     * Where [skipNext] goes, or null when the queue is exhausted and the caller should ask the
     * radio for more. Shuffle is a real reordering, so "next" is simply the next index — the old
     * code jumped to a random index, which could repeat tracks forever and never play others.
     */
    fun nextIndex(state: QueueState): Int? = when {
        state.isEmpty -> null
        state.repeatMode == RepeatMode.ONE -> state.currentIndex
        state.currentIndex + 1 < state.tracks.size -> state.currentIndex + 1
        state.repeatMode == RepeatMode.ALL -> 0
        else -> null
    }

    /** Null means "restart the current track" rather than move. */
    fun previousIndex(state: QueueState): Int? = when {
        state.isEmpty -> null
        state.currentIndex - 1 >= 0 -> state.currentIndex - 1
        state.repeatMode == RepeatMode.ALL -> state.tracks.size - 1
        else -> null
    }

    fun nextRepeatMode(mode: RepeatMode): RepeatMode = when (mode) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    /** The track the radio should base its next recommendations on. */
    fun radioSeed(state: QueueState): Track? = state.tracks.lastOrNull() ?: state.currentTrack
}
