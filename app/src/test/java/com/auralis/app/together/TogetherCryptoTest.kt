package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class TogetherCryptoTest {

    private val code = "ABCDEF"
    private val secret = "23456789ABCDEFGHJKMN"

    @Test
    fun `a message survives a round trip`() {
        val key = TogetherCrypto.deriveKey(code, secret)
        val sealed = TogetherCrypto.seal(key, "are you awake?")
        assertEquals("are you awake?", TogetherCrypto.open(key, sealed))
    }

    @Test
    fun `emoji and long messages survive too`() {
        val key = TogetherCrypto.deriveKey(code, secret)
        for (message in listOf("🔥🌙", "a".repeat(1_000), "", "line\nbreak\ttab")) {
            assertEquals(message, TogetherCrypto.open(key, TogetherCrypto.seal(key, message)))
        }
    }

    /** The reason any of this exists: the relay forwards bytes it has no way to read. */
    @Test
    fun `the ciphertext does not contain the message`() {
        val key = TogetherCrypto.deriveKey(code, secret)
        val sealed = TogetherCrypto.seal(key, "goodnight")
        assertFalse(sealed.contains("goodnight"))
    }

    @Test
    fun `the same message twice does not produce the same ciphertext`() {
        val key = TogetherCrypto.deriveKey(code, secret)
        assertNotEquals(TogetherCrypto.seal(key, "hi"), TogetherCrypto.seal(key, "hi"))
    }

    @Test
    fun `someone with the wrong secret gets nothing, not a crash`() {
        val mine = TogetherCrypto.deriveKey(code, secret)
        val theirs = TogetherCrypto.deriveKey(code, "MNJKHGFEDCBA98765432")
        assertNull(TogetherCrypto.open(theirs, TogetherCrypto.seal(mine, "private")))
    }

    @Test
    fun `two rooms sharing a secret still cannot read each other`() {
        val here = TogetherCrypto.deriveKey("ABCDEF", secret)
        val there = TogetherCrypto.deriveKey("QRSTUV", secret)
        assertNotEquals(here, there)
        assertNull(TogetherCrypto.open(there, TogetherCrypto.seal(here, "private")))
    }

    @Test
    fun `a tampered message is rejected rather than half-decrypted`() {
        val key = TogetherCrypto.deriveKey(code, secret)
        val sealed = TogetherCrypto.seal(key, "meet me at nine")
        val flipped = sealed.toCharArray().also { it[it.size - 2] = if (it[it.size - 2] == 'A') 'B' else 'A' }
        assertNull(TogetherCrypto.open(key, String(flipped)))
    }

    @Test
    fun `junk on the wire returns null rather than ending the session`() {
        val key = TogetherCrypto.deriveKey(code, secret)
        for (junk in listOf("", "not base64 !!", "AAAA", "////")) {
            assertNull(junk, TogetherCrypto.open(key, junk))
        }
    }

    @Test
    fun `the key is deterministic, so both phones derive the same one`() {
        assertEquals(
            TogetherCrypto.deriveKey(code, secret),
            TogetherCrypto.deriveKey(code, secret),
        )
    }

    @Test
    fun `case does not matter, because people retype these`() {
        val typed = TogetherCrypto.deriveKey("abcdef", secret.lowercase())
        val shared = TogetherCrypto.deriveKey(code, secret)
        assertEquals(shared, typed)
    }

    // --- the honest part: how strong is it, really ---

    @Test
    fun `a session opened from a link is keyed from a secret the relay never saw`() {
        assertEquals(EncryptionStrength.LINK_SECRET, TogetherCrypto.deriveKey(code, secret).strength)
    }

    @Test
    fun `a session from a hand-typed code says so, rather than implying it is as strong`() {
        assertEquals(EncryptionStrength.CODE_ONLY, TogetherCrypto.deriveKey(code, null).strength)
        // A malformed secret is not quietly treated as a good one.
        assertEquals(EncryptionStrength.CODE_ONLY, TogetherCrypto.deriveKey(code, "short").strength)
        assertEquals(EncryptionStrength.CODE_ONLY, TogetherCrypto.deriveKey(code, "0".repeat(20)).strength)
    }

    @Test
    fun `the code-only key is still a different key from the link key`() {
        assertNotEquals(TogetherCrypto.deriveKey(code, null), TogetherCrypto.deriveKey(code, secret))
    }

    @Test
    fun `a generated secret is long enough to be worth generating`() {
        val random = SecureRandom()
        val secrets = (1..200).map { TogetherCrypto.generateRoomSecret(random) }
        assertEquals(200, secrets.toSet().size)
        for (generated in secrets) {
            assertTrue(generated, TogetherCrypto.isValidSecret(generated))
            assertEquals(20, generated.length)
        }
    }

    @Test
    fun `a secret uses the alphabet that survives being read aloud`() {
        val generated = TogetherCrypto.generateRoomSecret()
        for (confusable in listOf('0', 'O', '1', 'I', 'L')) {
            assertFalse(generated, generated.contains(confusable))
        }
    }
}
