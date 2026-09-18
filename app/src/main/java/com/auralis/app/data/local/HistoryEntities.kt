package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per play. Nothing reads this yet — v4.3's affinity scoring does — but it lands now
 * because a recommender can only learn from history that was already being recorded. Starting to
 * collect on the day the feature ships would mean shipping it empty.
 *
 * [hourOfDay] and [dayOfWeek] are denormalised from [startedAtMs] so time-of-day affinity is a
 * GROUP BY rather than a full-table scan with date maths in Kotlin.
 */
@Entity(
    tableName = "play_history",
    indices = [Index("trackId"), Index("startedAtMs"), Index("sessionId")]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val startedAtMs: Long,
    val playedMs: Long,
    /** 0f–1f of the track's duration actually heard. */
    val completionRatio: Float,
    val skipped: Boolean,
    /** What started this play: "queue", "shelf:trending", "radio", "jam", "search"… */
    val context: String,
    /** Set when the play began under a mood chip, so moods can learn what you pick under them. */
    val moodLabel: String?,
    /** Groups plays into one listening session; also the join key to [SharedPlayEntity]. */
    val sessionId: String?,
    val hourOfDay: Int,
    val dayOfWeek: Int
)

/**
 * A play that happened while a Together session was live, and who was in the room for it.
 *
 * This is what makes "Our Songs" and the shared streak possible: a track is *ours* because we were
 * both there when it played, which plain [PlayHistoryEntity] cannot express.
 */
@Entity(
    tableName = "shared_plays",
    indices = [Index("trackId"), Index("jamSessionId"), Index("playedAtMs")]
)
data class SharedPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val jamSessionId: String,
    /** Display name of the other participant at the time, kept so history survives a rename. */
    val partnerName: String?,
    val playedAtMs: Long,
    /** True when this device added the track, false when the partner did. */
    val addedByMe: Boolean
)
