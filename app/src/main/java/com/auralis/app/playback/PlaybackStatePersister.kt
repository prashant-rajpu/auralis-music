package com.auralis.app.playback

import com.auralis.app.data.local.PlaybackStateDao
import com.auralis.app.data.local.PlayerStateEntity
import com.auralis.app.data.local.QueueItemEntity
import com.auralis.app.data.repository.LibraryRepository
import com.auralis.app.domain.model.Track
import com.auralis.app.playback.queue.QueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What was playing when the app last stopped. */
data class RestoredPlayback(
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val isShuffled: Boolean,
    val repeatMode: RepeatMode
) {
    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)
}

/**
 * Saves the queue and position so a force-stop does not throw the session away.
 *
 * Like [PlaybackHistoryRecorder] this observes the player's flows instead of being called from
 * every mutation site, so it cannot be forgotten and does not care who owns the player.
 *
 * Writes are debounced: the position ticks several times a second, and persisting each tick would
 * mean thousands of pointless database writes an hour. A second of lost position after a crash is
 * not worth that.
 */
@Singleton
class PlaybackStatePersister @Inject constructor(
    private val playbackStateDao: PlaybackStateDao,
    private val libraryRepository: LibraryRepository
) {

    fun attach(
        scope: CoroutineScope,
        queue: StateFlow<List<Track>>,
        currentIndex: StateFlow<Int>,
        positionMs: StateFlow<Long>,
        isShuffled: StateFlow<Boolean>,
        repeatMode: StateFlow<RepeatMode>
    ) {
        scope.launch {
            combine(queue, currentIndex, isShuffled, repeatMode) { tracks, index, shuffled, repeat ->
                Snapshot(tracks, index, shuffled, repeat)
            }
                .distinctUntilChanged()
                .debounce(SAVE_DEBOUNCE_MS)
                .collect { snapshot -> save(snapshot, positionMs.value) }
        }

        // Position on its own, saved far less often: it is the cheapest field to be slightly wrong
        // about and the most expensive to keep exactly right.
        scope.launch {
            positionMs
                .debounce(POSITION_DEBOUNCE_MS)
                .collect { position ->
                    val tracks = queue.value
                    if (tracks.isEmpty()) return@collect
                    save(
                        Snapshot(tracks, currentIndex.value, isShuffled.value, repeatMode.value),
                        position
                    )
                }
        }
    }

    private data class Snapshot(
        val tracks: List<Track>,
        val currentIndex: Int,
        val isShuffled: Boolean,
        val repeatMode: RepeatMode
    )

    private suspend fun save(snapshot: Snapshot, positionMs: Long) {
        if (snapshot.tracks.isEmpty()) {
            playbackStateDao.clearSnapshot()
            return
        }

        // The queue stores ids only, so the tracks themselves have to be in the catalog or the
        // restore would come back as a list of unknown ids.
        libraryRepository.remember(snapshot.tracks)

        playbackStateDao.saveSnapshot(
            items = snapshot.tracks.mapIndexed { index, track ->
                QueueItemEntity(
                    position = index,
                    trackId = track.id,
                    isRecommendation = track.isAutoplayRecommendation
                )
            },
            state = PlayerStateEntity(
                currentIndex = snapshot.currentIndex,
                positionMs = positionMs,
                isShuffled = snapshot.isShuffled,
                repeatMode = snapshot.repeatMode.name,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * Returns null when there is nothing worth restoring. A queue whose tracks have all vanished
     * from the catalog counts as nothing: restoring a shorter queue would land on the wrong track.
     */
    suspend fun restore(): RestoredPlayback? {
        val items = playbackStateDao.queue()
        val state = playbackStateDao.playerState() ?: return null
        if (items.isEmpty()) return null

        val ordered = items.sortedBy { it.position }
        val tracks = libraryRepository.tracks(ordered.map { it.trackId })
        if (tracks.size != ordered.size) return null

        val restoredTracks = tracks.mapIndexed { index, track ->
            if (ordered[index].isRecommendation) track.copy(isAutoplayRecommendation = true) else track
        }

        return RestoredPlayback(
            tracks = restoredTracks,
            currentIndex = state.currentIndex.coerceIn(0, restoredTracks.lastIndex),
            positionMs = state.positionMs.coerceAtLeast(0L),
            isShuffled = state.isShuffled,
            repeatMode = runCatching { RepeatMode.valueOf(state.repeatMode) }.getOrDefault(RepeatMode.OFF)
        )
    }

    /** Used when the user clears the queue, so the next launch does not resurrect it. */
    suspend fun clear() = playbackStateDao.clearSnapshot()

    fun snapshotOf(queueState: QueueState, positionMs: Long): Pair<List<QueueItemEntity>, PlayerStateEntity> =
        queueState.tracks.mapIndexed { index, track ->
            QueueItemEntity(index, track.id, track.isAutoplayRecommendation)
        } to PlayerStateEntity(
            currentIndex = queueState.currentIndex,
            positionMs = positionMs,
            isShuffled = queueState.isShuffled,
            repeatMode = queueState.repeatMode.name,
            updatedAtMs = System.currentTimeMillis()
        )

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
        const val POSITION_DEBOUNCE_MS = 5_000L
    }
}
