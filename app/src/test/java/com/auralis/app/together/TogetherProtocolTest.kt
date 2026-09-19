package com.auralis.app.together

import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherProtocolTest {

    private val ref = TrackRef(
        provider = "audius",
        providerId = "abc123",
        title = "A Song",
        artist = "An Artist",
        durationMs = 210_000,
    )

    // --- the property the whole design exists to hold ---

    @Test
    fun `a peer cannot put a stream url on the wire`() {
        val hostile = """
            {"type":"playback","senderId":"m1","serverMs":1,"positionMs":0,"isPlaying":true,
             "speed":1,"mediaUrl":"https://evil.invalid/payload.mp3",
             "track":{"provider":"audius","providerId":"abc123","title":"A Song","artist":"An Artist",
                      "durationMs":1000,"mediaUrl":"https://evil.invalid/payload.mp3",
                      "url":"https://evil.invalid/payload.mp3"}}
        """.trimIndent()

        val message = TogetherProtocol.decodeServerMessage(hostile)
        assertTrue(message is TogetherServerMessage.Playback)

        // There is no field for it to land in, so it is gone by the time anything can read it.
        val track = TogetherProtocol.toTrack((message as TogetherServerMessage.Playback).track)
        assertEquals("", track.mediaUrl)
    }

    @Test
    fun `a rebuilt track always has an empty media url, whatever arrived`() {
        assertEquals("", TogetherProtocol.toTrack(ref).mediaUrl)
        assertEquals("", TogetherProtocol.toTrack(ref.copy(artUrl = "https://cdn.invalid/a.jpg")).mediaUrl)
    }

    @Test
    fun `cover art is only kept when it is https`() {
        assertEquals(
            "https://cdn.invalid/a.jpg",
            TogetherProtocol.toTrack(ref.copy(artUrl = "https://cdn.invalid/a.jpg")).albumArtUrl,
        )
        for (bad in listOf("http://cdn.invalid/a.jpg", "file:///etc/passwd", "javascript:alert(1)")) {
            assertNull(TogetherProtocol.toTrack(ref.copy(artUrl = bad)).albumArtUrl)
        }
    }

    @Test
    fun `art is not carried at all unless it is https`() {
        val track = Track(
            id = "auralis_global_abc",
            title = "t",
            artist = "a",
            albumArtUrl = "http://cdn.invalid/a.jpg",
            mediaUrl = "https://audius.co/x.mp3",
            durationMs = 1,
        )
        assertNull(TogetherProtocol.trackRef(track).artUrl)
    }

    // --- round trips ---

    @Test
    fun `every client message survives a round trip`() {
        val messages = listOf(
            TogetherClientMessage.Ping(at = 12),
            TogetherClientMessage.Playback(track = ref, positionMs = 5, isPlaying = true, speed = 1f),
            TogetherClientMessage.Seek(positionMs = 900),
            TogetherClientMessage.QueueAdd(track = ref),
            TogetherClientMessage.QueueRemove(providerId = "abc123"),
            TogetherClientMessage.Buffering(buffering = true),
            TogetherClientMessage.Chat(ciphertext = "AAECAwQ="),
            TogetherClientMessage.Reaction(emoji = "🔥"),
            TogetherClientMessage.Bye,
        )
        for (message in messages) {
            val json = TogetherProtocol.encode(message)
            val back = TogetherJson.decodeFromString(TogetherClientMessage.serializer(), json)
            assertEquals(message, back)
        }
    }

    @Test
    fun `client messages use the type names the relay validates`() {
        assertTrue(TogetherProtocol.encode(TogetherClientMessage.Bye).contains("\"type\":\"bye\""))
        assertTrue(
            TogetherProtocol.encode(TogetherClientMessage.QueueAdd(ref))
                .contains("\"type\":\"queueAdd\""),
        )
    }

    @Test
    fun `a welcome carries the snapshot a late joiner needs`() {
        val raw = """
            {"type":"welcome","memberId":"m1","hostId":"m0","serverMs":1700,
             "state":{"members":[{"id":"m0","name":"Priya","isHost":true,"buffering":false,
                                  "joinedAtMs":10}],
                      "hostId":"m0",
                      "playback":{"track":{"provider":"jamendo","providerId":"7","title":"T",
                                           "artist":"A","durationMs":1000},
                                  "positionMs":500,"isPlaying":true,"speed":1,"atServerMs":1650},
                      "queue":[],"chat":[]}}
        """.trimIndent()
        val welcome = TogetherProtocol.decodeServerMessage(raw) as TogetherServerMessage.Welcome
        assertEquals("m1", welcome.memberId)
        assertEquals(1, welcome.state.members.size)
        assertEquals(1650L, welcome.state.playback?.atServerMs)
    }

    @Test
    fun `every server message type decodes`() {
        val raws = listOf(
            """{"type":"pong","at":1,"serverMs":2}""",
            """{"type":"presence","members":[],"hostId":"m0","serverMs":2}""",
            """{"type":"seek","senderId":"m0","serverMs":2,"positionMs":9}""",
            """{"type":"queue","serverMs":2,"items":[]}""",
            """{"type":"chat","senderId":"m0","serverMs":2,"ciphertext":"AA=="}""",
            """{"type":"reaction","senderId":"m0","serverMs":2,"emoji":"🔥"}""",
            """{"type":"error","code":"rate_limited","message":"Slow down"}""",
        )
        for (raw in raws) assertTrue(raw, TogetherProtocol.decodeServerMessage(raw) != null)
    }

    @Test
    fun `an unreadable frame returns null rather than dropping the session`() {
        assertNull(TogetherProtocol.decodeServerMessage("{not json"))
        assertNull(TogetherProtocol.decodeServerMessage("""{"type":"exec","cmd":"rm -rf /"}"""))
        assertNull(TogetherProtocol.decodeServerMessage("""{"type":"pong"}"""))
    }

    @Test
    fun `a field a newer relay adds does not break an older app`() {
        val raw = """{"type":"pong","at":1,"serverMs":2,"regionHint":"fra"}"""
        assertEquals(
            TogetherServerMessage.Pong(at = 1, serverMs = 2),
            TogetherProtocol.decodeServerMessage(raw),
        )
    }

    // --- identity across two phones ---

    @Test
    fun `a track keeps its identity through a reference and back`() {
        val ids = mapOf(
            "yt_dQw4w9WgXcQ" to Provider.YOUTUBE,
            "auralis_global_xyz" to Provider.AUDIUS,
            "auralis_songid" to Provider.JIOSAAVN,
            "jamendo_1234" to Provider.JAMENDO,
            "local_42" to Provider.LOCAL,
            "some-imported-id" to Provider.IMPORTED,
        )
        for ((id, provider) in ids) {
            val original = Track(
                id = id,
                title = "Title",
                artist = "Artist",
                albumArtUrl = null,
                mediaUrl = "https://example.invalid/a.mp3",
                durationMs = 1234,
            )
            val rebuilt = TogetherProtocol.toTrack(TogetherProtocol.trackRef(original))
            assertEquals(id, provider, rebuilt.provider)
            assertEquals(id, original.providerId, rebuilt.providerId)
            assertEquals(id, id, rebuilt.id)
        }
    }

    @Test
    fun `the key is what both phones compare tracks by`() {
        assertEquals("audius:abc123", ref.key)
        assertEquals(ref.key, TogetherProtocol.trackRef(TogetherProtocol.toTrack(ref)).key)
    }

    // --- cross-edition ---

    @Test
    fun `a play guest falls back to search for a track it cannot resolve`() {
        val youtube = ref.copy(provider = "youtube", providerId = "dQw4w9WgXcQ")
        assertEquals(TrackAvailability.DIRECT, TogetherProtocol.availability(youtube, hasScrapedSources = true))
        assertEquals(
            TrackAvailability.FALLBACK_SEARCH,
            TogetherProtocol.availability(youtube, hasScrapedSources = false),
        )
    }

    @Test
    fun `both editions resolve the open catalogs directly`() {
        for (provider in listOf("audius", "jamendo")) {
            assertEquals(
                provider,
                TrackAvailability.DIRECT,
                TogetherProtocol.availability(ref.copy(provider = provider), hasScrapedSources = false),
            )
        }
    }

    @Test
    fun `a file on the other phone is searched for, not fetched`() {
        val theirs = ref.copy(provider = "local", providerId = "42")
        assertEquals(
            TrackAvailability.FALLBACK_SEARCH,
            TogetherProtocol.availability(theirs, hasScrapedSources = true),
        )
    }

    @Test
    fun `a reference with nothing to search for says so instead of desyncing silently`() {
        val blank = ref.copy(provider = "local", title = "", artist = "", fallbackQuery = "")
        assertEquals(
            TrackAvailability.UNAVAILABLE,
            TogetherProtocol.availability(blank, hasScrapedSources = false),
        )
    }

    @Test
    fun `the fallback query drops the noise that stops a search matching`() {
        val noisy = Track(
            id = "yt_dQw4w9WgXcQ",
            title = "Midnight Drive (Official Video)",
            artist = "Nova feat. Ash",
            albumArtUrl = null,
            mediaUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            durationMs = 1,
        )
        assertEquals("Midnight Drive Nova", TogetherProtocol.trackRef(noisy).fallbackQuery)
    }
}
