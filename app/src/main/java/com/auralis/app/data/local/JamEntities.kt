package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One listening-together session, kept after it ends.
 *
 * Together is the headline feature and it is aimed at people who listen together regularly, so a
 * session is worth remembering rather than discarding when the socket closes: the streak, the
 * session timeline and "Our Songs" are all built from these rows.
 */
@Entity(
    tableName = "jam_sessions",
    indices = [Index("startedAtMs"), Index("roomCode")]
)
data class JamSessionEntity(
    @PrimaryKey val id: String,
    val roomCode: String,
    /** Other participant's display name when the session ran; kept so history survives a rename. */
    val partnerName: String?,
    val startedAtMs: Long,
    /** Null while the session is live. */
    val endedAtMs: Long?,
    val trackCount: Int,
    val wasHost: Boolean
)

/**
 * What happened during a session, in order: tracks played, who added what, reactions, dedications.
 *
 * [payload] is free-form text rather than typed columns because event kinds will keep being added,
 * and a schema migration per new reaction type is not a trade worth making. Message content that
 * reaches the relay is encrypted in transit; what lands here is the local plaintext, on the user's
 * own device.
 */
@Entity(
    tableName = "jam_events",
    foreignKeys = [
        ForeignKey(
            entity = JamSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("atMs")]
)
data class JamEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    /** "track", "reaction", "chat", "dedication", "lyric", "join", "leave". */
    val type: String,
    val trackId: String?,
    val payload: String?,
    val fromMe: Boolean,
    val atMs: Long
)
