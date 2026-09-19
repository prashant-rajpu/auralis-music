package com.auralis.app.data.repository

import com.auralis.app.data.local.HistoryDao
import com.auralis.app.data.local.JamDao
import com.auralis.app.data.local.JamEventEntity
import com.auralis.app.data.local.JamSessionEntity
import com.auralis.app.data.local.SharedPlayEntity
import com.auralis.app.domain.model.Track
import com.auralis.app.together.ListeningStreak
import com.auralis.app.together.TogetherRecorder
import com.auralis.app.together.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One session, as it reads back on the timeline months later. */
data class SessionMemory(
    val id: String,
    val partnerName: String?,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val trackCount: Int,
    val wasHost: Boolean,
)

/**
 * What the two of you have built up, session by session.
 *
 * A track is *ours* because we were both there when it played — which plain play history cannot
 * express, and which is why `shared_plays` exists beside it. Everything here reads from rows that
 * a live session writes; nothing is inferred from the ordinary history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CoupleRepository @Inject constructor(
    private val jamDao: JamDao,
    private val historyDao: HistoryDao,
    private val library: LibraryRepository,
) : TogetherRecorder {

    // ---- while a session runs -------------------------------------------------------------

    /** Opens a session row. Returns the id that every play and event is filed under. */
    override suspend fun beginSession(
        roomCode: String,
        partnerName: String?,
        wasHost: Boolean,
    ): String = beginSession(roomCode, partnerName, wasHost, System.currentTimeMillis())

    suspend fun beginSession(
        roomCode: String,
        partnerName: String?,
        wasHost: Boolean,
        nowMs: Long,
    ): String {
        // A session left open by a crash is finished rather than left dangling, or the timeline
        // would grow a run of sessions that never ended.
        jamDao.openSession()?.let { jamDao.endSession(it.id, nowMs, it.trackCount) }

        val id = UUID.randomUUID().toString()
        jamDao.upsertSession(
            JamSessionEntity(
                id = id,
                roomCode = roomCode,
                partnerName = partnerName,
                startedAtMs = nowMs,
                endedAtMs = null,
                trackCount = 0,
                wasHost = wasHost,
            ),
        )
        return id
    }

    /**
     * A track that played while both of you were in the room. This is the row Our Songs and the
     * streak are built from, so it is written once per track per session rather than per tick.
     */
    override suspend fun recordSharedPlay(
        sessionId: String,
        track: Track,
        partnerName: String?,
        addedByMe: Boolean,
    ) = recordSharedPlay(sessionId, track, partnerName, addedByMe, System.currentTimeMillis())

    suspend fun recordSharedPlay(
        sessionId: String,
        track: Track,
        partnerName: String?,
        addedByMe: Boolean,
        nowMs: Long,
    ) {
        library.remember(track, nowMs)
        historyDao.recordShared(
            SharedPlayEntity(
                trackId = track.id,
                jamSessionId = sessionId,
                partnerName = partnerName,
                playedAtMs = nowMs,
                addedByMe = addedByMe,
            ),
        )
        jamDao.recordEvent(
            JamEventEntity(
                sessionId = sessionId,
                type = EVENT_TRACK,
                trackId = track.id,
                payload = null,
                fromMe = addedByMe,
                atMs = nowMs,
            ),
        )
        val session = jamDao.session(sessionId) ?: return
        jamDao.upsertSession(session.copy(trackCount = session.trackCount + 1))
    }

    /**
     * Something worth remembering that is not a track: a dedication, a lyric, a knock.
     *
     * [payload] is the local plaintext. It reached the relay encrypted; what lands here is on the
     * user's own device, where hiding it from them would serve nobody.
     */
    override suspend fun recordEvent(
        sessionId: String,
        type: String,
        payload: String?,
        fromMe: Boolean,
        trackId: String?,
    ) = recordEvent(sessionId, type, payload, fromMe, trackId, System.currentTimeMillis())

    suspend fun recordEvent(
        sessionId: String,
        type: String,
        payload: String?,
        fromMe: Boolean,
        trackId: String?,
        nowMs: Long,
    ) {
        jamDao.recordEvent(
            JamEventEntity(
                sessionId = sessionId,
                type = type,
                trackId = trackId,
                payload = payload,
                fromMe = fromMe,
                atMs = nowMs,
            ),
        )
    }

    override suspend fun endSession(sessionId: String) = endSession(sessionId, System.currentTimeMillis())

    suspend fun endSession(sessionId: String, nowMs: Long) {
        val session = jamDao.session(sessionId) ?: return
        jamDao.endSession(sessionId, nowMs, session.trackCount)
    }

    // ---- what it adds up to ---------------------------------------------------------------

    /** Ranked by how often you heard it together, not by how often either of you played it. */
    fun ourSongs(limit: Int = 50): Flow<List<Track>> =
        historyDao.ourSongs(limit).flatMapLatest { counts ->
            flow { emit(library.tracks(counts.map { it.trackId })) }
        }

    val sharedPlayCount: Flow<Int> = historyDao.sharedPlayCount()

    /**
     * Recomputed whenever a shared play lands. The count is the trigger rather than the data:
     * the streak needs every timestamp, and a `Flow` of a couple of thousand longs would be a
     * lot of rows to keep live for a number.
     */
    val streak: Flow<Streak> = historyDao.sharedPlayCount().map {
        ListeningStreak.from(historyDao.sharedPlayTimestamps())
    }

    fun memories(limit: Int = 100): Flow<List<SessionMemory>> =
        jamDao.sessions(limit).map { rows ->
            rows.map {
                SessionMemory(
                    id = it.id,
                    partnerName = it.partnerName,
                    startedAtMs = it.startedAtMs,
                    endedAtMs = it.endedAtMs,
                    trackCount = it.trackCount,
                    wasHost = it.wasHost,
                )
            }
        }

    fun events(sessionId: String): Flow<List<JamEventEntity>> = jamDao.events(sessionId)

    /** Every dedication ever sent or received, newest first. */
    fun dedications(limit: Int = 100): Flow<List<JamEventEntity>> =
        jamDao.eventsOfType(EVENT_DEDICATION, limit)

    val sessionCount: Flow<Int> = jamDao.sessionCount()

    /** Sessions and their length, for the "you have listened together for N hours" line. */
    val totalTimeTogetherMs: Flow<Long> = jamDao.sessions(limit = 1000).map { rows ->
        rows.sumOf { (it.endedAtMs ?: it.startedAtMs) - it.startedAtMs }
    }

    suspend fun forgetEverything() = jamDao.clearSessions()

    companion object {
        const val EVENT_TRACK = "track"
        const val EVENT_DEDICATION = "dedication"
        const val EVENT_LYRIC = "lyric"
        const val EVENT_KNOCK = "knock"
        const val EVENT_CHAT = "chat"
    }
}
