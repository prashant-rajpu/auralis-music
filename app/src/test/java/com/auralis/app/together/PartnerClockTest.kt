package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PartnerClockTest {

    private val london = ZoneId.of("Europe/London")
    private val kolkata = "Asia/Kolkata"
    private val dubai = ZoneId.of("Asia/Dubai")

    /** 2026-06-15T20:00:00Z — a summer evening in London, so BST is in force. */
    private val summerEvening = Instant.parse("2026-06-15T20:00:00Z")

    @Test
    fun `it says what time it is where they are`() {
        // 20:00 UTC is 01:30 the next morning in Kolkata.
        assertEquals("1:30 AM", PartnerClock.timeFor(kolkata, summerEvening)?.uppercase())
    }

    @Test
    fun `an older client that never sent a zone gets nothing rather than a wrong time`() {
        assertNull(PartnerClock.timeFor("", summerEvening))
        assertNull(PartnerClock.timeFor("Middle/Earth", summerEvening))
        assertNull(PartnerClock.offsetLabel("", london, summerEvening))
        assertNull(PartnerClock.minutesFrom("nonsense", london, summerEvening))
    }

    @Test
    fun `the offset is worked out from the date, not from a table`() {
        // In June, London is UTC+1 and Kolkata UTC+5:30, so 4 hours 30.
        assertEquals(270, PartnerClock.minutesFrom(kolkata, london, summerEvening))
        // In January, London is UTC+0, so it is 5 hours 30.
        val winter = Instant.parse("2026-01-15T20:00:00Z")
        assertEquals(330, PartnerClock.minutesFrom(kolkata, london, winter))
    }

    @Test
    fun `a half-hour zone keeps its half hour`() {
        // The pair this was built for: Dubai is UTC+4 all year, Kolkata UTC+5:30. An hour and a
        // half, not an hour — counting in whole hours threw the 30 minutes away.
        assertEquals(90, PartnerClock.minutesFrom(kolkata, dubai, summerEvening))
        assertEquals(
            "1 hour 30 minutes ahead",
            PartnerClock.offsetLabel(kolkata, dubai, summerEvening),
        )
        assertEquals(
            "1 hour 30 minutes behind",
            PartnerClock.offsetLabel("Asia/Dubai", ZoneId.of(kolkata), summerEvening),
        )
        // Kolkata sits 4:30 from London in summer, and the label has to say the 30.
        assertEquals(
            "4 hours 30 minutes ahead",
            PartnerClock.offsetLabel(kolkata, london, summerEvening),
        )
    }

    @Test
    fun `a quarter-hour zone keeps its quarter hour`() {
        // Nepal is UTC+5:45 — 15 minutes past Kolkata, which is the tightest offset in use.
        assertEquals(15, PartnerClock.minutesFrom("Asia/Kathmandu", ZoneId.of(kolkata), summerEvening))
        assertEquals(
            "15 minutes ahead",
            PartnerClock.offsetLabel("Asia/Kathmandu", ZoneId.of(kolkata), summerEvening),
        )
    }

    @Test
    fun `the label reads like a person wrote it`() {
        // London is on BST in June, so Dubai is three hours up rather than four.
        assertEquals("3 hours ahead", PartnerClock.offsetLabel("Asia/Dubai", london, summerEvening))
        assertEquals(
            "3 hours behind",
            PartnerClock.offsetLabel("Europe/London", dubai, summerEvening),
        )
        assertEquals(
            "An hour ahead",
            PartnerClock.offsetLabel("Europe/Paris", london, summerEvening),
        )
        assertEquals(
            "An hour behind",
            PartnerClock.offsetLabel("Europe/London", ZoneId.of("Europe/Paris"), summerEvening),
        )
    }

    @Test
    fun `two zones that agree today say so`() {
        assertEquals(
            "Same time as you",
            PartnerClock.offsetLabel("Europe/Dublin", london, summerEvening),
        )
    }

    @Test
    fun `it knows when it is the middle of the night for them`() {
        // 01:30 in Kolkata.
        assertTrue(PartnerClock.isLateThere(kolkata, summerEvening))
        // 21:00 in London.
        assertFalse(PartnerClock.isLateThere("Europe/London", summerEvening))
    }

    @Test
    fun `late covers the whole night, not just after midnight`() {
        val elevenPmUtc = Instant.parse("2026-01-15T23:30:00Z")
        assertTrue(PartnerClock.isLateThere("UTC", elevenPmUtc))
        val sevenAmUtc = Instant.parse("2026-01-15T07:00:00Z")
        assertFalse(PartnerClock.isLateThere("UTC", sevenAmUtc))
        val sixFiftyNine = Instant.parse("2026-01-15T06:59:00Z")
        assertTrue(PartnerClock.isLateThere("UTC", sixFiftyNine))
    }

    @Test
    fun `it notices when it is not even the same day there`() {
        assertTrue(PartnerClock.isDifferentDay(kolkata, london, summerEvening))
        assertFalse(PartnerClock.isDifferentDay("Europe/Dublin", london, summerEvening))
        assertEquals("Tuesday", PartnerClock.dayFor(kolkata, summerEvening))
    }

    @Test
    fun `an unknown zone is never treated as late or as a different day`() {
        assertFalse(PartnerClock.isLateThere("", summerEvening))
        assertFalse(PartnerClock.isDifferentDay("", london, summerEvening))
        assertNull(PartnerClock.dayFor("", summerEvening))
    }
}
