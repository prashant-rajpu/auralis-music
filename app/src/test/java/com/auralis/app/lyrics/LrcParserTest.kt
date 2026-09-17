package com.auralis.app.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_emptyString_returnsEmptyList() {
        val result = LrcParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_standardTimestamps_returnsChronologicalLyricLines() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:01.50]First line of the song
            [00:05.20]Second line after pause
            [01:10.00]Chorus line starts
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)

        assertEquals(1500L, lines[0].timestampMs)
        assertEquals("First line of the song", lines[0].text)

        assertEquals(5200L, lines[1].timestampMs)
        assertEquals("Second line after pause", lines[1].text)

        assertEquals(70000L, lines[2].timestampMs)
        assertEquals("Chorus line starts", lines[2].text)
    }

    @Test
    fun parse_outOfOrderTimestamps_sortedChronologically() {
        val lrc = """
            [00:10.00]Late line
            [00:02.00]Early line
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(2000L, lines[0].timestampMs)
        assertEquals("Early line", lines[0].text)
        assertEquals(10000L, lines[1].timestampMs)
        assertEquals("Late line", lines[1].text)
    }

    @Test
    fun parse_threeDigitMilliseconds_handledCorrectly() {
        val lrc = "[01:05.500]Line with 3 digit ms"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(65500L, lines[0].timestampMs)
        assertEquals("Line with 3 digit ms", lines[0].text)
    }
}
