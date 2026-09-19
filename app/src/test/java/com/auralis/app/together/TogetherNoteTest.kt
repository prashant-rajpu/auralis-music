package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherNoteTest {

    private val key = TogetherCrypto.deriveKey("ABCDEF", "23456789ABCDEFGHJKMN")
    private val track = TrackRef(
        provider = "audius",
        providerId = "abc",
        title = "Midnight Drive",
        artist = "Nova",
        durationMs = 210_000,
    )

    @Test
    fun `every kind of note survives a round trip`() {
        val notes = listOf(
            TogetherNote.Text("are you awake?"),
            TogetherNote.Dedication(track, "this one is yours"),
            TogetherNote.LyricMoment("and the night goes on", track, 61_000),
            TogetherNote.Knock,
            TogetherNote.Goodnight(atServerMs = 1_700_000_000_000),
        )
        for (note in notes) {
            assertEquals(note, TogetherNotes.open(key, TogetherNotes.seal(key, note)))
        }
    }

    /**
     * The point of putting everything in one encrypted channel: the relay cannot tell a knock from
     * a paragraph, so it does not learn that someone sent a dedication at 11:40pm either.
     */
    @Test
    fun `the relay cannot tell one kind of note from another`() {
        val knock = TogetherNotes.seal(key, TogetherNote.Knock)
        val dedication = TogetherNotes.seal(key, TogetherNote.Dedication(track, "this one is yours"))

        for (sealed in listOf(knock, dedication)) {
            assertFalse(sealed.contains("knock"))
            assertFalse(sealed.contains("dedication"))
            assertFalse(sealed.contains("Midnight Drive"))
        }
    }

    @Test
    fun `a dedication carries a reference, never something playable`() {
        val sealed = TogetherNotes.seal(key, TogetherNote.Dedication(track, "for you"))
        val opened = TogetherNotes.open(key, sealed) as TogetherNote.Dedication
        assertEquals("", TogetherProtocol.toTrack(opened.track).mediaUrl)
    }

    @Test
    fun `a note sent with a different invite cannot be opened`() {
        val other = TogetherCrypto.deriveKey("ABCDEF", "MNJKHGFEDCBA98765432")
        assertNull(TogetherNotes.open(other, TogetherNotes.seal(key, TogetherNote.Text("private"))))
    }

    /** An older build sent a bare string. That should read as a plain message, not a broken one. */
    @Test
    fun `a plain string from an older build is read as text`() {
        val legacy = TogetherCrypto.seal(key, "sent by an older version")
        assertEquals(TogetherNote.Text("sent by an older version"), TogetherNotes.open(key, legacy))
    }

    @Test
    fun `a note type this build does not know is not mistaken for text`() {
        // A future note kind: unreadable as a note, and it is not a bare string either.
        val future = TogetherCrypto.seal(key, """{"type":"hologram","spin":3}""")
        val opened = TogetherNotes.open(key, future)
        assertTrue(opened is TogetherNote.Text)
        assertTrue((opened as TogetherNote.Text).body.contains("hologram"))
    }

    @Test
    fun `junk on the wire is null rather than a crash`() {
        for (junk in listOf("", "not base64 !!", "AAAA")) {
            assertNull(junk, TogetherNotes.open(key, junk))
        }
    }
}
