package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherInviteTest {

    private val secret = "23456789ABCDEFGHJKMN"

    @Test
    fun `a link round-trips through the share sheet`() {
        val link = TogetherInvite.link("ABCDEF", secret)
        assertEquals("auralis://join/ABCDEF?k=$secret", link)
        assertEquals(Invite("ABCDEF", secret), TogetherInvite.parse(link))
    }

    @Test
    fun `a link without a secret is still a valid invite`() {
        val link = TogetherInvite.link("ABCDEF", null)
        assertEquals("auralis://join/ABCDEF", link)
        assertEquals(Invite("ABCDEF", null), TogetherInvite.parse(link))
    }

    @Test
    fun `a link pasted inside a sentence still resolves`() {
        val pasted = "hey listen with me auralis://join/ABCDEF?k=$secret tonight?"
        assertEquals(Invite("ABCDEF", secret), TogetherInvite.parse(pasted))
    }

    @Test
    fun `an https invite works too, for chat apps that will not link a custom scheme`() {
        assertEquals(
            Invite("ABCDEF", secret),
            TogetherInvite.parse("https://auralis.app/join/ABCDEF?k=$secret"),
        )
    }

    @Test
    fun `six characters read down the phone still joins the room`() {
        val invite = TogetherInvite.parse("abc-def")
        assertEquals("ABCDEF", invite?.code)
        assertNull(invite?.secret)
        assertEquals(EncryptionStrength.CODE_ONLY, invite?.strength)
    }

    @Test
    fun `a link invite is the strong one`() {
        assertEquals(
            EncryptionStrength.LINK_SECRET,
            TogetherInvite.parse(TogetherInvite.link("ABCDEF", secret))!!.strength,
        )
    }

    @Test
    fun `a mangled secret is dropped rather than used as if it were real`() {
        val invite = TogetherInvite.parse("auralis://join/ABCDEF?k=TRUNCATED")
        assertEquals("ABCDEF", invite?.code)
        assertNull(invite?.secret)
    }

    @Test
    fun `nonsense is not an invite`() {
        for (bad in listOf("", "   ", "hello", "auralis://join/ABC", "auralis://playlist/xyz")) {
            assertNull(bad, TogetherInvite.parse(bad))
        }
    }

    @Test
    fun `a code with a character the relay would refuse is not an invite`() {
        assertNull(TogetherInvite.parse("auralis://join/ABCDE0"))
        assertNull(TogetherInvite.parse("ABCDE0"))
    }

    @Test
    fun `the share text says who is asking and carries both ways in`() {
        val text = TogetherInvite.shareText("ABCDEF", secret, "Priya")
        assertTrue(text.contains("Priya"))
        assertTrue(text.contains("ABCDEF"))
        assertTrue(text.contains("auralis://join/ABCDEF?k=$secret"))
    }

    @Test
    fun `an unnamed host still gets a sentence that makes sense`() {
        assertTrue(TogetherInvite.shareText("ABCDEF", null, "  ").startsWith("Someone wants"))
    }
}
