package com.auralis.app.network

import com.auralis.app.domain.model.Track
import org.junit.Assert.*
import org.junit.Test

class JamProtocolTest {

    @Test
    fun testCleanTopic_formatsCorrectly() {
        assertEquals("auralis_jam_babu1234", JamProtocolHelper.cleanTopic("BABU-1234"))
        assertEquals("auralis_jam_laddubabu", JamProtocolHelper.cleanTopic("  laddu & babu!  "))
        assertEquals("auralis_jam_global", JamProtocolHelper.cleanTopic(""))
        assertEquals("auralis_jam_global", JamProtocolHelper.cleanTopic("   ---   "))
    }

    @Test
    fun testSyncPlayback_serializationAndDeserialization() {
        val originalTrack = Track(
            id = "yt_test123",
            title = "Perfect",
            artist = "Ed Sheeran",
            albumArtUrl = "https://example.com/art.jpg",
            mediaUrl = "https://example.com/stream.mp3",
            durationMs = 260000L,
            source = "Auralis Master",
            qualityBadge = "320 kbps Master"
        )

        val jsonString = JamProtocolHelper.buildSyncPlaybackJson(
            username = "Babu",
            track = originalTrack,
            position = 45000L,
            isPlaying = true,
            action = "play"
        )

        // Partner receives this message
        val parsedState = JamProtocolHelper.parseJamPayload(jsonString, currentUsername = "Laddu")
        assertNotNull(parsedState)
        assertTrue(parsedState is JamState.SyncPlayback)

        val sync = parsedState as JamState.SyncPlayback
        assertEquals("Babu", sync.sender)
        assertEquals(45000L, sync.position)
        assertTrue(sync.isPlaying)
        assertEquals("play", sync.action)
        assertEquals("yt_test123", sync.track.id)
        assertEquals("Perfect", sync.track.title)
        assertEquals("Ed Sheeran", sync.track.artist)
    }

    @Test
    fun testEchoSuppression_ignoresOwnMessages() {
        val originalTrack = Track(
            id = "yt_test123",
            title = "Perfect",
            artist = "Ed Sheeran",
            albumArtUrl = "https://example.com/art.jpg",
            mediaUrl = "https://example.com/stream.mp3",
            durationMs = 260000L
        )

        val jsonString = JamProtocolHelper.buildSyncPlaybackJson(
            username = "Babu",
            track = originalTrack,
            position = 10000L,
            isPlaying = true
        )

        // Sender receives their own echo
        val parsedState = JamProtocolHelper.parseJamPayload(jsonString, currentUsername = "Babu")
        assertNull("Echo from same user must be ignored", parsedState)

        // Case-insensitive check
        val parsedCaseInsensitive = JamProtocolHelper.parseJamPayload(jsonString, currentUsername = "babu")
        assertNull("Echo must be case-insensitively ignored", parsedCaseInsensitive)
    }

    @Test
    fun testReaction_serializationAndDeserialization() {
        val jsonString = JamProtocolHelper.buildReactionJson(username = "Laddu", emoji = "💖")

        val state = JamProtocolHelper.parseJamPayload(jsonString, currentUsername = "Babu")
        assertNotNull(state)
        assertTrue(state is JamState.ReactionReceived)

        val reaction = state as JamState.ReactionReceived
        assertEquals("💖", reaction.emoji)
        assertEquals("Laddu", reaction.sender)
    }

    @Test
    fun testMemoryQuote_serializationAndDeserialization() {
        val quoteText = "I love you to the moon and back jaanaa 💋"
        val jsonString = JamProtocolHelper.buildMemoryQuoteJson(username = "Babu", quote = quoteText)

        val state = JamProtocolHelper.parseJamPayload(jsonString, currentUsername = "Laddu")
        assertNotNull(state)
        assertTrue(state is JamState.MemoryQuoteReceived)

        val quoteState = state as JamState.MemoryQuoteReceived
        assertEquals(quoteText, quoteState.quote)
        assertEquals("Babu", quoteState.sender)
    }

    @Test
    fun testQueueTrack_serializationAndDeserialization() {
        val track = Track(
            id = "yt_queue999",
            title = "Enchanted",
            artist = "Taylor Swift",
            albumArtUrl = "https://example.com/taylor.jpg",
            mediaUrl = "https://example.com/stream_enchanted.mp3",
            durationMs = 350000L
        )

        val jsonString = JamProtocolHelper.buildQueueTrackJson(username = "Wifeeee", track = track)

        val state = JamProtocolHelper.parseJamPayload(jsonString, currentUsername = "Babu")
        assertNotNull(state)
        assertTrue(state is JamState.QueueTrack)

        val queueState = state as JamState.QueueTrack
        assertEquals("Wifeeee", queueState.sender)
        assertEquals("yt_queue999", queueState.track.id)
        assertEquals("Enchanted", queueState.track.title)
    }

    @Test
    fun testDriftThreshold_shouldSeekOnlyWhenExceedsTolerance() {
        // Within 1500ms tolerance: should NOT seek (avoids jitter)
        assertFalse(JamProtocolHelper.shouldSeek(currentPositionMs = 10000L, targetPositionMs = 10500L, thresholdMs = 1500L))
        assertFalse(JamProtocolHelper.shouldSeek(currentPositionMs = 10000L, targetPositionMs = 9200L, thresholdMs = 1500L))
        assertFalse(JamProtocolHelper.shouldSeek(currentPositionMs = 10000L, targetPositionMs = 11400L, thresholdMs = 1500L))

        // Exceeds 1500ms tolerance: MUST seek to re-sync
        assertTrue(JamProtocolHelper.shouldSeek(currentPositionMs = 10000L, targetPositionMs = 11600L, thresholdMs = 1500L))
        assertTrue(JamProtocolHelper.shouldSeek(currentPositionMs = 10000L, targetPositionMs = 5000L, thresholdMs = 1500L))
    }

    @Test
    fun testExtractNtfyMessage_parsesStreamLine() {
        val streamLine = """{"id":"abc123xyz","time":1789712039,"event":"message","topic":"auralis_jam_babu1234","message":"{\"type\":\"reaction\",\"sender\":\"Laddu\",\"emoji\":\"💖\"}"}"""
        val extracted = JamProtocolHelper.extractMessageBody(streamLine)
        assertEquals("""{"type":"reaction","sender":"Laddu","emoji":"💖"}""", extracted)

        val nonMessageLine = """{"id":"abc123xyz","time":1789712039,"event":"keepalive","topic":"auralis_jam_babu1234"}"""
        assertNull(JamProtocolHelper.extractMessageBody(nonMessageLine))
    }

    @Test
    fun testCleanSearchQuery_removesNoiseAndFeatures() {
        assertEquals("Starboy The Weeknd", JamProtocolHelper.cleanSearchQuery("Starboy (Official Music Video)", "The Weeknd ft. Daft Punk"))
        assertEquals("Shape of You Ed Sheeran", JamProtocolHelper.cleanSearchQuery("Shape of You [Official Lyric Video]", "Ed Sheeran"))
        assertEquals("Blinding Lights The Weeknd", JamProtocolHelper.cleanSearchQuery("Blinding Lights (Audio)", "The Weeknd feat. Max"))
    }

    @Test
    fun testIsPlayableDirectStreamUrl() {
        assertTrue(JamProtocolHelper.isPlayableDirectStreamUrl("https://aac.saavncdn.com/077/stream_320.mp4"))
        assertTrue(JamProtocolHelper.isPlayableDirectStreamUrl("https://rr1---sn-4g5edn6s.googlevideo.com/videoplayback?expire=123"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("https://music.youtube.com/watch?v=4NRXx6U8ABQ"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("https://www.youtube.com/watch?v=4NRXx6U8ABQ"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl(""))
    }
}

