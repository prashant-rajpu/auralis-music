package com.auralis.app.lyrics

import com.auralis.app.domain.model.LyricLine

object PlainLyrics {
    /** Spreads unsynced lines evenly across the track so they can still auto-scroll. */
    fun toTimedLines(plainText: String, durationMs: Long): List<LyricLine> {
        val lines = plainText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val safeDuration = if (durationMs > 0) durationMs else 180000L
        val interval = (safeDuration - 5000L).coerceAtLeast(1000L) / lines.size.coerceAtLeast(1)

        return lines.mapIndexed { index, line ->
            LyricLine(timestampMs = index * interval, text = line)
        }
    }
}
