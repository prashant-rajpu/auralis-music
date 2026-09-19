package com.auralis.app.together

import com.auralis.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TogetherSessionTest {

    private val secret = "23456789ABCDEFGHJKMN"
    private val invite = Invite("ABCDEF", secret)
    private val key = TogetherCrypto.deriveKey("ABCDEF", secret)

    private val theirTrack = TrackRef(
        provider = "audius",
        providerId = "abc",
        title = "A Song",
        artist = "An Artist",
        durationMs = 240_000,
    )

    private class Fixture(
        val transport: FakeTransport = FakeTransport(),
        val player: FakePlayer = FakePlayer(),
        val store: FakeStore = FakeStore(),
        val repository: FakeRepository = FakeRepository(),
        val recorder: FakeRecorder = FakeRecorder(),
        val clock: TestClock = TestClock(),
    )

    private fun TestFixtureScope.session(f: Fixture) = TogetherSession(
        transport = f.transport,
        store = f.store,
        player = f.player,
        repository = f.repository,
        recorder = f.recorder,
        clock = f.clock,
        scope = scope,
    )

    private class TestFixtureScope(val scope: CoroutineScope)

    /** Puts the clock in sync the way a real connect does: a burst of pongs with a fixed offset. */
    private suspend fun TogetherSession.syncClock(f: Fixture, offsetMs: Long = 4_000) {
        repeat(4) {
            val sent = f.clock.nowMs()
            f.clock.advance(20)
            handle(TogetherServerMessage.Pong(at = sent, serverMs = sent + 10 + offsetMs))
        }
    }

    private suspend fun TogetherSession.welcome(members: List<Member> = listOf(me, them)) {
        handle(
            TogetherServerMessage.Welcome(
                memberId = "me",
                hostId = "them",
                serverMs = 1_000,
                state = RoomSnapshot(members = members, hostId = "them"),
            ),
        )
    }

    private val me = Member("me", "Me", isHost = false, buffering = false, joinedAtMs = 1)
    private val them = Member(
        id = "them",
        name = "Priya",
        isHost = true,
        buffering = false,
        joinedAtMs = 0,
        timeZone = "Asia/Kolkata",
    )

    // --- joining ---

    @Test
    fun `hosting mints a room and an invite whose secret the relay never sees`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)

        val invite = session.host("Me").getOrThrow()
        runCurrent()

        assertEquals("ABCDEF", invite.code)
        assertNotNull(invite.secret)
        assertEquals(EncryptionStrength.LINK_SECRET, invite.strength)
        assertEquals("ABCDEF", f.transport.joined?.first)
        // The secret is not in anything the transport was given.
        assertFalse(f.transport.joined.toString().contains(invite.secret!!))
        session.leave()
        runCurrent()
    }

    @Test
    fun `a failure to create a room is reported, not swallowed`() = runTest {
        val f = Fixture()
        f.transport.createResult = Result.failure(java.io.IOException("relay down"))
        val session = TestFixtureScope(backgroundScope).session(f)

        assertTrue(session.host("Me").isFailure)
        runCurrent()
        assertNull(f.transport.joined)
    }

    @Test
    fun `resuming walks back into the last room, honestly weaker`() = runTest {
        val f = Fixture()
        f.store.lastRoom.value = LastRoom("ABCDEF", "member-token-0123456789", "Priya")
        val session = TestFixtureScope(backgroundScope).session(f)

        assertTrue(session.resumeLastRoom("Me"))
        runCurrent()
        assertEquals("ABCDEF", f.transport.joined?.first)
        assertEquals(EncryptionStrength.CODE_ONLY, session.state.value.room?.encryption)
        session.leave()
        runCurrent()
    }

    @Test
    fun `there is nothing to resume when no room was remembered`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        assertFalse(session.resumeLastRoom("Me"))
        runCurrent()
    }

    @Test
    fun `leaving hangs up, forgets the room and puts the speed back`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        f.player.setSpeed(0.98f)

        session.leave()
        runCurrent()

        assertTrue(f.transport.left)
        assertTrue(f.store.forgotten)
        assertNull(session.state.value.room)
    }

    // --- the clock ---

    @Test
    fun `the session pings hard at first so the clock is usable quickly`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        advanceTimeBy(1_100)
        val pings = f.transport.sent.count { it is TogetherClientMessage.Ping }

        assertTrue("only $pings pings in the first second", pings >= 4)
        session.leave()
        runCurrent()
    }

    @Test
    fun `pongs put the room's clock on screen`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.syncClock(f, offsetMs = 4_000)

        val state = session.state.value
        assertTrue(state.clockReady)
        assertEquals(4_000L, state.clockOffsetMs)
        session.leave()
        runCurrent()
    }

    // --- what the room says ---

    @Test
    fun `a welcome seeds who is in the room`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        val room = session.state.value.room!!
        assertEquals("me", room.memberId)
        assertEquals("them", room.hostId)
        assertFalse(room.isHost)
        assertEquals("Priya", room.partner?.name)
        assertEquals("Asia/Kolkata", room.partner?.timeZone)
        session.leave()
        runCurrent()
    }

    @Test
    fun `chat from the snapshot is decrypted, and knows which side sent it`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.handle(
            TogetherServerMessage.Welcome(
                memberId = "me",
                hostId = "them",
                serverMs = 1_000,
                state = RoomSnapshot(
                    members = listOf(me, them),
                    hostId = "them",
                    chat = listOf(
                        ChatEntry("them", 900, TogetherNotes.seal(key, TogetherNote.Text("are you awake?"))),
                        ChatEntry("me", 950, TogetherNotes.seal(key, TogetherNote.Text("always"))),
                    ),
                ),
            ),
        )

        val chat = session.state.value.chat
        assertEquals(
            listOf("are you awake?", "always"),
            chat.map { (it.note as TogetherNote.Text).body },
        )
        assertEquals(listOf(false, true), chat.map { it.isMine })
        assertEquals("Priya", chat.first().senderName)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a message sent with a different invite is shown as unreadable, not hidden`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        val other = TogetherCrypto.deriveKey("ABCDEF", "MNJKHGFEDCBA98765432")
        session.handle(TogetherServerMessage.Chat("them", 1_000, TogetherCrypto.seal(other, "hi")))

        assertNull(session.state.value.chat.last().note)
        session.leave()
        runCurrent()
    }

    @Test
    fun `sending a message puts ciphertext on the wire and nothing else`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.sendChat("meet me at nine")
        runCurrent()

        val chat = f.transport.meaningful.filterIsInstance<TogetherClientMessage.Chat>().single()
        assertFalse(chat.ciphertext.contains("meet me at nine"))
        assertEquals(TogetherNote.Text("meet me at nine"), TogetherNotes.open(key, chat.ciphertext))
        session.leave()
        runCurrent()
    }

    @Test
    fun `an empty message is not sent`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.sendChat("   ")
        runCurrent()

        assertTrue(f.transport.meaningful.isEmpty())
        session.leave()
        runCurrent()
    }

    @Test
    fun `our own reaction coming back is not shown twice`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        val seen = mutableListOf<TogetherReaction>()
        val collector = backgroundScope.launch { session.reactions.collect(seen::add) }
        runCurrent()

        session.handle(TogetherServerMessage.Reaction("me", 1, "🔥"))
        session.handle(TogetherServerMessage.Reaction("them", 2, "🌙"))
        runCurrent()

        assertEquals(listOf("🌙"), seen.map { it.emoji })
        collector.cancel()
        session.leave()
        runCurrent()
    }

    // --- the things that only make sense between two people ---

    @Test
    fun `a knock is a moment, not a line in the transcript`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        val seen = mutableListOf<TogetherKnock>()
        val collector = backgroundScope.launch { session.knocks.collect(seen::add) }
        runCurrent()

        session.handle(
            TogetherServerMessage.Chat("them", 1_000, TogetherNotes.seal(key, TogetherNote.Knock)),
        )
        runCurrent()

        assertEquals(listOf("Priya"), seen.map { it.senderName })
        assertTrue("a knock should not appear as a message", session.state.value.chat.isEmpty())
        collector.cancel()
        session.leave()
        runCurrent()
    }

    @Test
    fun `our own knock does not knock back at us`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        val seen = mutableListOf<TogetherKnock>()
        val collector = backgroundScope.launch { session.knocks.collect(seen::add) }
        runCurrent()

        session.handle(
            TogetherServerMessage.Chat("me", 1_000, TogetherNotes.seal(key, TogetherNote.Knock)),
        )
        runCurrent()

        assertTrue(seen.isEmpty())
        collector.cancel()
        session.leave()
        runCurrent()
    }

    @Test
    fun `a dedication carries a reference and a note, and nothing playable`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.dedicate(track(), "this one is yours")
        runCurrent()

        val sent = f.transport.meaningful.filterIsInstance<TogetherClientMessage.Chat>().single()
        assertFalse(sent.ciphertext.contains("audius.co"))
        val note = TogetherNotes.open(key, sent.ciphertext) as TogetherNote.Dedication
        assertEquals("this one is yours", note.note)
        assertEquals("", TogetherProtocol.toTrack(note.track).mediaUrl)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a lyric moment needs something to be playing`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.sendLyricMoment("and the night goes on", 61_000)
        runCurrent()
        assertTrue(f.transport.meaningful.isEmpty())

        f.player.currentTrack = track()
        session.sendLyricMoment("and the night goes on", 61_000)
        runCurrent()

        val sent = f.transport.meaningful.filterIsInstance<TogetherClientMessage.Chat>().single()
        val note = TogetherNotes.open(key, sent.ciphertext) as TogetherNote.LyricMoment
        assertEquals("and the night goes on", note.line)
        assertEquals(61_000L, note.positionMs)
        session.leave()
        runCurrent()
    }

    /** The point of goodnight: the same instant on both phones, not the same countdown. */
    @Test
    fun `goodnight is scheduled in room time and fires on both sides`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        // Their clock is four seconds ahead of ours; the fade must still land together.
        session.syncClock(f, offsetMs = 4_000)

        session.goodnightIn(delayMs = 60_000)
        runCurrent()

        val at = session.state.value.goodnightAtServerMs
        assertNotNull(at)
        val sent = f.transport.meaningful.filterIsInstance<TogetherClientMessage.Chat>().single()
        assertEquals(TogetherNote.Goodnight(at!!), TogetherNotes.open(key, sent.ciphertext))
        // It is a room timestamp, so it is offset from our own clock by exactly the measured skew.
        assertEquals(f.clock.nowMs() + 60_000 + 4_000, at)
        session.leave()
        runCurrent()
    }

    @Test
    fun `when goodnight arrives, the music stops`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)
        f.player.currentTrack = track()
        f.player.isPlaying = true

        session.goodnightIn(delayMs = 10_000)
        runCurrent()
        session.tick()
        assertTrue("too early to stop", f.player.isPlaying)

        f.clock.advance(11_000)
        session.tick()

        assertFalse(f.player.isPlaying)
        assertNull(session.state.value.goodnightAtServerMs)
        session.leave()
        runCurrent()
    }

    @Test
    fun `their goodnight stops our music too`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)
        f.player.currentTrack = track()
        f.player.isPlaying = true

        val at = f.clock.nowMs() + 5_000
        session.handle(
            TogetherServerMessage.Chat(
                "them",
                1_000,
                TogetherNotes.seal(key, TogetherNote.Goodnight(at)),
            ),
        )
        assertEquals(at, session.state.value.goodnightAtServerMs)
        assertTrue("a goodnight is not a chat message", session.state.value.chat.isEmpty())

        f.clock.advance(6_000)
        session.tick()

        assertFalse(f.player.isPlaying)
        session.leave()
        runCurrent()
    }

    @Test
    fun `goodnight can be called off`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)
        f.player.currentTrack = track()
        f.player.isPlaying = true

        session.goodnightIn(delayMs = 5_000)
        runCurrent()
        session.cancelGoodnight()
        runCurrent()
        f.clock.advance(10_000)
        session.tick()

        assertTrue(f.player.isPlaying)
        session.leave()
        runCurrent()
    }

    @Test
    fun `goodnight needs a clock it can trust`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        session.goodnightIn(delayMs = 5_000)
        runCurrent()

        assertNull(session.state.value.goodnightAtServerMs)
        assertTrue(f.transport.meaningful.isEmpty())
        session.leave()
        runCurrent()
    }

    // --- following what they are playing ---

    @Test
    fun `a peer's track is loaded locally, never from a url they chose`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        session.handle(
            TogetherServerMessage.Playback("them", 5_000, theirTrack, 30_000, true, 1f),
        )

        val loaded = f.player.loaded.single()
        assertEquals("auralis_global_abc", loaded.id)
        assertEquals("", loaded.mediaUrl)
        session.leave()
        runCurrent()
    }

    @Test
    fun `our own playback echoing back does not restart the track`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        session.handle(TogetherServerMessage.Playback("me", 5_000, theirTrack, 30_000, true, 1f))

        assertTrue(f.player.loaded.isEmpty())
        session.leave()
        runCurrent()
    }

    @Test
    fun `a track this edition cannot resolve is searched for instead`() = runTest {
        val f = Fixture()
        f.repository.results = listOf(track(id = "jamendo_77", title = "A Song"))
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        // A file on the other phone: no edition can fetch that, both can look for their own copy.
        val theirs = theirTrack.copy(provider = "local", providerId = "42")
        session.handle(TogetherServerMessage.Playback("them", 5_000, theirs, 0, true, 1f))
        runCurrent()

        assertEquals(listOf("A Song An Artist"), f.repository.queries)
        assertEquals("jamendo_77", f.player.loaded.single().id)
        assertNull(session.state.value.unplayable)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a track nobody can find says so rather than desyncing in silence`() = runTest {
        val f = Fixture()
        f.repository.results = emptyList()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        f.player.isPlaying = true

        val theirs = theirTrack.copy(provider = "local", providerId = "42")
        session.handle(TogetherServerMessage.Playback("them", 5_000, theirs, 0, true, 1f))
        runCurrent()

        assertEquals(theirs, session.state.value.unplayable)
        assertFalse(f.player.isPlaying)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a search that blows up does not take the session with it`() = runTest {
        val f = Fixture()
        f.repository.failWith = java.io.IOException("offline")
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        val theirs = theirTrack.copy(provider = "local", providerId = "42")
        session.handle(TogetherServerMessage.Playback("them", 5_000, theirs, 0, true, 1f))
        runCurrent()

        assertNotNull(session.state.value.unplayable)
        session.leave()
        runCurrent()
    }

    @Test
    fun `both editions resolve an open-catalog track directly`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        session.handle(TogetherServerMessage.Playback("them", 5_000, theirTrack, 0, true, 1f))
        runCurrent()

        assertTrue("edition=${BuildConfig.HAS_SCRAPED_SOURCES}", f.repository.queries.isEmpty())
        assertEquals(1, f.player.loaded.size)
        session.leave()
        runCurrent()
    }

    // --- what gets remembered ---

    @Test
    fun `a room you are sitting in alone is not a session you had together`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome(listOf(me))
        runCurrent()

        assertNull("nothing to remember yet", f.recorder.sessionId)
        session.leave()
        runCurrent()
    }

    @Test
    fun `the timeline opens the moment they arrive`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome(listOf(me))
        runCurrent()

        session.handle(TogetherServerMessage.Presence(listOf(me, them), "them", 1_000))
        runCurrent()

        assertEquals("session-ABCDEF", f.recorder.sessionId)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a track playing when they arrive counts as one you heard together`() = runTest {
        val f = Fixture()
        f.player.currentTrack = track()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        runCurrent()

        assertEquals(listOf("session-ABCDEF" to "auralis_global_abc"), f.recorder.sharedPlays)
        session.leave()
        runCurrent()
    }

    /** Our Songs counts sessions, not seconds, so a long track is not a hundred shared plays. */
    @Test
    fun `a track is filed once per session, however many ticks it survives`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 0, true, 1f))
        runCurrent()
        repeat(10) {
            f.clock.advance(TogetherSession.TICK_MS)
            f.player.positionMs += TogetherSession.TICK_MS
            session.tick()
        }
        runCurrent()

        assertEquals(1, f.recorder.sharedPlays.size)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a dedication lands in the timeline with its note`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        runCurrent()

        session.handle(
            TogetherServerMessage.Chat(
                "them",
                1_000,
                TogetherNotes.seal(key, TogetherNote.Dedication(theirTrack, "this one is yours")),
            ),
        )
        runCurrent()

        assertEquals(
            listOf(Triple("dedication", "this one is yours", false)),
            f.recorder.events,
        )
        session.leave()
        runCurrent()
    }

    @Test
    fun `a sleep timer is plumbing, not a memory`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)
        runCurrent()

        session.handle(
            TogetherServerMessage.Chat(
                "them",
                1_000,
                TogetherNotes.seal(key, TogetherNote.Goodnight(f.clock.nowMs() + 5_000)),
            ),
        )
        runCurrent()

        assertTrue(f.recorder.events.isEmpty())
        session.leave()
        runCurrent()
    }

    @Test
    fun `leaving closes the session rather than leaving it open forever`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        runCurrent()

        session.leave()
        runCurrent()

        assertTrue(f.recorder.ended)
    }

    // --- correcting drift ---

    @Test
    fun `drifting behind them is corrected with a seek`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        // They are at 30 s as of room time = now. We are a second and a half behind.
        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 28_500
        f.player.loaded.clear()
        f.player.seeks.clear()

        session.tick()

        assertEquals(SyncState.CORRECTED, session.state.value.syncState)
        assertEquals(1, f.player.seeks.size)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a small gap is nudged rather than jumped`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_200
        f.player.seeks.clear()

        session.tick()

        assertEquals(SyncState.CATCHING_UP, session.state.value.syncState)
        assertEquals(0.98f, f.player.appliedSpeed, 0f)
        assertTrue(f.player.seeks.isEmpty())
        session.leave()
        runCurrent()
    }

    /** The loop that would otherwise exist: correcting ourselves, then announcing the correction. */
    @Test
    fun `a correction is not broadcast back as if we had chosen it`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 28_000
        f.transport.sent.clear()

        session.tick()
        session.tick()

        assertTrue(
            f.transport.meaningful.toString(),
            f.transport.meaningful.none { it is TogetherClientMessage.Playback },
        )
        session.leave()
        runCurrent()
    }

    @Test
    fun `the user pressing pause on this phone is announced to the room`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_000
        session.tick()

        // Past the quiet window, then the user hits pause.
        f.clock.advance(TogetherSession.BROADCAST_QUIET_MS + 1_000)
        f.transport.sent.clear()
        f.player.isPlaying = false
        session.tick()

        val announced = f.transport.meaningful.filterIsInstance<TogetherClientMessage.Playback>()
        assertEquals(1, announced.size)
        assertFalse(announced.single().isPlaying)
        session.leave()
        runCurrent()
    }

    /** The most infuriating possible bug: you hit pause and the sync loop un-pauses you. */
    @Test
    fun `pausing on this phone stays paused`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_000
        session.tick()

        f.clock.advance(TogetherSession.BROADCAST_QUIET_MS + 1_000)
        f.player.isPlaying = false
        session.tick()

        repeat(4) {
            f.clock.advance(TogetherSession.TICK_MS)
            session.tick()
        }

        assertFalse("the sync loop overrode the user", f.player.isPlaying)
        session.leave()
        runCurrent()
    }

    @Test
    fun `nothing is announced while the room is simply playing on`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        val base = f.clock.nowMs()
        session.handle(TogetherServerMessage.Playback("them", base, theirTrack, 30_000, true, 1f))
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_000
        session.tick()
        f.clock.advance(TogetherSession.BROADCAST_QUIET_MS + 100)
        session.tick()
        f.transport.sent.clear()

        repeat(6) {
            f.clock.advance(TogetherSession.TICK_MS)
            f.player.positionMs += TogetherSession.TICK_MS
            session.tick()
        }

        assertTrue(
            f.transport.meaningful.toString(),
            f.transport.meaningful.none { it is TogetherClientMessage.Playback },
        )
        session.leave()
        runCurrent()
    }

    @Test
    fun `nothing is corrected until the clock is trustworthy`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.positionMs = 10_000
        f.player.seeks.clear()

        session.tick()

        assertTrue(f.player.seeks.isEmpty())
        session.leave()
        runCurrent()
    }

    @Test
    fun `we hold rather than run ahead of a partner who is buffering`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome(listOf(me, them.copy(buffering = true)))
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_000

        session.tick()

        assertEquals(SyncState.WAITING_FOR_PEER, session.state.value.syncState)
        assertFalse(f.player.isPlaying)
        session.leave()
        runCurrent()
    }

    // --- the ways a nudge used to get stranded ---

    /**
     * Two percent slow is inaudible for a second and sounds like a broken app for a whole song.
     * Every path out of the correction loop has to put the speed back.
     */
    @Test
    fun `a nudge is released when the peer moves to another track`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_200
        session.tick()
        assertEquals(0.98f, f.player.appliedSpeed, 0f)

        // They skip. The old code returned here and left us at 0.98x for good.
        f.player.currentTrack = track(id = "jamendo_77")
        session.tick()

        assertEquals(1f, f.player.appliedSpeed, 0f)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a nudge is released when the peer goes quiet`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_200
        session.tick()
        assertEquals(0.98f, f.player.appliedSpeed, 0f)

        f.clock.advance(60 * 60 * 1000)
        session.tick()

        assertEquals(1f, f.player.appliedSpeed, 0f)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a nudge is released when the user takes over`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_200
        session.tick()
        assertEquals(0.98f, f.player.appliedSpeed, 0f)

        f.clock.advance(TogetherSession.BROADCAST_QUIET_MS + 1_000)
        f.player.isPlaying = false
        session.tick()

        assertEquals(1f, f.player.appliedSpeed, 0f)
        session.leave()
        runCurrent()
    }

    @Test
    fun `leaving puts the speed back`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        session.syncClock(f, offsetMs = 0)

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_200
        session.tick()
        assertEquals(0.98f, f.player.appliedSpeed, 0f)

        session.leave()
        runCurrent()

        assertEquals(1f, f.player.appliedSpeed, 0f)
    }

    /** A clock measured over a slow link leans one way, so nudging against it never stops. */
    @Test
    fun `a slow connection is never nudged against`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        // Every exchange takes 600ms, so the offset is only good to a few hundred milliseconds.
        repeat(4) {
            val sent = f.clock.nowMs()
            f.clock.advance(600)
            session.handle(TogetherServerMessage.Pong(at = sent, serverMs = sent + 300))
        }

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 30_200
        f.player.seeks.clear()

        session.tick()

        assertEquals("a 200ms gap is inside the measurement error", 1f, f.player.appliedSpeed, 0f)
        assertTrue(f.player.seeks.isEmpty())
        session.leave()
        runCurrent()
    }

    @Test
    fun `a slow connection still fixes a gap too big to be measurement error`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()
        session.welcome()
        repeat(4) {
            val sent = f.clock.nowMs()
            f.clock.advance(600)
            session.handle(TogetherServerMessage.Pong(at = sent, serverMs = sent + 300))
        }

        session.handle(
            TogetherServerMessage.Playback("them", f.clock.nowMs(), theirTrack, 30_000, true, 1f),
        )
        f.player.currentTrack = track()
        f.player.isPlaying = true
        f.player.positionMs = 45_000
        f.player.seeks.clear()

        session.tick()

        assertEquals(1, f.player.seeks.size)
        assertEquals(1f, f.player.appliedSpeed, 0f)
        session.leave()
        runCurrent()
    }

    // --- the shared queue ---

    @Test
    fun `adding to the shared queue sends a reference, not a url`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.addToSharedQueue(track())
        runCurrent()

        val add = f.transport.meaningful.filterIsInstance<TogetherClientMessage.QueueAdd>().single()
        assertEquals("audius", add.track.provider)
        assertFalse(TogetherProtocol.encode(add).contains("audius.co/stream.mp3"))
        session.leave()
        runCurrent()
    }

    @Test
    fun `the room's queue lands in state for the screen to show`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.handle(
            TogetherServerMessage.Queue(1_000, listOf(QueueEntry(theirTrack, addedBy = "them"))),
        )

        assertEquals(1, session.state.value.queue.size)
        assertEquals("them", session.state.value.queue.single().addedBy)
        session.leave()
        runCurrent()
    }

    @Test
    fun `a relay error is surfaced rather than lost`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        runCurrent()

        session.handle(TogetherServerMessage.Failure("rate_limited", "Slow down"))

        assertEquals("Slow down", session.state.value.message)
        session.leave()
        runCurrent()
    }
}
