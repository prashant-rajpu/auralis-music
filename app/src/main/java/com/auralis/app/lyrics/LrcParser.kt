package com.auralis.app.lyrics

import com.auralis.app.domain.model.LyricLine

object LrcParser {
    // Matches [01:23.45] or [01:23.456] or [1:23.45]
    private val TIMESTAMP_REGEX = Regex("\\[(\\d{1,2}):(\\d{2}(?:\\.\\d{1,3})?)\\]")

    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()

        val lyricLines = mutableListOf<LyricLine>()

        lrcContent.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            // Ignore metadata tags like [ar:Artist], [ti:Title], [al:Album], [offset:0]
            if (trimmed.startsWith("[") && !trimmed.matches(Regex("^\\[\\d+.*"))) {
                return@forEach
            }

            val timestamps = mutableListOf<Long>()
            val matcher = TIMESTAMP_REGEX.findAll(trimmed)

            for (match in matcher) {
                val minStr = match.groupValues[1]
                val secStr = match.groupValues[2]
                try {
                    val minutes = minStr.toLong()
                    val seconds = secStr.toDouble()
                    val totalMs = (minutes * 60 * 1000) + (seconds * 1000).toLong()
                    timestamps.add(totalMs)
                } catch (e: Exception) {
                    // Ignore parsing error on unexpected malformed number
                }
            }

            if (timestamps.isNotEmpty()) {
                val text = trimmed.replace(TIMESTAMP_REGEX, "").trim()
                for (ts in timestamps) {
                    lyricLines.add(LyricLine(timestampMs = ts, text = text))
                }
            }
        }

        return lyricLines.sortedBy { it.timestampMs }
    }
}
