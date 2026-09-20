package com.auralis.app.call

import com.auralis.app.together.CallState
import com.auralis.app.together.FakeMailbox
import com.auralis.app.together.FakePlayer
import com.auralis.app.together.FakeRecorder
import com.auralis.app.together.FakeRepository
import com.auralis.app.together.FakeStore
import com.auralis.app.together.FakeTransport
import com.auralis.app.together.IceServer
import com.auralis.app.together.Invite
import com.auralis.app.together.Member
import com.auralis.app.together.RoomSnapshot
import com.auralis.app.together.TestClock
import com.auralis.app.together.TogetherClientMessage
import com.auralis.app.together.TogetherCrypto
import com.auralis.app.together.TogetherNote
import com.auralis.app.together.TogetherNotes
import com.auralis.app.together.TogetherServerMessage
import com.auralis.app.together.TogetherSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glue: a state machine, a media engine and a room, joined up.
 *
 * Driven through a real [TogetherSession] over a fake transport rather than a mocked one, because
 * the thing worth proving is that a handshake actually travels — that pressing accept ends with an
 * offer, encrypted, on the wire, and with a camera open on this side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallSessionTest {

    private val secret = "23456789ABCDEFGHJKMN"
    private val invite = Invite("ABCDEF", secret)
    private val key = TogetherCrypto.deriveKey("ABCDEF", secret)

    private val me = Member("me", "Me", isHost = true, buffering = false, joinedAtMs = 0)
    private val them = Member("them", "Priya", isHost = false, buffering = false, joinedAtMs = 1)

    /**
     * Stands in for WebRTC. Answers the two calls that produce something — an offer and an answer
     * — so the handshake can be followed all the way to the wire.
     */
    private class FakeEngine : CallMediaEngine {
        var started: Pair<List<IceServer>, Boolean>? = null
        var offers = 0
        var accepted: String? = null
        var answers = mutableListOf<String>()
        val candidates = mutableListOf<String>()
        var released = 0
        var micEnabled: Boolean? = null
        private var events: CallEngineEvents? = null

        override fun start(iceServers: List<IceServer>, withVideo: Boolean, events: CallEngineEvents) {
            started = iceServers to withVideo
            this.events = events
        }

        override fun createOffer() {
            offers++
            events?.onLocalOffer("v=0 offer")
        }

        override fun acceptOffer(sdp: String, withVideo: Boolean) {
            accepted = sdp
            events?.onLocalAnswer("v=0 answer")
        }

        override fun applyAnswer(sdp: String) {
            answers += sdp
        }

        override fun addCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
            candidates += candidate
        }

        override fun setMicEnabled(enabled: Boolean) {
            micEnabled = enabled
        }

        override fun setCameraEnabled(enabled: Boolean) = Unit
        override fun switchCamera() = Unit

        override fun release() {
            released++
        }

        fun connect() = events?.onConnected()
    }

    private class Fixture(
        val transport: FakeTransport = FakeTransport(),
        val player: FakePlayer = FakePlayer(),
        val store: FakeStore = FakeStore(),
        val repository: FakeRepository = FakeRepository(),
        val recorder: FakeRecorder = FakeRecorder(),
        val mailbox: FakeMailbox = FakeMailbox(),
        val engine: FakeEngine = FakeEngine(),
        val clock: TestClock = TestClock(),
    )

    private fun together(f: Fixture, scope: CoroutineScope) = TogetherSession(
        transport = f.transport,
        store = f.store,
        player = f.player,
        repository = f.repository,
        recorder = f.recorder,
        mailbox = f.mailbox,
        clock = f.clock,
        scope = scope,
    )

    /**
     * Joins the room and lands the welcome, with this phone as host unless told otherwise.
     *
     * The `runCurrent` between the two matters: joining is dispatched onto the session's own
     * thread, and a welcome handled before it lands would be applied to a room that does not
     * exist yet — and then overwritten when it does.
     */
    private suspend fun TestScope.enter(session: TogetherSession, iHost: Boolean = true) {
        session.join(invite, "Me")
        runCurrent()
        val hostId = if (iHost) "me" else "them"
        session.handle(
            TogetherServerMessage.Welcome(
                memberId = "me",
                hostId = hostId,
                serverMs = 1_000,
                state = RoomSnapshot(members = listOf(me, them), hostId = hostId),
            ),
        )
        runCurrent()
    }

    private fun theirNote(note: TogetherNote, atMs: Long = 2_000) =
        TogetherServerMessage.Chat("them", atMs, TogetherNotes.seal(key, note))

    private fun List<TogetherClientMessage>.notes(): List<TogetherNote> =
        filterIsInstance<TogetherClientMessage.Chat>().mapNotNull { TogetherNotes.open(key, it.ciphertext) }

    @Test
    fun `answering a call as host opens the camera and puts an offer on the wire`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = true)
        runCurrent()

        session.handle(theirNote(TogetherNote.CallInvite(withVideo = true)))
        runCurrent()
        assertEquals(CallState.RINGING_IN, call.state.value.state)

        call.accept()
        runCurrent()

        assertEquals(true, f.engine.started?.second)
        assertEquals(1, f.engine.offers)
        assertTrue(
            "the offer has to reach the other phone, encrypted like everything else",
            f.transport.sent.notes().any { it is TogetherNote.CallOffer },
        )
    }

    @Test
    fun `a guest that answers says so rather than waiting for a call that never starts`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = false)
        runCurrent()

        session.handle(theirNote(TogetherNote.CallInvite(withVideo = false)))
        runCurrent()
        call.accept()
        runCurrent()

        assertTrue(f.transport.sent.notes().contains(TogetherNote.CallAccept))
        assertEquals("a guest does not offer", 0, f.engine.offers)

        // Their offer arrives; this side answers it.
        session.handle(theirNote(TogetherNote.CallOffer("v=0 theirs", withVideo = false)))
        runCurrent()

        assertEquals("v=0 theirs", f.engine.accepted)
        assertTrue(f.transport.sent.notes().any { it is TogetherNote.CallAnswer })
    }

    @Test
    fun `the call is built with the servers the relay minted, not a guess`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = true)
        val relayServers = listOf(
            IceServer(urls = listOf("stun:stun.cloudflare.com:3478")),
            IceServer(
                urls = listOf("turns:turn.cloudflare.com:443?transport=tcp"),
                username = "u",
                credential = "p",
            ),
        )
        session.handle(TogetherServerMessage.Ice(serverMs = 1_100, iceServers = relayServers))
        runCurrent()

        call.call(withVideo = true)
        runCurrent()
        session.handle(theirNote(TogetherNote.CallAccept))
        runCurrent()

        assertEquals(relayServers, f.engine.started?.first)
        assertTrue(
            "the TLS relay on 443 is the one that survives a network that blocks calls",
            f.engine.started?.first.orEmpty().any { server ->
                server.urls.any { it.startsWith("turns:") && it.contains(":443") }
            },
        )
    }

    @Test
    fun `with no servers from the relay it still tries, rather than refusing to call`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = true)
        runCurrent()

        session.handle(theirNote(TogetherNote.CallInvite(withVideo = false)))
        runCurrent()
        call.accept()
        runCurrent()

        assertNotNull(f.engine.started)
        assertTrue(f.engine.started!!.first.isNotEmpty())
    }

    @Test
    fun `leaving the room ends the call and gives the camera back`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = true)
        runCurrent()
        session.handle(theirNote(TogetherNote.CallInvite(withVideo = true)))
        runCurrent()
        call.accept()
        runCurrent()
        f.engine.connect()
        runCurrent()
        assertEquals(CallState.ACTIVE, call.state.value.state)

        session.leave()
        runCurrent()

        assertEquals(CallState.ENDED, call.state.value.state)
        assertTrue("a call that outlives its room could not even hang up", f.engine.released > 0)
    }

    @Test
    fun `a candidate that arrives before the offer is held, not thrown away`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = false)
        runCurrent()

        session.handle(theirNote(TogetherNote.CallInvite(withVideo = false)))
        runCurrent()
        call.accept()
        runCurrent()

        // Trickled ahead of the description it belongs to, which is routine on a fast network.
        session.handle(theirNote(TogetherNote.CallIce("candidate:early", "0", 0)))
        runCurrent()
        assertTrue(f.engine.candidates.isEmpty())

        session.handle(theirNote(TogetherNote.CallOffer("v=0 theirs", withVideo = false)))
        runCurrent()

        assertEquals(listOf("candidate:early"), f.engine.candidates)
    }

    @Test
    fun `muting tells the microphone and the other phone`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = true)
        runCurrent()
        session.handle(theirNote(TogetherNote.CallInvite(withVideo = false)))
        runCurrent()
        call.accept()
        runCurrent()

        call.setMic(false)
        runCurrent()

        assertEquals(false, f.engine.micEnabled)
        assertTrue(
            f.transport.sent.notes().any { it is TogetherNote.CallMedia && !it.audioEnabled },
        )
    }

    @Test
    fun `the call handshake never reaches the conversation`() = runTest {
        val f = Fixture()
        val session = together(f, backgroundScope)
        val call = CallSession(session, f.engine, f.clock, backgroundScope)
        enter(session, iHost = true)
        runCurrent()
        session.handle(theirNote(TogetherNote.CallInvite(withVideo = true)))
        runCurrent()
        call.accept()
        runCurrent()

        assertTrue(f.mailbox.messagesIn("ABCDEF").isEmpty())
        assertNull(f.mailbox.stored.values.firstOrNull())
    }
}
