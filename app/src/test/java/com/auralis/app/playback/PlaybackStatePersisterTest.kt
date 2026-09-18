package com.auralis.app.playback

import com.auralis.app.data.local.PlaybackStateDao
import com.auralis.app.data.local.PlayerStateEntity
import com.auralis.app.data.local.QueueItemEntity
import com.auralis.app.data.repository.LibraryRepository
import com.auralis.app.domain.model.Track
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackStatePersisterTest {

    private lateinit var dao: PlaybackStateDao
    private lateinit var library: LibraryRepository
    private lateinit var persister: PlaybackStatePersister

    private fun track(id: String) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist",
        albumArtUrl = null,
        mediaUrl = "https://example.invalid/$id.m4a",
        durationMs = 200_000L
    )

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        library = mockk(relaxed = true)
        persister = PlaybackStatePersister(dao, library)
    }

    private fun state(index: Int, positionMs: Long = 0L, shuffled: Boolean = false, repeat: String = "OFF") =
        PlayerStateEntity(
            currentIndex = index,
            positionMs = positionMs,
            isShuffled = shuffled,
            repeatMode = repeat,
            updatedAtMs = 0L
        )

    @Test
    fun `restores the queue in its saved order`() = runTest {
        val ids = listOf("c", "a", "b")
        // Rows come back in an arbitrary order, as a database is entitled to do.
        coEvery { dao.queue() } returns listOf(
            QueueItemEntity(2, "b", false),
            QueueItemEntity(0, "c", false),
            QueueItemEntity(1, "a", false)
        )
        coEvery { dao.playerState(any()) } returns state(index = 1, positionMs = 42_000L)
        coEvery { library.tracks(ids) } returns ids.map(::track)

        val restored = persister.restore()

        assertEquals(ids, restored!!.tracks.map { it.id })
        assertEquals("a", restored.currentTrack?.id)
        assertEquals(42_000L, restored.positionMs)
    }

    @Test
    fun `gives up when some tracks can no longer be resolved`() = runTest {
        coEvery { dao.queue() } returns listOf(
            QueueItemEntity(0, "a", false),
            QueueItemEntity(1, "gone", false),
            QueueItemEntity(2, "c", false)
        )
        coEvery { dao.playerState(any()) } returns state(index = 2)
        // The catalog lost one; a shorter queue would resume on the wrong song.
        coEvery { library.tracks(any()) } returns listOf(track("a"), track("c"))

        assertNull(persister.restore())
    }

    @Test
    fun `returns nothing when there is no saved queue`() = runTest {
        coEvery { dao.queue() } returns emptyList()
        coEvery { dao.playerState(any()) } returns state(index = 0)

        assertNull(persister.restore())
    }

    @Test
    fun `returns nothing when there is no saved player state`() = runTest {
        coEvery { dao.queue() } returns listOf(QueueItemEntity(0, "a", false))
        coEvery { dao.playerState(any()) } returns null

        assertNull(persister.restore())
    }

    @Test
    fun `clamps an index that points past the end of the queue`() = runTest {
        coEvery { dao.queue() } returns listOf(QueueItemEntity(0, "a", false))
        coEvery { dao.playerState(any()) } returns state(index = 9)
        coEvery { library.tracks(any()) } returns listOf(track("a"))

        val restored = persister.restore()

        assertEquals(0, restored!!.currentIndex)
        assertEquals("a", restored.currentTrack?.id)
    }

    @Test
    fun `keeps radio recommendations marked so the queue partition survives`() = runTest {
        coEvery { dao.queue() } returns listOf(
            QueueItemEntity(0, "a", false),
            QueueItemEntity(1, "b", true)
        )
        coEvery { dao.playerState(any()) } returns state(index = 0)
        coEvery { library.tracks(any()) } returns listOf(track("a"), track("b"))

        val restored = persister.restore()!!

        assertTrue(restored.tracks[1].isAutoplayRecommendation)
        assertTrue(!restored.tracks[0].isAutoplayRecommendation)
    }

    @Test
    fun `falls back to no repeat when the stored mode is not recognised`() = runTest {
        coEvery { dao.queue() } returns listOf(QueueItemEntity(0, "a", false))
        coEvery { dao.playerState(any()) } returns state(index = 0, repeat = "SOMETHING_OLD")
        coEvery { library.tracks(any()) } returns listOf(track("a"))

        assertEquals(RepeatMode.OFF, persister.restore()!!.repeatMode)
    }

    @Test
    fun `restores shuffle and repeat as they were left`() = runTest {
        coEvery { dao.queue() } returns listOf(QueueItemEntity(0, "a", false))
        coEvery { dao.playerState(any()) } returns state(index = 0, shuffled = true, repeat = "ONE")
        coEvery { library.tracks(any()) } returns listOf(track("a"))

        val restored = persister.restore()!!

        assertTrue(restored.isShuffled)
        assertEquals(RepeatMode.ONE, restored.repeatMode)
    }
}
