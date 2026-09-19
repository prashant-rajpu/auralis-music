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
        val clock: TestClock = TestClock(),
    )

    private fun TestFixtureScope.session(f: Fixture) = TogetherSession(
        transport = f.transport,
        store = f.store,
        player = f.player,
        repository = f.repository,
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
    private val them = Member("them", "Priya", isHost = true, buffering = false, joinedAtMs = 0)

    // --- joining ---

    @Test
    fun `hosting mints a room and an invite whose secret the relay never sees`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)

        val invite = session.host("Me").getOrThrow()

        assertEquals("ABCDEF", invite.code)
        assertNotNull(invite.secret)
        assertEquals(EncryptionStrength.LINK_SECRET, invite.strength)
        assertEquals("ABCDEF", f.transport.joined?.first)
        // The secret is not in anything the transport was given.
        assertFalse(f.transport.joined.toString().contains(invite.secret!!))
        session.leave()
    }

    @Test
    fun `a failure to create a room is reported, not swallowed`() = runTest {
        val f = Fixture()
        f.transport.createResult = Result.failure(java.io.IOException("relay down"))
        val session = TestFixtureScope(backgroundScope).session(f)

        assertTrue(session.host("Me").isFailure)
        assertNull(f.transport.joined)
    }

    @Test
    fun `resuming walks back into the last room, honestly weaker`() = runTest {
        val f = Fixture()
        f.store.lastRoom.value = LastRoom("ABCDEF", "member-token-0123456789", "Priya")
        val session = TestFixtureScope(backgroundScope).session(f)

        assertTrue(session.resumeLastRoom("Me"))
        assertEquals("ABCDEF", f.transport.joined?.first)
        assertEquals(EncryptionStrength.CODE_ONLY, session.state.value.room?.encryption)
        session.leave()
    }

    @Test
    fun `there is nothing to resume when no room was remembered`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        assertFalse(session.resumeLastRoom("Me"))
    }

    @Test
    fun `leaving hangs up, forgets the room and puts the speed back`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        f.player.setSpeed(0.98f)

        session.leave()

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

        advanceTimeBy(1_100)
        val pings = f.transport.sent.count { it is TogetherClientMessage.Ping }

        assertTrue("only $pings pings in the first second", pings >= 4)
        session.leave()
    }

    @Test
    fun `pongs put the room's clock on screen`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.syncClock(f, offsetMs = 4_000)

        val state = session.state.value
        assertTrue(state.clockReady)
        assertEquals(4_000L, state.clockOffsetMs)
        session.leave()
    }

    // --- what the room says ---

    @Test
    fun `a welcome seeds who is in the room`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()

        val room = session.state.value.room!!
        assertEquals("me", room.memberId)
        assertEquals("them", room.hostId)
        assertFalse(room.isHost)
        assertEquals("Priya", room.partner?.name)
        session.leave()
    }

    @Test
    fun `chat from the snapshot is decrypted, and knows which side sent it`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")

        session.handle(
            TogetherServerMessage.Welcome(
                memberId = "me",
                hostId = "them",
                serverMs = 1_000,
                state = RoomSnapshot(
                    members = listOf(me, them),
                    hostId = "them",
                    chat = listOf(
                        ChatEntry("them", 900, TogetherCrypto.seal(key, "are you awake?")),
                        ChatEntry("me", 950, TogetherCrypto.seal(key, "always")),
                    ),
                ),
            ),
        )

        val chat = session.state.value.chat
        assertEquals(listOf("are you awake?", "always"), chat.map { it.text })
        assertEquals(listOf(false, true), chat.map { it.isMine })
        assertEquals("Priya", chat.first().senderName)
        session.leave()
    }

    @Test
    fun `a message sent with a different invite is shown as unreadable, not hidden`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()

        val other = TogetherCrypto.deriveKey("ABCDEF", "MNJKHGFEDCBA98765432")
        session.handle(TogetherServerMessage.Chat("them", 1_000, TogetherCrypto.seal(other, "hi")))

        assertEquals(TogetherSession.UNREADABLE, session.state.value.chat.last().text)
        session.leave()
    }

    @Test
    fun `sending a message puts ciphertext on the wire and nothing else`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")

        session.sendChat("meet me at nine")

        val chat = f.transport.meaningful.filterIsInstance<TogetherClientMessage.Chat>().single()
        assertFalse(chat.ciphertext.contains("meet me at nine"))
        assertEquals("meet me at nine", TogetherCrypto.open(key, chat.ciphertext))
        session.leave()
    }

    @Test
    fun `an empty message is not sent`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")

        session.sendChat("   ")

        assertTrue(f.transport.meaningful.isEmpty())
        session.leave()
    }

    @Test
    fun `our own reaction coming back is not shown twice`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    // --- following what they are playing ---

    @Test
    fun `a peer's track is loaded locally, never from a url they chose`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()

        session.handle(
            TogetherServerMessage.Playback("them", 5_000, theirTrack, 30_000, true, 1f),
        )

        val loaded = f.player.loaded.single()
        assertEquals("auralis_global_abc", loaded.id)
        assertEquals("", loaded.mediaUrl)
        session.leave()
    }

    @Test
    fun `our own playback echoing back does not restart the track`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()

        session.handle(TogetherServerMessage.Playback("me", 5_000, theirTrack, 30_000, true, 1f))

        assertTrue(f.player.loaded.isEmpty())
        session.leave()
    }

    @Test
    fun `a track this edition cannot resolve is searched for instead`() = runTest {
        val f = Fixture()
        f.repository.results = listOf(track(id = "jamendo_77", title = "A Song"))
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()

        // A file on the other phone: no edition can fetch that, both can look for their own copy.
        val theirs = theirTrack.copy(provider = "local", providerId = "42")
        session.handle(TogetherServerMessage.Playback("them", 5_000, theirs, 0, true, 1f))
        runCurrent()

        assertEquals(listOf("A Song An Artist"), f.repository.queries)
        assertEquals("jamendo_77", f.player.loaded.single().id)
        assertNull(session.state.value.unplayable)
        session.leave()
    }

    @Test
    fun `a track nobody can find says so rather than desyncing in silence`() = runTest {
        val f = Fixture()
        f.repository.results = emptyList()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()
        f.player.isPlaying = true

        val theirs = theirTrack.copy(provider = "local", providerId = "42")
        session.handle(TogetherServerMessage.Playback("them", 5_000, theirs, 0, true, 1f))
        runCurrent()

        assertEquals(theirs, session.state.value.unplayable)
        assertFalse(f.player.isPlaying)
        session.leave()
    }

    @Test
    fun `a search that blows up does not take the session with it`() = runTest {
        val f = Fixture()
        f.repository.failWith = java.io.IOException("offline")
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()

        val theirs = theirTrack.copy(provider = "local", providerId = "42")
        session.handle(TogetherServerMessage.Playback("them", 5_000, theirs, 0, true, 1f))
        runCurrent()

        assertNotNull(session.state.value.unplayable)
        session.leave()
    }

    @Test
    fun `both editions resolve an open-catalog track directly`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
        session.welcome()

        session.handle(TogetherServerMessage.Playback("them", 5_000, theirTrack, 0, true, 1f))
        runCurrent()

        assertTrue("edition=${BuildConfig.HAS_SCRAPED_SOURCES}", f.repository.queries.isEmpty())
        assertEquals(1, f.player.loaded.size)
        session.leave()
    }

    // --- correcting drift ---

    @Test
    fun `drifting behind them is corrected with a seek`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    @Test
    fun `a small gap is nudged rather than jumped`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    /** The loop that would otherwise exist: correcting ourselves, then announcing the correction. */
    @Test
    fun `a correction is not broadcast back as if we had chosen it`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    @Test
    fun `the user pressing pause on this phone is announced to the room`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    /** The most infuriating possible bug: you hit pause and the sync loop un-pauses you. */
    @Test
    fun `pausing on this phone stays paused`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    @Test
    fun `nothing is announced while the room is simply playing on`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    @Test
    fun `nothing is corrected until the clock is trustworthy`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    @Test
    fun `we hold rather than run ahead of a partner who is buffering`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")
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
    }

    // --- the shared queue ---

    @Test
    fun `adding to the shared queue sends a reference, not a url`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")

        session.addToSharedQueue(track())

        val add = f.transport.meaningful.filterIsInstance<TogetherClientMessage.QueueAdd>().single()
        assertEquals("audius", add.track.provider)
        assertFalse(TogetherProtocol.encode(add).contains("audius.co/stream.mp3"))
        session.leave()
    }

    @Test
    fun `the room's queue lands in state for the screen to show`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")

        session.handle(
            TogetherServerMessage.Queue(1_000, listOf(QueueEntry(theirTrack, addedBy = "them"))),
        )

        assertEquals(1, session.state.value.queue.size)
        assertEquals("them", session.state.value.queue.single().addedBy)
        session.leave()
    }

    @Test
    fun `a relay error is surfaced rather than lost`() = runTest {
        val f = Fixture()
        val session = TestFixtureScope(backgroundScope).session(f)
        session.join(invite, "Me")

        session.handle(TogetherServerMessage.Failure("rate_limited", "Slow down"))

        assertEquals("Slow down", session.state.value.message)
        session.leave()
    }
}
