package com.auralis.app.together

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How many days in a row the two of you have listened together.
 *
 * Computed in Kotlin rather than SQL on purpose. A streak is counted in *days*, a day depends on a
 * time zone, and the two people this is for are by definition in different ones — so the boundary
 * has to be chosen explicitly rather than inherited from whatever SQLite's `date()` assumes. The
 * device's own zone is the right default: it is the streak as the person holding this phone
 * experiences it.
 *
 * Pure, so a test can walk a year of listening in a millisecond.
 */
data class Streak(
    val days: Int,
    /** True when today already counts, so the UI can stop nagging. */
    val listenedToday: Boolean,
) {
    /** A streak that will break at midnight unless something is played. */
    val atRisk: Boolean get() = days > 0 && !listenedToday
}

object ListeningStreak {

    /**
     * [timestamps] is every shared play, in any order. Only the distinct local days matter.
     *
     * A streak survives today being empty — until midnight, today is still winnable — so a run
     * ending yesterday counts, and [Streak.atRisk] is how the UI knows to say something.
     */
    fun from(
        timestamps: List<Long>,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Streak {
        if (timestamps.isEmpty()) return Streak(days = 0, listenedToday = false)

        val today = now.atZone(zone).toLocalDate()
        val days = timestamps
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .filterNot { it.isAfter(today) }
            .toSortedSet()
            .toList()
            .asReversed()

        if (days.isEmpty()) return Streak(days = 0, listenedToday = false)

        val listenedToday = days.first() == today
        val mostRecent = days.first()

        // A gap of more than one day between now and the last shared play ends it.
        if (ChronoUnit.DAYS.between(mostRecent, today) > 1) {
            return Streak(days = 0, listenedToday = false)
        }

        var length = 1
        var previous: LocalDate = mostRecent
        for (day in days.drop(1)) {
            if (ChronoUnit.DAYS.between(day, previous) != 1L) break
            length++
            previous = day
        }

        return Streak(days = length, listenedToday = listenedToday)
    }

    /** What the card says. Short, and never congratulatory about a streak of one. */
    fun label(streak: Streak): String = when {
        streak.days == 0 -> "No streak yet"
        streak.days == 1 && streak.listenedToday -> "Listening together today"
        streak.days == 1 -> "You listened together yesterday"
        streak.atRisk -> "${streak.days} days — keep it going today"
        else -> "${streak.days} days in a row"
    }
}
