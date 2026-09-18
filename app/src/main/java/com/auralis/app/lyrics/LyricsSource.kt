package com.auralis.app.lyrics

import com.auralis.app.domain.model.LyricLine
import com.auralis.app.playback.LyricsProvider

/** One lyrics backend. Implementations are contributed per build flavor. */
interface LyricsSource {
    val provider: LyricsProvider

    /** Lower is tried first in automatic mode. */
    val priority: Int

    suspend fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine>
}
