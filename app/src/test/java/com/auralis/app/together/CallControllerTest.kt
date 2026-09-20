package com.auralis.app.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallControllerTest {

    private fun controller(isHost: Boolean = true) = CallController { isHost }

    private fun signal(note: TogetherNote, name: String = "Priya") =
        CallSignal(senderId = "them", senderName = name, note = note)

    private inline fun <reified T> List<CallAction>.only(): T {
        val hits = filterIsInstance<T>()
        assertEquals("expected exactly one ${T::class.simpleName} in $this", 1, hits.size)
        return hits.first()
    }

    private fun List<CallAction>.sent(): List<TogetherNote> =
        filterIsInstance<CallAction.Send>().map { it.note }

    // --- placing and answering ---

    @Test
    fun `calling rings out and asks before doing any webrtc work`() {
        val c = controller()
        val actions = c.start(withVideo = true, peerName = "Priya")

        assertEquals(CallState.RINGING_OUT, c.state.state)
        assertEquals(listOf(TogetherNote.CallInvite(withVideo = true)), actions.sent())
        // Nothing should touch the camera until someone has agreed to talk.
        assertTrue(actions.none { it is CallAction.CreateOffer })
    }

    @Test
    fun `an invite rings in, carrying whether they want video`() {
        val c = controller(isHost = false)
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))

        assertEquals(CallState.RINGING_IN, c.state.state)
        assertFalse(c.state.withVideo)
        assertEquals("Priya", c.state.peerName)
    }

    @Test
    fun `the host makes the offer when a call is accepted`() {
        val c = controller(isHost = true)
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))

        val actions = c.accept()

        assertEquals(CallState.CONNECTING, c.state.state)
        assertEquals(true, actions.only<CallAction.CreateOffer>().withVideo)
    }

    @Test
    fun `the guest waits for the offer rather than making a competing one`() {
        val c = controller(isHost = false)
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))

        val actions = c.accept()

        assertEquals(CallState.CONNECTING, c.state.state)
        assertTrue("a guest must not offer", actions.none { it is CallAction.CreateOffer })
    }

    /** Both press call at the same moment. Two offers deadlock, so the host's wins. */
    @Test
    fun `a simultaneous call does not deadlock`() {
        val host = controller(isHost = true)
        host.start(withVideo = false, peerName = "Priya")
        val hostActions = host.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))

        val guest = controller(isHost = false)
        guest.start(withVideo = false, peerName = "Sam")
        val guestActions = guest.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))

        assertEquals(CallState.CONNECTING, host.state.state)
        assertEquals(CallState.CONNECTING, guest.state.state)
        assertEquals(1, hostActions.filterIsInstance<CallAction.CreateOffer>().size)
        assertTrue(guestActions.none { it is CallAction.CreateOffer })
    }

    @Test
    fun `declining tells them and lets go of the camera`() {
        val c = controller(isHost = false)
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))

        val actions = c.decline()

        assertEquals(CallState.ENDED, c.state.state)
        assertEquals("declined", c.state.endedReason)
        assertEquals(listOf(TogetherNote.CallDecline), actions.sent())
        assertTrue(actions.any { it is CallAction.Release })
    }

    @Test
    fun `a declined call stops ringing on the caller's side`() {
        val c = controller()
        c.start(withVideo = false, peerName = "Priya")

        val actions = c.onSignal(signal(TogetherNote.CallDecline))

        assertEquals(CallState.ENDED, c.state.state)
        assertEquals("declined", c.state.endedReason)
        assertTrue(actions.any { it is CallAction.Release })
    }

    // --- the handshake ---

    @Test
    fun `their offer is accepted and answered`() {
        val c = controller(isHost = false)
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))
        c.accept()

        val actions = c.onSignal(signal(TogetherNote.CallOffer("v=0 their-offer", withVideo = true)))

        assertEquals("v=0 their-offer", actions.only<CallAction.AcceptOffer>().sdp)
        assertEquals(CallRole.CALLEE, c.state.role)
    }

    @Test
    fun `our answer goes back to them`() {
        val c = controller(isHost = false)
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))
        c.accept()
        c.onSignal(signal(TogetherNote.CallOffer("v=0 theirs", withVideo = false)))

        assertEquals(
            listOf(TogetherNote.CallAnswer("v=0 ours")),
            c.onLocalAnswer("v=0 ours").sent(),
        )
    }

    /**
     * WebRTC trickles candidates the moment it finds them, so on a fast link they routinely
     * overtake the description they belong to. Dropping those costs the call its best route.
     */
    @Test
    fun `candidates that arrive before the offer are held, not dropped`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))

        val early = c.onSignal(signal(TogetherNote.CallIce("candidate:1 udp", "audio", 0)))
        val alsoEarly = c.onSignal(signal(TogetherNote.CallIce("candidate:2 tcp", "audio", 0)))
        assertTrue("nothing to add them to yet", early.isEmpty() && alsoEarly.isEmpty())

        val onAnswer = c.onSignal(signal(TogetherNote.CallAnswer("v=0 theirs")))

        val added = onAnswer.filterIsInstance<CallAction.AddCandidate>()
        assertEquals(listOf("candidate:1 udp", "candidate:2 tcp"), added.map { it.candidate })
    }

    @Test
    fun `candidates after the description are applied straight away`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))
        c.onSignal(signal(TogetherNote.CallAnswer("v=0 theirs")))

        val actions = c.onSignal(signal(TogetherNote.CallIce("candidate:9 udp", "video", 1)))

        val added = actions.only<CallAction.AddCandidate>()
        assertEquals("candidate:9 udp", added.candidate)
        assertEquals(1, added.sdpMLineIndex)
    }

    @Test
    fun `a candidate arriving with no call is ignored rather than queued forever`() {
        val c = controller()
        assertTrue(c.onSignal(signal(TogetherNote.CallIce("candidate:1", "audio", 0))).isEmpty())
    }

    // --- while connected ---

    @Test
    fun `connecting starts the clock once and only once`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))

        c.onConnected(atServerMs = 1_000)
        assertEquals(CallState.ACTIVE, c.state.state)
        assertEquals(1_000L, c.state.activeSinceServerMs)

        c.onConnected(atServerMs = 5_000)
        assertEquals("a reconnect must not restart the duration", 1_000L, c.state.activeSinceServerMs)
    }

    /** Moving from wifi to mobile drops every candidate. Hanging up on that is unusable on a walk. */
    @Test
    fun `losing the path reconnects rather than ending the call`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))
        c.onConnected(atServerMs = 1_000)

        c.onDisconnected()

        assertEquals(CallState.RECONNECTING, c.state.state)
        assertTrue(c.state.isLive)
    }

    @Test
    fun `a connection that genuinely fails ends the call and says so`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))

        val actions = c.onConnectionFailed()

        assertEquals(CallState.ENDED, c.state.state)
        assertEquals("connection failed", c.state.endedReason)
        assertEquals(listOf(TogetherNote.CallEnd("connection failed")), actions.sent())
        assertTrue(actions.any { it is CallAction.Release })
    }

    @Test
    fun `muting tells the other side so it can show it`() {
        val c = controller(isHost = true)
        c.start(withVideo = true, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))
        c.onConnected(atServerMs = 1)

        val actions = c.setMic(false)

        assertFalse(c.state.media.micEnabled)
        assertEquals(
            listOf(TogetherNote.CallMedia(audioEnabled = false, videoEnabled = true)),
            actions.sent(),
        )
    }

    @Test
    fun `their mute shows on our side`() {
        val c = controller(isHost = true)
        c.start(withVideo = true, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))

        c.onSignal(signal(TogetherNote.CallMedia(audioEnabled = false, videoEnabled = false)))

        assertFalse(c.state.media.peerMicEnabled)
        assertFalse(c.state.media.peerCameraEnabled)
    }

    @Test
    fun `nothing is announced when there is no call to announce it about`() {
        val c = controller()
        assertTrue(c.setMic(false).isEmpty())
        assertTrue(c.setCamera(true).isEmpty())
        assertTrue(c.onLocalCandidate("candidate:1", "audio", 0).isEmpty())
    }

    // --- ending ---

    @Test
    fun `hanging up tells them and releases the hardware`() {
        val c = controller(isHost = true)
        c.start(withVideo = true, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))
        c.onConnected(atServerMs = 1)

        val actions = c.hangUp()

        assertEquals(CallState.ENDED, c.state.state)
        assertEquals(listOf(TogetherNote.CallEnd("ended")), actions.sent())
        assertTrue(actions.any { it is CallAction.Release })
    }

    @Test
    fun `their hangup ends it here too`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))
        c.onConnected(atServerMs = 1)

        val actions = c.onSignal(signal(TogetherNote.CallEnd("ended")))

        assertEquals(CallState.ENDED, c.state.state)
        assertTrue(actions.any { it is CallAction.Release })
    }

    @Test
    fun `hanging up twice does not send a second goodbye`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.hangUp()
        assertTrue(c.hangUp().isEmpty())
    }

    @Test
    fun `dismissing a finished call goes back to idle, ready for the next one`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.hangUp()

        c.dismissEnded()

        assertEquals(CallState.IDLE, c.state.state)
        assertEquals("", c.state.endedReason)
        // And a fresh call still works afterwards.
        assertEquals(CallState.RINGING_OUT, controller().let { it.start(false, "Priya"); it.state.state })
    }

    @Test
    fun `stale candidates do not leak into the next call`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))
        c.onSignal(signal(TogetherNote.CallIce("candidate:old", "audio", 0)))
        c.hangUp()
        c.dismissEnded()

        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))
        val actions = c.onSignal(signal(TogetherNote.CallAnswer("v=0 fresh")))

        assertTrue(
            "a candidate from the previous call must not be replayed",
            actions.filterIsInstance<CallAction.AddCandidate>().isEmpty(),
        )
    }

    @Test
    fun `a second invite while already talking is ignored`() {
        val c = controller(isHost = true)
        c.start(withVideo = false, peerName = "Priya")
        c.onSignal(signal(TogetherNote.CallInvite(withVideo = false)))
        c.onConnected(atServerMs = 1)

        c.onSignal(signal(TogetherNote.CallInvite(withVideo = true)))

        assertEquals(CallState.ACTIVE, c.state.state)
        assertFalse("an existing call must not be upgraded behind your back", c.state.withVideo)
    }
}
