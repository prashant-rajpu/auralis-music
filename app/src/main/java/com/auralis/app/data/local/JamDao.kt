package com.auralis.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: JamSessionEntity)

    @Query("SELECT * FROM jam_sessions ORDER BY startedAtMs DESC LIMIT :limit")
    fun sessions(limit: Int = 100): Flow<List<JamSessionEntity>>

    @Query("SELECT * FROM jam_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun session(sessionId: String): JamSessionEntity?

    /** The session still running, if any — used to re-attach after the app was killed mid-session. */
    @Query("SELECT * FROM jam_sessions WHERE endedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun openSession(): JamSessionEntity?

    @Query("UPDATE jam_sessions SET endedAtMs = :endedAtMs, trackCount = :trackCount WHERE id = :sessionId")
    suspend fun endSession(sessionId: String, endedAtMs: Long, trackCount: Int)

    @Insert
    suspend fun recordEvent(event: JamEventEntity): Long

    @Query("SELECT * FROM jam_events WHERE sessionId = :sessionId ORDER BY atMs ASC")
    fun events(sessionId: String): Flow<List<JamEventEntity>>

    @Query("SELECT * FROM jam_events WHERE type = :type ORDER BY atMs DESC LIMIT :limit")
    fun eventsOfType(type: String, limit: Int = 100): Flow<List<JamEventEntity>>

    @Query("SELECT COUNT(*) FROM jam_sessions")
    fun sessionCount(): Flow<Int>

    @Query("DELETE FROM jam_sessions")
    suspend fun clearSessions()
}
