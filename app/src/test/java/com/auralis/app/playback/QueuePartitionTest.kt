package com.auralis.app.playback

import com.auralis.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePartitionTest {

    private fun createTrack(id: String, title: String, isAutoplay: Boolean = false): Track {
        return Track(
            id = id,
            title = title,
            artist = "Test Artist",
            albumArtUrl = "https://example.com/art.jpg",
            mediaUrl = "https://example.com/stream.mp3",
            durationMs = 180000L,
            isAutoplayRecommendation = isAutoplay
        )
    }

    @Test
    fun testPartitionHelper_addToQueue_insertsBeforeFirstAutoplayTrack() {
        val currentQueue = listOf(
            createTrack("1", "Song 1", isAutoplay = false),
            createTrack("2", "Song 2", isAutoplay = false),
            createTrack("3", "Radio Song 1", isAutoplay = true),
            createTrack("4", "Radio Song 2", isAutoplay = true)
        )

        val newManualTrack = createTrack("user_1", "User Choice", isAutoplay = false)

        val result = QueuePartitionHelper.insertManualTrack(currentQueue, newManualTrack)

        assertEquals(5, result.size)
        // User track should be inserted at index 2 (before first autoplay track)
        assertEquals("user_1", result[2].id)
        assertEquals("3", result[3].id)
        assertEquals("4", result[4].id)
    }

    @Test
    fun testPartitionHelper_addToQueue_appendsToEndWhenNoAutoplayTracks() {
        val currentQueue = listOf(
            createTrack("1", "Song 1", isAutoplay = false),
            createTrack("2", "Song 2", isAutoplay = false)
        )

        val newManualTrack = createTrack("user_1", "User Choice", isAutoplay = false)

        val result = QueuePartitionHelper.insertManualTrack(currentQueue, newManualTrack)

        assertEquals(3, result.size)
        assertEquals("user_1", result.last().id)
    }

    @Test
    fun testPartitionHelper_playNext_insertsImmediatelyAfterCurrentIndex() {
        val currentQueue = listOf(
            createTrack("1", "Song 1", isAutoplay = false),
            createTrack("2", "Song 2", isAutoplay = false),
            createTrack("3", "Radio Song 1", isAutoplay = true)
        )

        val nextTrack = createTrack("priority_1", "Immediate Next", isAutoplay = false)

        val result = QueuePartitionHelper.insertPlayNext(currentQueue, currentIndex = 0, nextTrack)

        assertEquals(4, result.size)
        assertEquals("1", result[0].id)
        assertEquals("priority_1", result[1].id)
        assertEquals("2", result[2].id)
        assertEquals("3", result[3].id)
    }

    @Test
    fun testPartitionHelper_clearAutoplay_preservesManualTracksOnly() {
        val currentQueue = listOf(
            createTrack("1", "Song 1", isAutoplay = false),
            createTrack("2", "Song 2", isAutoplay = false),
            createTrack("3", "Radio Song 1", isAutoplay = true),
            createTrack("4", "Radio Song 2", isAutoplay = true)
        )

        val result = QueuePartitionHelper.filterOutAutoplay(currentQueue)

        assertEquals(2, result.size)
        assertTrue(result.all { !it.isAutoplayRecommendation })
        assertEquals("1", result[0].id)
        assertEquals("2", result[1].id)
    }

    @Test
    fun testCuratedCatalog_providesGuaranteedStarterTracks() {
        val fallbackTracks = CuratedCatalog.getStarterTracks()
        assertTrue("Curated tracks must not be empty", fallbackTracks.isNotEmpty())
        assertTrue("Curated tracks must have at least 5 tracks", fallbackTracks.size >= 5)
        for (track in fallbackTracks) {
            assertTrue("Track title must not be blank", track.title.isNotBlank())
            assertTrue("Track artist must not be blank", track.artist.isNotBlank())
            assertTrue("Track mediaUrl must not be blank", track.mediaUrl.isNotBlank())
            assertFalse("Starter tracks must not be marked as autoplay", track.isAutoplayRecommendation)
        }
    }
}
