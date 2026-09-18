package com.auralis.app.data

import com.auralis.app.data.local.OfflineDownloader
import com.auralis.app.data.local.TrackDao
import com.auralis.app.data.local.TrackEntity
import com.auralis.app.data.repository.AggregatedMusicRepository
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.MusicSource
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatedMusicRepositoryTest {

    private class FakeSource(
        override val provider: Provider,
        override val priority: Int,
        override val trendingPriority: Int = priority,
        override val isEnabled: Boolean = true,
        private val searchResults: List<Track> = emptyList(),
        private val trendingResults: List<Track> = emptyList(),
        private val relatedResults: List<Track> = emptyList(),
        private val failing: Boolean = false
    ) : MusicSource {
        var searchCalls = 0
            private set

        override suspend fun search(query: String): List<Track> {
            searchCalls++
            if (failing) throw IllegalStateException("$provider is down")
            return searchResults
        }

        override suspend fun trending(): List<Track> {
            if (failing) throw IllegalStateException("$provider is down")
            return trendingResults
        }

        override suspend fun related(seed: Track): List<Track> = relatedResults
    }

    private class FakeTrackDao(private val rows: MutableList<TrackEntity> = mutableListOf()) : TrackDao {
        override fun getAllTracks(): List<TrackEntity> = rows
        override fun getTrackById(trackId: String): TrackEntity? = rows.find { it.id == trackId }
        override fun insertTrack(track: TrackEntity) { rows.add(track) }
        override fun insertTracks(tracks: List<TrackEntity>) { rows.addAll(tracks) }
        override fun deleteTrack(trackId: String) { rows.removeAll { it.id == trackId } }
    }

    private fun track(id: String, title: String = "Song", artist: String = "Artist") = Track(
        id = id,
        title = title,
        artist = artist,
        albumArtUrl = null,
        mediaUrl = "https://discoveryprovider.audius.co/v1/tracks/$id/stream",
        durationMs = 1000L
    )

    private fun entity(id: String, title: String = "Song", artist: String = "Artist") = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        albumArtUrl = null,
        mediaUrl = "/files/music/$id.mp3",
        durationMs = 1000L
    )

    private fun repository(
        sources: Set<MusicSource>,
        dao: TrackDao = FakeTrackDao()
    ) = AggregatedMusicRepository(sources, dao, mockk<OfflineDownloader>(relaxed = true))

    @Test
    fun searchQueriesEverySourceAndOrdersByPriority() = runTest {
        val yt = FakeSource(Provider.YOUTUBE, priority = 0, searchResults = listOf(track("yt_a")))
        val audius = FakeSource(Provider.AUDIUS, priority = 20, searchResults = listOf(track("auralis_global_b")))

        val results = repository(setOf(audius, yt)).searchTracks("perfect")

        assertEquals(listOf("yt_a", "auralis_global_b"), results.map { it.id })
        assertEquals(1, yt.searchCalls)
        assertEquals(1, audius.searchCalls)
    }

    @Test
    fun searchFilterQueriesOnlyTheChosenProvider() = runTest {
        val yt = FakeSource(Provider.YOUTUBE, priority = 0, searchResults = listOf(track("yt_a")))
        val audius = FakeSource(Provider.AUDIUS, priority = 20, searchResults = listOf(track("auralis_global_b")))

        val results = repository(setOf(yt, audius)).searchTracks("perfect", filter = Provider.AUDIUS)

        assertEquals(listOf("auralis_global_b"), results.map { it.id })
        assertEquals(0, yt.searchCalls)
    }

    @Test
    fun oneFailingSourceDoesNotLoseTheOthersResults() = runTest {
        val broken = FakeSource(Provider.YOUTUBE, priority = 0, failing = true)
        val working = FakeSource(Provider.AUDIUS, priority = 20, searchResults = listOf(track("auralis_global_b")))

        val results = repository(setOf(broken, working)).searchTracks("perfect")

        assertEquals(listOf("auralis_global_b"), results.map { it.id })
    }

    @Test
    fun searchIncludesDownloadsAndMarksThemDownloaded() = runTest {
        val dao = FakeTrackDao(mutableListOf(entity("auralis_global_b", title = "Perfect")))
        val audius = FakeSource(Provider.AUDIUS, priority = 20, searchResults = listOf(track("auralis_global_b", title = "Perfect")))

        val results = repository(setOf(audius), dao).searchTracks("perfect")

        assertEquals(1, results.size)
        assertTrue("a downloaded track must be flagged", results.single().isDownloaded)
    }

    @Test
    fun blankQuerySkipsEverySource() = runTest {
        val yt = FakeSource(Provider.YOUTUBE, priority = 0, searchResults = listOf(track("yt_a")))

        assertTrue(repository(setOf(yt)).searchTracks("   ").isEmpty())
        assertEquals(0, yt.searchCalls)
    }

    @Test
    fun disabledSourcesAreNeverQueriedOrAdvertised() = runTest {
        val disabled = FakeSource(Provider.JAMENDO, priority = 30, isEnabled = false, searchResults = listOf(track("jamendo_x")))
        val repo = repository(setOf(disabled))

        assertTrue(repo.searchTracks("anything").isEmpty())
        assertEquals(0, disabled.searchCalls)
        assertFalse(Provider.JAMENDO in repo.availableProviders)
    }

    @Test
    fun trendingFallsThroughToTheNextSourceWhenOneFailsOrIsEmpty() = runTest {
        val failing = FakeSource(Provider.JIOSAAVN, priority = 10, failing = true)
        val empty = FakeSource(Provider.AUDIUS, priority = 20, trendingResults = emptyList())
        val good = FakeSource(Provider.JAMENDO, priority = 30, trendingResults = listOf(track("jamendo_z")))

        val results = repository(setOf(failing, empty, good)).fetchServerTracks()

        assertEquals(listOf("jamendo_z"), results.map { it.id })
    }

    @Test
    fun trendingUsesTrendingPriorityNotSearchPriority() = runTest {
        // YouTube searches first but has no real charts, so it must be tried last for trending
        val yt = FakeSource(Provider.YOUTUBE, priority = 0, trendingPriority = 90, trendingResults = listOf(track("yt_a")))
        val audius = FakeSource(Provider.AUDIUS, priority = 20, trendingResults = listOf(track("auralis_global_b")))

        val results = repository(setOf(yt, audius)).fetchServerTracks()

        assertEquals(listOf("auralis_global_b"), results.map { it.id })
    }

    @Test
    fun trendingFallsBackToTheLibraryWhenEverySourceFails() = runTest {
        val dao = FakeTrackDao(mutableListOf(entity("auralis_offline")))
        val broken = FakeSource(Provider.AUDIUS, priority = 20, failing = true)

        val results = repository(setOf(broken), dao).fetchServerTracks()

        assertEquals(listOf("auralis_offline"), results.map { it.id })
    }

    @Test
    fun relatedFallsBackToAnArtistSearchWhenTheSourceHasNoRecommendations() = runTest {
        val seed = track("auralis_global_seed", artist = "Dua Lipa")
        val audius = FakeSource(
            Provider.AUDIUS,
            priority = 20,
            relatedResults = emptyList(),
            searchResults = listOf(seed, track("auralis_global_other", artist = "Dua Lipa"))
        )

        val results = repository(setOf(audius)).relatedTracks(seed)

        assertEquals("the seed itself must not be recommended", listOf("auralis_global_other"), results.map { it.id })
    }

    @Test
    fun relatedPrefersTheSeedsOwnSource() = runTest {
        val seed = track("auralis_global_seed")
        val audius = FakeSource(Provider.AUDIUS, priority = 20, relatedResults = listOf(track("auralis_global_rec")))
        val yt = FakeSource(Provider.YOUTUBE, priority = 0, relatedResults = listOf(track("yt_rec")))

        val results = repository(setOf(yt, audius)).relatedTracks(seed)

        assertEquals(listOf("auralis_global_rec"), results.map { it.id })
    }
}
