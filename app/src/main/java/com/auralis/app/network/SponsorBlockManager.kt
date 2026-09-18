package com.auralis.app.network

import android.util.Log
import com.auralis.app.domain.model.Track
import com.auralis.app.playback.SegmentSkipper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorBlockManager @Inject constructor(
    private val sponsorBlockApi: SponsorBlockApi
) : SegmentSkipper {
    // In-memory cache for fast lookup during playback position ticks
    private val segmentCache = mutableMapOf<String, List<SponsorSegment>>()

    suspend fun fetchSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isEmpty()) return@withContext emptyList()

        segmentCache[cleanId]?.let { return@withContext it }

        try {
            val segments = sponsorBlockApi.getSkipSegments(videoId = cleanId)
            segmentCache[cleanId] = segments
            Log.d("SponsorBlockManager", "Fetched ${segments.size} skip segments for $cleanId")
            segments
        } catch (e: Exception) {
            // Silently treat 404 or missing segments as empty list and cache to avoid spam
            segmentCache[cleanId] = emptyList()
            emptyList()
        }
    }

    /**
     * Checks if the track begins with a non-music intro/skit/dialogue segment (e.g. from 0 to 30s).
     * If so, returns the millisecond offset to jump straight into the music.
     */
    fun getIntroSkipTargetMs(videoId: String): Long? {
        val segments = segmentCache[videoId] ?: return null
        val introSegment = segments.firstOrNull { segment ->
            segment.startMs <= 2000L && segment.endMs > 3000L
        }
        return introSegment?.endMs
    }

    /**
     * Checks if the current playback position falls inside a non-music or sponsor segment.
     * If so, returns the end position to seek past it.
     */
    fun checkSkipTargetMs(videoId: String, currentPositionMs: Long): Long? {
        val segments = segmentCache[videoId] ?: return null
        val hit = segments.firstOrNull { segment ->
            currentPositionMs >= segment.startMs && currentPositionMs < segment.endMs - 500L
        }
        return hit?.endMs
    }

    override fun supports(track: Track): Boolean = track.isYouTubeTrack()

    override suspend fun prepare(track: Track) {
        fetchSegments(videoIdOf(track))
    }

    override fun introSkipTargetMs(track: Track): Long? = getIntroSkipTargetMs(videoIdOf(track))

    override fun skipTargetMs(track: Track, positionMs: Long): Long? = checkSkipTargetMs(videoIdOf(track), positionMs)

    private fun videoIdOf(track: Track): String = track.getYouTubeVideoId() ?: track.id.removePrefix("yt_")
}
