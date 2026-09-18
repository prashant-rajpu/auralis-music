package com.auralis.app.playback

import com.auralis.app.domain.model.Track

/** Knows which parts of a track are not music (sponsor reads, intros) so playback can jump past them. */
interface SegmentSkipper {
    fun supports(track: Track): Boolean
    suspend fun prepare(track: Track)
    fun introSkipTargetMs(track: Track): Long?
    fun skipTargetMs(track: Track, positionMs: Long): Long?
}
