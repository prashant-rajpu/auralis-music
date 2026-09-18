package com.auralis.app.playback

import com.auralis.app.domain.model.Track

/**
 * Pure helper for queue partitioning between user-queued tracks and infinite radio autoplay recommendations.
 */
object QueuePartitionHelper {

    fun insertManualTrack(currentQueue: List<Track>, newTrack: Track): List<Track> {
        val updated = currentQueue.toMutableList()
        val firstAutoplayIndex = updated.indexOfFirst { it.isAutoplayRecommendation }
        if (firstAutoplayIndex != -1) {
            updated.add(firstAutoplayIndex, newTrack)
        } else {
            updated.add(newTrack)
        }
        return updated
    }

    fun insertPlayNext(currentQueue: List<Track>, currentIndex: Int, nextTrack: Track): List<Track> {
        val updated = currentQueue.toMutableList()
        val insertIndex = (currentIndex + 1).coerceAtMost(updated.size)
        updated.add(insertIndex, nextTrack)
        return updated
    }

    fun filterOutAutoplay(currentQueue: List<Track>): List<Track> {
        return currentQueue.filter { !it.isAutoplayRecommendation }
    }
}
