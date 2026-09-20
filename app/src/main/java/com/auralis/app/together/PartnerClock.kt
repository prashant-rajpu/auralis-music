package com.auralis.app.together

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * What time it is where they are.
 *
 * Small, but it is most of the difference between a party feature and one built for two people in
 * different time zones. Knowing it is 2am for them changes whether you start a session at all —
 * and the app can say so instead of leaving you to work out the offset in your head.
 *
 * Pure, and takes the instant, so a test can visit every hour of the day without waiting.
 */
object PartnerClock {

    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    /** Null when their phone never told us, which is what an older client looks like. */
    fun timeFor(timeZoneId: String, now: Instant = Instant.now()): String? {
        val zone = zoneOrNull(timeZoneId) ?: return null
        return CLOCK.format(now.atZone(zone))
    }

    /**
     * How many minutes ahead or behind they are. Zero when the zones agree today — which is not the
     * same as the same zone, and is the number that actually matters.
     *
     * Minutes rather than hours because plenty of the world is not on a whole hour: India is
     * UTC+5:30, Nepal UTC+5:45, and parts of Australia UTC+9:30. Rounding those down loses the half
     * hour entirely and tells you it is 1am there when it is half past.
     */
    fun minutesFrom(theirZoneId: String, mine: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): Int? {
        val theirs = zoneOrNull(theirZoneId) ?: return null
        val theirOffset = theirs.rules.getOffset(now).totalSeconds
        val myOffset = mine.rules.getOffset(now).totalSeconds
        return (theirOffset - myOffset) / 60
    }

    /** "3 hours ahead", "1 hour 30 minutes behind", "same time as you". */
    fun offsetLabel(theirZoneId: String, mine: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String? {
        val minutes = minutesFrom(theirZoneId, mine, now) ?: return null
        if (minutes == 0) return "Same time as you"
        val direction = if (minutes > 0) "ahead" else "behind"
        return "${spellOut(abs(minutes))} $direction"
    }

    /** "An hour", "4 hours", "1 hour 30 minutes", "45 minutes". */
    private fun spellOut(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$rest minutes"
            rest == 0 && hours == 1 -> "An hour"
            rest == 0 -> "$hours hours"
            hours == 1 -> "1 hour $rest minutes"
            else -> "$hours hours $rest minutes"
        }
    }

    /** Between 11pm and 7am where they are: worth a gentler prompt than "start a session". */
    fun isLateThere(timeZoneId: String, now: Instant = Instant.now()): Boolean {
        val zone = zoneOrNull(timeZoneId) ?: return false
        val hour = now.atZone(zone).hour
        return hour >= 23 || hour < 7
    }

    /** "Tuesday" where they are, for when it is not even the same day. */
    fun dayFor(timeZoneId: String, now: Instant = Instant.now()): String? {
        val zone = zoneOrNull(timeZoneId) ?: return null
        return now.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    fun isDifferentDay(
        theirZoneId: String,
        mine: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Boolean {
        val theirs = zoneOrNull(theirZoneId) ?: return false
        return now.atZone(theirs).toLocalDate() != now.atZone(mine).toLocalDate()
    }

    private fun zoneOrNull(id: String): ZoneId? =
        if (id.isBlank()) null else runCatching { ZoneId.of(id) }.getOrNull()

    /** Exposed for the goodnight-mode copy, which needs to know what "late" means here too. */
    val LATE_START: LocalTime = LocalTime.of(23, 0)
    val LATE_END: LocalTime = LocalTime.of(7, 0)
}
