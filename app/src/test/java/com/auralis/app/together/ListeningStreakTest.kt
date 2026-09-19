package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ListeningStreakTest {

    private val zone = ZoneId.of("Europe/London")
    private val now = Instant.parse("2026-06-15T20:00:00Z") // Monday evening in London

    private fun daysAgo(n: Int, hour: Int = 20): Long =
        now.atZone(zone).minusDays(n.toLong()).withHour(hour).toInstant().toEpochMilli()

    @Test
    fun `nothing played is not a streak`() {
        val streak = ListeningStreak.from(emptyList(), zone, now)
        assertEquals(0, streak.days)
        assertFalse(streak.listenedToday)
        assertFalse(streak.atRisk)
    }

    @Test
    fun `listening today alone is a streak of one`() {
        val streak = ListeningStreak.from(listOf(daysAgo(0)), zone, now)
        assertEquals(1, streak.days)
        assertTrue(streak.listenedToday)
        assertFalse(streak.atRisk)
    }

    @Test
    fun `consecutive days count once each, however many plays`() {
        val timestamps = listOf(
            daysAgo(0, hour = 9), daysAgo(0, hour = 20), daysAgo(0, hour = 22),
            daysAgo(1), daysAgo(2), daysAgo(3),
        )
        assertEquals(4, ListeningStreak.from(timestamps, zone, now).days)
    }

    /** Until midnight, today is still winnable, so a run ending yesterday is not over yet. */
    @Test
    fun `a streak that has not been fed today is still alive, and says so`() {
        val streak = ListeningStreak.from(listOf(daysAgo(1), daysAgo(2), daysAgo(3)), zone, now)
        assertEquals(3, streak.days)
        assertFalse(streak.listenedToday)
        assertTrue(streak.atRisk)
    }

    @Test
    fun `a missed day ends it`() {
        val streak = ListeningStreak.from(listOf(daysAgo(2), daysAgo(3), daysAgo(4)), zone, now)
        assertEquals(0, streak.days)
    }

    @Test
    fun `a gap in the middle only counts the recent run`() {
        val timestamps = listOf(daysAgo(0), daysAgo(1), daysAgo(5), daysAgo(6), daysAgo(7))
        assertEquals(2, ListeningStreak.from(timestamps, zone, now).days)
    }

    @Test
    fun `order does not matter, because the database does not promise one`() {
        val forwards = listOf(daysAgo(2), daysAgo(1), daysAgo(0))
        val shuffled = listOf(daysAgo(1), daysAgo(0), daysAgo(2))
        assertEquals(
            ListeningStreak.from(forwards, zone, now),
            ListeningStreak.from(shuffled, zone, now),
        )
    }

    /**
     * The whole reason this is Kotlin and not SQL: a day is a day *somewhere*. The same play is
     * yesterday in London and today in Auckland.
     */
    @Test
    fun `the day boundary follows the zone it is asked for`() {
        val lateInLondon = Instant.parse("2026-06-15T23:30:00Z")
        val auckland = ZoneId.of("Pacific/Auckland")

        val inLondon = ListeningStreak.from(listOf(lateInLondon.toEpochMilli()), zone, now = Instant.parse("2026-06-16T08:00:00Z"))
        val inAuckland = ListeningStreak.from(listOf(lateInLondon.toEpochMilli()), auckland, now = Instant.parse("2026-06-16T08:00:00Z"))

        // 23:30 UTC on the 15th is the 16th in Auckland but still the 16th 00:30 in London (BST).
        assertTrue(inLondon.days > 0)
        assertTrue(inAuckland.listenedToday)
    }

    @Test
    fun `a timestamp from the future does not inflate the count`() {
        val tomorrow = now.atZone(zone).plusDays(1).toInstant().toEpochMilli()
        val streak = ListeningStreak.from(listOf(tomorrow, daysAgo(0)), zone, now)
        assertEquals(1, streak.days)
    }

    @Test
    fun `the label never congratulates a streak of one as though it were a run`() {
        assertEquals("No streak yet", ListeningStreak.label(Streak(0, false)))
        assertEquals("Listening together today", ListeningStreak.label(Streak(1, true)))
        assertEquals("You listened together yesterday", ListeningStreak.label(Streak(1, false)))
        assertEquals("5 days — keep it going today", ListeningStreak.label(Streak(5, false)))
        assertEquals("5 days in a row", ListeningStreak.label(Streak(5, true)))
    }
}
