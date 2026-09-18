package com.auralis.app.network

import com.auralis.app.domain.model.Track
import org.junit.Assert.*
import org.junit.Test

class JamProtocolTest {

    @Test
    fun testCleanTopic_formatsCorrectly() {
        assertEquals("auralis_jam_babu1234", JamProtocolHelper.cleanTopic("BABU-1234"))
        assertEquals("auralis_jam_laddubabu", JamProtocolHelper.cleanTopic("  laddu & babu!  "))
    }

    @Test
    fun testIsValidJamCode_rejectsBlankAndShortCodes() {
        assertTrue(JamProtocolHelper.isValidJamCode("BABU-1234"))
        assertTrue(JamProtocolHelper.isValidJamCode("ab12"))
        // A blank code used to land everyone in one shared "global" room
        assertFalse(JamProtocolHelper.isValidJamCode(""))
        assertFalse(JamProtocolHelper.isValidJamCode("   ---   "))
        assertFalse(JamProtocolHelper.isValidJamCode("ab1"))
        assertFalse(JamProtocolHelper.isValidJamCode("x".repeat(40)))
    }

    @Test
    fun testSyncPlayback_serializationAndDeserialization() {
        val originalTrack = Track(
            id = "yt_test123",
            title = "Perfect",
            artist = "Ed Sheeran",
            albumArtUrl = "https://i.ytimg.com/vi/test123/hqdefault.jpg",
            mediaUrl = "https://aac.saavncdn.com/077/stream_320.mp4",
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
        assertEquals(originalTrack.mediaUrl, sync.track.mediaUrl)
        assertEquals(originalTrack.albumArtUrl, sync.track.albumArtUrl)
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
    fun testInboundTrack_dropsUrlsOffTheAllowlist() {
        val hostile = Track(
            id = "yt_abcdefghijk",
            title = "Perfect",
            artist = "Ed Sheeran",
            albumArtUrl = "https://evil.example.com/tracker.jpg",
            mediaUrl = "https://evil.example.com/anything.mp3",
            durationMs = 260000L
        )

        val sync = JamProtocolHelper.parseJamPayload(
            JamProtocolHelper.buildSyncPlaybackJson("Babu", hostile, 0L, true), currentUsername = "Laddu"
        ) as JamState.SyncPlayback
        assertEquals("A peer must not be able to make us fetch an arbitrary URL", "", sync.track.mediaUrl)
        assertNull(sync.track.albumArtUrl)

        val queued = JamProtocolHelper.parseJamPayload(
            JamProtocolHelper.buildQueueTrackJson("Babu", hostile), currentUsername = "Laddu"
        ) as JamState.QueueTrack
        assertEquals("", queued.track.mediaUrl)
        assertNull(queued.track.albumArtUrl)
    }

    @Test
    fun testInboundTrack_keepsCanonicalYouTubeAndCdnUrls() {
        val watch = Track(
            id = "yt_abcdefghijk",
            title = "Perfect",
            artist = "Ed Sheeran",
            albumArtUrl = "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
            mediaUrl = "https://www.youtube.com/watch?v=abcdefghijk",
            durationMs = 260000L
        )
        val parsedWatch = JamProtocolHelper.parseJamPayload(
            JamProtocolHelper.buildSyncPlaybackJson("Babu", watch, 0L, true), currentUsername = "Laddu"
        ) as JamState.SyncPlayback
        assertEquals(watch.mediaUrl, parsedWatch.track.mediaUrl)
        assertEquals(watch.albumArtUrl, parsedWatch.track.albumArtUrl)

        val audius = watch.copy(
            id = "auralis_global_xyz",
            mediaUrl = "https://discoveryprovider.audius.co/v1/tracks/xyz/stream?app_name=Auralis",
            albumArtUrl = "https://creatornode2.audius.co/content/abc/480x480.jpg"
        )
        val parsedAudius = JamProtocolHelper.parseJamPayload(
            JamProtocolHelper.buildSyncPlaybackJson("Babu", audius, 0L, true), currentUsername = "Laddu"
        ) as JamState.SyncPlayback
        assertEquals(audius.mediaUrl, parsedAudius.track.mediaUrl)
        assertEquals(audius.albumArtUrl, parsedAudius.track.albumArtUrl)
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
            albumArtUrl = "https://i.ytimg.com/vi/queue999/hqdefault.jpg",
            mediaUrl = "https://aac.saavncdn.com/077/stream_enchanted_320.mp4",
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
        assertEquals(track.mediaUrl, queueState.track.mediaUrl)
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
    fun testParseNtfyEvent_exposesIdForDeduplication() {
        val streamLine = """{"id":"abc123xyz","time":1789712039,"event":"message","topic":"auralis_jam_babu1234","message":"{}"}"""
        val event = JamProtocolHelper.parseNtfyEvent(streamLine)
        assertNotNull(event)
        assertEquals("abc123xyz", event!!.id)
        assertEquals("message", event.event)
        assertEquals("{}", event.message)

        assertNull(JamProtocolHelper.parseNtfyEvent("not json"))
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
        assertTrue(JamProtocolHelper.isPlayableDirectStreamUrl("https://discoveryprovider.audius.co/v1/tracks/abc/stream?app_name=Auralis"))
        assertTrue(JamProtocolHelper.isPlayableDirectStreamUrl("https://cdns-preview-a.dzcdn.net/stream/c-abc-1.mp3"))
        assertTrue(JamProtocolHelper.isPlayableDirectStreamUrl("/data/user/0/com.auralis.app/files/music/yt_abc_song.mp4"))
        assertTrue(JamProtocolHelper.isPlayableDirectStreamUrl("file:///data/user/0/com.auralis.app/files/music/a.mp4"))

        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("https://music.youtube.com/watch?v=4NRXx6U8ABQ"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("https://www.youtube.com/watch?v=4NRXx6U8ABQ"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("https://evil.example.com/track.mp3"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("https://googlevideo.com.evil.example/x"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("https://user@evil.example/saavncdn.com/x.mp4"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("http://aac.saavncdn.com/077/stream_320.mp4"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl("yt:abc"))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl(""))
        assertFalse(JamProtocolHelper.isPlayableDirectStreamUrl(null))
    }

    @Test
    fun testNeedsStreamResolution_skipsLocalFilesEvenForYouTubeIds() {
        val downloadedYouTubeTrack = Track(
            id = "yt_abcdefghijk",
            title = "Perfect",
            artist = "Ed Sheeran",
            albumArtUrl = null,
            mediaUrl = "/data/user/0/com.auralis.app/files/music/yt_abcdefghijk_Perfect.webm",
            durationMs = 260000L
        )
        assertFalse(JamProtocolHelper.needsStreamResolution(downloadedYouTubeTrack))

        val watchPage = downloadedYouTubeTrack.copy(mediaUrl = "https://www.youtube.com/watch?v=abcdefghijk")
        assertTrue(JamProtocolHelper.needsStreamResolution(watchPage))

        val directSaavn = downloadedYouTubeTrack.copy(id = "auralis_1", mediaUrl = "https://aac.saavncdn.com/077/stream_320.mp4")
        assertFalse(JamProtocolHelper.needsStreamResolution(directSaavn))
    }
}
