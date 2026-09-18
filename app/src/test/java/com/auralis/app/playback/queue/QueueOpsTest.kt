package com.auralis.app.playback.queue

import com.auralis.app.domain.model.Track
import com.auralis.app.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueOpsTest {

    private fun track(id: String, autoplay: Boolean = false) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist",
        albumArtUrl = null,
        mediaUrl = "https://discoveryprovider.audius.co/v1/tracks/$id/stream",
        durationMs = 1000L,
        isAutoplayRecommendation = autoplay
    )

    private fun state(ids: List<String>, currentIndex: Int, autoplayFrom: Int = Int.MAX_VALUE) =
        QueueState(
            tracks = ids.mapIndexed { i, id -> track(id, autoplay = i >= autoplayFrom) },
            currentIndex = currentIndex
        )

    private fun ids(s: QueueState) = s.tracks.map { it.id }

    // --- play -------------------------------------------------------------

    @Test
    fun playStartsAtTheChosenTrackWithinItsQueue() {
        val queue = listOf(track("a"), track("b"), track("c"))
        val result = QueueOps.play(QueueState(), track("b"), queue)
        assertEquals(listOf("a", "b", "c"), ids(result))
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun playFallsBackToASingleTrackQueueWhenTheTrackIsNotInIt() {
        val result = QueueOps.play(QueueState(), track("z"), listOf(track("a"), track("b")))
        assertEquals(listOf("z"), ids(result))
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun playClearsAnyShuffleMemoryFromThePreviousQueue() {
        val shuffled = QueueOps.setShuffled(state(listOf("a", "b", "c"), 0), true, Random(1))
        val result = QueueOps.play(shuffled, track("x"))
        assertFalse(result.isShuffled)
        assertNull(result.unshuffledOrder)
    }

    // --- playNext ---------------------------------------------------------

    @Test
    fun playNextInsertsDirectlyAfterTheCurrentTrack() {
        val result = QueueOps.playNext(state(listOf("a", "b", "c"), 0), track("x"))
        assertEquals(listOf("a", "x", "b", "c"), ids(result))
        assertEquals("the current track must not move", 0, result.currentIndex)
    }

    @Test
    fun playNextMovesATrackAlreadyQueuedLaterInsteadOfDuplicatingIt() {
        val result = QueueOps.playNext(state(listOf("a", "b", "c"), 0), track("c"))
        assertEquals(listOf("a", "c", "b"), ids(result))
    }

    @Test
    fun playNextLeavesAnAlreadyPlayedCopyAloneSoHistoryIsIntact() {
        val result = QueueOps.playNext(state(listOf("a", "b", "c"), 2), track("a"))
        assertEquals(listOf("a", "b", "c", "a"), ids(result))
    }

    @Test
    fun playNextOnAnEmptyQueueStartsPlayback() {
        val result = QueueOps.playNext(QueueState(), track("x"))
        assertEquals(listOf("x"), ids(result))
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun playNextStripsTheAutoplayFlagSoUserChoicesAreNotTreatedAsRadio() {
        val result = QueueOps.playNext(state(listOf("a", "b"), 0), track("x", autoplay = true))
        assertFalse(result.tracks[1].isAutoplayRecommendation)
    }

    // --- addToQueue -------------------------------------------------------

    @Test
    fun addToQueueAppendsWhenThereAreNoRecommendations() {
        val result = QueueOps.addToQueue(state(listOf("a", "b"), 0), track("x"))
        assertEquals(listOf("a", "b", "x"), ids(result))
    }

    @Test
    fun addToQueueGoesAheadOfRadioPicks() {
        // a, b chosen by the user; r1, r2 appended by the radio
        val s = state(listOf("a", "b", "r1", "r2"), 0, autoplayFrom = 2)
        val result = QueueOps.addToQueue(s, track("x"))
        assertEquals(listOf("a", "b", "x", "r1", "r2"), ids(result))
    }

    @Test
    fun addToQueueIgnoresRecommendationsAlreadyPlayed() {
        // The only autoplay track sits behind the current index, so x still appends
        val s = state(listOf("r0", "a", "b"), 2).let {
            it.copy(tracks = it.tracks.mapIndexed { i, t -> if (i == 0) t.copy(isAutoplayRecommendation = true) else t })
        }
        val result = QueueOps.addToQueue(s, track("x"))
        assertEquals(listOf("r0", "a", "b", "x"), ids(result))
    }

    @Test
    fun addToQueueDoesNotDuplicate() {
        val s = state(listOf("a", "b"), 0)
        assertEquals(ids(s), ids(QueueOps.addToQueue(s, track("b"))))
    }

    // --- move / remove / clear -------------------------------------------

    @Test
    fun moveKeepsTheCurrentTrackSelectedEvenWhenItShifts() {
        val result = QueueOps.move(state(listOf("a", "b", "c"), 2), 0, 2)
        assertEquals(listOf("b", "c", "a"), ids(result))
        assertEquals("c is still playing", 1, result.currentIndex)
    }

    @Test
    fun moveRejectsOutOfRangeAndNoOpIndices() {
        val s = state(listOf("a", "b"), 0)
        assertEquals(ids(s), ids(QueueOps.move(s, 0, 0)))
        assertEquals(ids(s), ids(QueueOps.move(s, 5, 0)))
        assertEquals(ids(s), ids(QueueOps.move(s, 0, 5)))
    }

    @Test
    fun removeShiftsTheCurrentIndexWhenAnEarlierTrackGoes() {
        val result = QueueOps.remove(state(listOf("a", "b", "c"), 2), 0)
        assertEquals(listOf("b", "c"), ids(result))
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun removeRefusesToDropTheTrackThatIsPlaying() {
        val s = state(listOf("a", "b", "c"), 1)
        assertEquals(ids(s), ids(QueueOps.remove(s, 1)))
    }

    @Test
    fun clearUpcomingKeepsHistoryAndTheCurrentTrack() {
        val result = QueueOps.clearUpcoming(state(listOf("a", "b", "c", "d"), 1))
        assertEquals(listOf("a", "b"), ids(result))
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun clearRecommendationsKeepsUserTracksAndAnythingAlreadyPlayed() {
        val s = state(listOf("a", "r1", "b", "r2"), 1, autoplayFrom = 1)
            .let { it.copy(tracks = it.tracks.mapIndexed { i, t -> t.copy(isAutoplayRecommendation = i == 1 || i == 3) }) }
        val result = QueueOps.clearRecommendations(s)
        assertEquals("r1 already played, so it stays", listOf("a", "r1", "b"), ids(result))
        assertEquals(1, result.currentIndex)
    }

    // --- recommendations --------------------------------------------------

    @Test
    fun appendRecommendationsSkipsDuplicatesAndAlreadyPlayedTracks() {
        val s = state(listOf("a", "b"), 0)
        val result = QueueOps.appendRecommendations(
            s,
            candidates = listOf(track("b"), track("c"), track("d"), track("c")),
            alreadyPlayedIds = setOf("d")
        )
        assertEquals(listOf("a", "b", "c"), ids(result))
        assertTrue(result.tracks.last().isAutoplayRecommendation)
    }

    @Test
    fun appendRecommendationsRespectsTheLimit() {
        val result = QueueOps.appendRecommendations(
            state(listOf("a"), 0),
            candidates = (1..20).map { track("r$it") },
            limit = 3
        )
        assertEquals(4, result.tracks.size)
    }

    @Test
    fun appendRecommendationsLeavesTheQueueAloneWhenNothingIsNew() {
        val s = state(listOf("a", "b"), 0)
        assertEquals(s, QueueOps.appendRecommendations(s, listOf(track("a"), track("b"))))
    }

    // --- shuffle ----------------------------------------------------------

    @Test
    fun shuffleReordersOnlyWhatHasNotPlayedYet() {
        val s = state((1..10).map { "t$it" }, 2)
        val result = QueueOps.setShuffled(s, true, Random(42))

        assertEquals("history and current track are untouched", listOf("t1", "t2", "t3"), ids(result).take(3))
        assertEquals("no track is lost or duplicated", ids(s).toSet(), ids(result).toSet())
        assertEquals(2, result.currentIndex)
        assertTrue(result.isShuffled)
    }

    @Test
    fun unshuffleRestoresTheOriginalOrderAndFollowsTheCurrentTrack() {
        val original = state((1..10).map { "t$it" }, 2)
        val shuffled = QueueOps.setShuffled(original, true, Random(7))
        val restored = QueueOps.setShuffled(shuffled, false)

        assertEquals(ids(original), ids(restored))
        assertFalse(restored.isShuffled)
        assertNull(restored.unshuffledOrder)
        assertEquals("t3", restored.currentTrack?.id)
    }

    @Test
    fun unshuffleKeepsPlayingTheSameTrackEvenIfItMovedWhileShuffled() {
        val original = state((1..10).map { "t$it" }, 0)
        val shuffled = QueueOps.setShuffled(original, true, Random(3)).copy(currentIndex = 5)
        val playingId = shuffled.currentTrack?.id

        val restored = QueueOps.setShuffled(shuffled, false)
        assertEquals(playingId, restored.currentTrack?.id)
    }

    // --- navigation -------------------------------------------------------

    @Test
    fun nextAdvancesThenStopsAtTheEnd() {
        assertEquals(1, QueueOps.nextIndex(state(listOf("a", "b", "c"), 0)))
        assertNull(QueueOps.nextIndex(state(listOf("a", "b", "c"), 2)))
    }

    @Test
    fun repeatAllWrapsAroundAndRepeatOneStaysPut() {
        val end = state(listOf("a", "b"), 1)
        assertEquals(0, QueueOps.nextIndex(end.copy(repeatMode = RepeatMode.ALL)))
        assertEquals(1, QueueOps.nextIndex(end.copy(repeatMode = RepeatMode.ONE)))
    }

    @Test
    fun shuffledNextIsSequentialSoEveryTrackPlaysOnce() {
        // The old implementation jumped to a random index, which could repeat one track forever
        val shuffled = QueueOps.setShuffled(state((1..6).map { "t$it" }, 0), true, Random(11))
        val visited = mutableListOf(shuffled.currentTrack!!.id)
        var s = shuffled
        while (true) {
            val next = QueueOps.nextIndex(s) ?: break
            s = QueueOps.playAt(s, next)
            visited.add(s.currentTrack!!.id)
        }
        assertEquals("every track plays exactly once", visited.size, visited.toSet().size)
        assertEquals(6, visited.size)
    }

    @Test
    fun previousStepsBackAndSignalsRestartAtTheStart() {
        assertEquals(0, QueueOps.previousIndex(state(listOf("a", "b"), 1)))
        assertNull(QueueOps.previousIndex(state(listOf("a", "b"), 0)))
        assertEquals(1, QueueOps.previousIndex(state(listOf("a", "b"), 0).copy(repeatMode = RepeatMode.ALL)))
    }

    @Test
    fun navigationOnAnEmptyQueueYieldsNothing() {
        assertNull(QueueOps.nextIndex(QueueState()))
        assertNull(QueueOps.previousIndex(QueueState()))
    }

    @Test
    fun repeatModeCycles() {
        assertEquals(RepeatMode.ALL, QueueOps.nextRepeatMode(RepeatMode.OFF))
        assertEquals(RepeatMode.ONE, QueueOps.nextRepeatMode(RepeatMode.ALL))
        assertEquals(RepeatMode.OFF, QueueOps.nextRepeatMode(RepeatMode.ONE))
    }

    @Test
    fun upcomingCountDrivesRadioPrefetch() {
        assertEquals(2, state(listOf("a", "b", "c"), 0).upcomingCount)
        assertEquals(0, state(listOf("a", "b", "c"), 2).upcomingCount)
        assertEquals(0, QueueState().upcomingCount)
    }
}
