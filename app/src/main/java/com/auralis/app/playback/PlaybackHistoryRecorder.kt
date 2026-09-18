package com.auralis.app.playback

import com.auralis.app.data.repository.LibraryRepository
import com.auralis.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one `play_history` row per track actually listened to.
 *
 * It observes the player's flows rather than being called from the places that change the track.
 * That keeps it independent of whoever owns the player — the same object will work unchanged once
 * playback moves into the service — and means no future call site can forget to report a play.
 *
 * A row is written when the track changes, not when it starts, because the interesting fields are
 * how much was heard and whether it was skipped, and neither is known at the start.
 */
@Singleton
class PlaybackHistoryRecorder @Inject constructor(
    private val libraryRepository: LibraryRepository
) {

    /**
     * A play counts as skipped when less than this much of it was heard. The same threshold the
     * affinity scorer will use for a negative signal.
     */
    private val skipThreshold = 0.3f

    fun attach(
        scope: CoroutineScope,
        currentTrack: StateFlow<Track?>,
        positionMs: StateFlow<Long>,
        durationMs: StateFlow<Long>,
        playbackContext: () -> String = { "queue" }
    ) {
        scope.launch {
            var playing: Track? = null
            var startedAtMs = 0L
            var furthestMs = 0L
            var knownDurationMs = 0L

            combine(currentTrack, positionMs, durationMs) { track, position, duration ->
                Triple(track, position, duration)
            }.collect { (track, position, duration) ->
                if (track?.id != playing?.id) {
                    // Flush the outgoing track first: the incoming one has already reset position.
                    playing?.let { finished ->
                        record(finished, furthestMs, knownDurationMs, startedAtMs, playbackContext())
                    }
                    playing = track
                    startedAtMs = System.currentTimeMillis()
                    furthestMs = 0L
                    knownDurationMs = duration
                } else {
                    // Position is polled, so take the furthest point reached rather than the last
                    // sample — otherwise a seek back to the start would erase the whole play.
                    if (position > furthestMs) furthestMs = position
                    if (duration > 0L) knownDurationMs = duration
                }
            }
        }
    }

    private suspend fun record(
        track: Track,
        playedMs: Long,
        durationMs: Long,
        startedAtMs: Long,
        context: String
    ) {
        // Under a second is a pass-through while skipping, not a play worth learning from.
        if (playedMs < MIN_PLAY_MS) return

        val effectiveDuration = if (durationMs > 0L) durationMs else track.durationMs
        val ratio = if (effectiveDuration > 0L) {
            (playedMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        libraryRepository.recordPlay(
            track = track,
            playedMs = playedMs,
            completionRatio = ratio,
            skipped = ratio < skipThreshold,
            context = context,
            startedAtMs = startedAtMs
        )
    }

    private companion object {
        const val MIN_PLAY_MS = 1_000L
    }
}
