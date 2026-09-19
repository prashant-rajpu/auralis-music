package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEndpointsTest {

    private val base = "https://relay.example.com"

    @Test
    fun `the create route is where the worker serves it`() {
        assertEquals(
            "https://relay.example.com/rooms",
            RelayEndpoints.createRoom(base).toString(),
        )
    }

    @Test
    fun `a trailing slash in a hand-typed relay address is harmless`() {
        assertEquals(
            "https://relay.example.com/rooms",
            RelayEndpoints.createRoom("https://relay.example.com/  ".trim() + "/").toString(),
        )
    }

    @Test
    fun `the socket route carries the token and name as query parameters`() {
        val url = RelayEndpoints.socket(base, "ABCDEF", "tok-123456789012345", "Priya")
        assertNotNull(url)
        assertEquals("/rooms/ABCDEF/ws", url!!.encodedPath)
        assertEquals("tok-123456789012345", url.queryParameter("token"))
        assertEquals("Priya", url.queryParameter("name"))
    }

    @Test
    fun `the socket carries our time zone, so they can see what time it is here`() {
        val url = RelayEndpoints.socket(base, "ABCDEF", "tok-123456789012345", "Me", "Asia/Kolkata")!!
        assertEquals("Asia/Kolkata", url.queryParameter("tz"))
    }

    @Test
    fun `a name with a space or an ampersand does not break the query`() {
        val url = RelayEndpoints.socket(base, "ABCDEF", "tok-123456789012345", "Sam & Alex")!!
        assertEquals("Sam & Alex", url.queryParameter("name"))
        assertFalse(url.toString().contains("Sam & Alex"))
    }

    @Test
    fun `a lowercase code is upper-cased, because the relay addresses rooms by the exact code`() {
        val url = RelayEndpoints.socket(base, "abcdef", "tok-123456789012345", "x")!!
        assertEquals("/rooms/ABCDEF/ws", url.encodedPath)
    }

    @Test
    fun `a code the relay would refuse never leaves the phone`() {
        for (bad in listOf("ABC", "ABCDEFG", "ABCDE0", "ABCDEI", "")) {
            assertNull(bad, RelayEndpoints.socket(base, bad, "tok-123456789012345", "x"))
        }
    }

    @Test
    fun `the code alphabet has no character that can be misread aloud`() {
        for (confusable in listOf('0', 'O', '1', 'I', 'L')) {
            assertFalse("$confusable", RelayEndpoints.CODE_ALPHABET.contains(confusable))
        }
    }

    @Test
    fun `typing a code forgivingly still produces one the relay accepts`() {
        assertEquals("ABCDEF", RelayEndpoints.normalizeCode("abc-def"))
        assertEquals("ABCDEF", RelayEndpoints.normalizeCode("ABCDEFGH"))
        assertTrue(RelayEndpoints.isValidCode(RelayEndpoints.normalizeCode("a b c d e f")))
    }

    @Test
    fun `a plaintext relay address is refused unless it is a local dev server`() {
        assertNull(RelayEndpoints.createRoom("http://relay.example.com"))
        assertNotNull(RelayEndpoints.createRoom("http://localhost:8787"))
        assertNotNull(RelayEndpoints.createRoom("http://127.0.0.1:8787"))
    }

    @Test
    fun `nonsense in the relay setting fails here rather than as a mystery socket error`() {
        for (bad in listOf("", "   ", "not a url", "ftp://relay.example.com", "relay.example.com")) {
            assertNull(bad, RelayEndpoints.createRoom(bad))
        }
    }
}
