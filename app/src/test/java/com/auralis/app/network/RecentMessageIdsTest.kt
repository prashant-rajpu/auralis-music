package com.auralis.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentMessageIdsTest {

    @Test
    fun markSeen_isTrueOnlyTheFirstTime() {
        val ids = RecentMessageIds(capacity = 10)
        assertTrue(ids.markSeen("a"))
        assertFalse("Same message arriving on a second transport must be ignored", ids.markSeen("a"))
        assertTrue(ids.markSeen("b"))
    }

    @Test
    fun markSeen_forgetsTheOldestBeyondCapacity() {
        val ids = RecentMessageIds(capacity = 3)
        assertTrue(ids.markSeen("a"))
        assertTrue(ids.markSeen("b"))
        assertTrue(ids.markSeen("c"))

        assertTrue(ids.markSeen("d")) // evicts a
        assertTrue(ids.markSeen("a")) // a is new again, evicts b
        assertFalse(ids.markSeen("c"))
        assertFalse(ids.markSeen("d"))
        assertTrue(ids.markSeen("b"))
    }
}
