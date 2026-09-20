package com.auralis.app.together

/**
 * Whose job it is to make the offer.
 *
 * Both phones can press call at the same moment, and WebRTC has no opinion about who wins — two
 * simultaneous offers just deadlock. The room already has a host, so the tie is broken by that:
 * the host offers, the other side answers. Nothing needs to be negotiated to agree on it, which
 * is the whole point.
 */
enum class CallRole { CALLER, CALLEE }

enum class CallState {
    /** No call, and none being offered. */
    IDLE,

    /** We asked; waiting for them to pick up. */
    RINGING_OUT,

    /** They asked; waiting for us. */
    RINGING_IN,

    /** Answered, and the peer connection is being built. */
    CONNECTING,

    /** Media is flowing. */
    ACTIVE,

    /** Was connected, lost the path, trying to get it back. */
    RECONNECTING,

    /** Over. Why is in [CallSessionState.endedReason]. */
    ENDED,
}

data class CallMediaState(
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = false,
    /** What they told us about their side, so the UI can show a muted or camera-off avatar. */
    val peerMicEnabled: Boolean = true,
    val peerCameraEnabled: Boolean = false,
)

data class CallSessionState(
    val state: CallState = CallState.IDLE,
    val role: CallRole = CallRole.CALLER,
    val withVideo: Boolean = false,
    val peerName: String = "",
    val media: CallMediaState = CallMediaState(),
    val endedReason: String = "",
    /** Room time the call became [CallState.ACTIVE], for the duration counter. */
    val activeSinceServerMs: Long? = null,
) {
    val isRinging: Boolean get() = state == CallState.RINGING_IN || state == CallState.RINGING_OUT
    val isLive: Boolean
        get() = state == CallState.CONNECTING || state == CallState.ACTIVE ||
            state == CallState.RECONNECTING
}

/** What the layer that actually owns WebRTC should do next. */
sealed interface CallAction {
    /** Build a peer connection and produce an offer. */
    data class CreateOffer(val withVideo: Boolean) : CallAction

    /** Build a peer connection from their offer and produce an answer. */
    data class AcceptOffer(val sdp: String, val withVideo: Boolean) : CallAction

    data class ApplyAnswer(val sdp: String) : CallAction
    data class AddCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int) : CallAction

    /** Tear the peer connection down and release camera and microphone. */
    data object Release : CallAction

    /** Put this on the wire, encrypted. */
    data class Send(val note: TogetherNote) : CallAction
}

/**
 * The call, as a state machine over signalling events — and nothing else.
 *
 * Deliberately knows nothing about WebRTC, cameras, or Android. It decides *what should happen*;
 * something else does it. That separation is the only reason any of this can be tested without
 * two phones, a camera and a network, and call setup is exactly the kind of thing that is
 * miserable to debug by installing an APK and trying again.
 *
 * Candidates that arrive before the offer has been applied are held rather than dropped. WebRTC
 * trickles them as soon as it finds them, so on a fast network they routinely overtake the offer,
 * and discarding them costs the call the best route it had.
 */
class CallController(private val isHost: () -> Boolean) {

    private var pending = mutableListOf<TogetherNote.CallIce>()
    private var remoteDescriptionApplied = false

    var state: CallSessionState = CallSessionState()
        private set

    /** The user pressed call. */
    fun start(withVideo: Boolean, peerName: String): List<CallAction> {
        if (state.isLive || state.isRinging) return emptyList()
        reset()
        state = CallSessionState(
            state = CallState.RINGING_OUT,
            role = CallRole.CALLER,
            withVideo = withVideo,
            peerName = peerName,
            media = CallMediaState(cameraEnabled = withVideo),
        )
        return listOf(CallAction.Send(TogetherNote.CallInvite(withVideo)))
    }

    /** The user accepted an incoming call. */
    fun accept(): List<CallAction> {
        if (state.state != CallState.RINGING_IN) return emptyList()
        state = state.copy(state = CallState.CONNECTING)
        // The host offers, so an accepting host still has to be the one to create it. A guest
        // instead has to say it picked up: the host is sitting on a ringing invite and has no
        // other way to find out, and without this both phones ring at each other indefinitely.
        return if (isHost()) listOf(CallAction.CreateOffer(state.withVideo))
        else listOf(CallAction.Send(TogetherNote.CallAccept))
    }

    fun decline(): List<CallAction> {
        if (state.state != CallState.RINGING_IN) return emptyList()
        endLocally("declined")
        return listOf(CallAction.Send(TogetherNote.CallDecline), CallAction.Release)
    }

    /** The user hung up, or left the session. */
    fun hangUp(reason: String = "ended"): List<CallAction> {
        // A call that is already over has nothing to say goodbye about, and saying it twice would
        // end whatever call the other side had started since.
        if (state.state == CallState.IDLE || state.state == CallState.ENDED) return emptyList()
        endLocally(reason)
        return listOf(CallAction.Send(TogetherNote.CallEnd(reason)), CallAction.Release)
    }

    fun setMic(enabled: Boolean): List<CallAction> = toggle(state.media.copy(micEnabled = enabled))

    fun setCamera(enabled: Boolean): List<CallAction> =
        toggle(state.media.copy(cameraEnabled = enabled))

    private fun toggle(media: CallMediaState): List<CallAction> {
        if (!state.isLive) return emptyList()
        state = state.copy(media = media)
        return listOf(CallAction.Send(TogetherNote.CallMedia(media.micEnabled, media.cameraEnabled)))
    }

    /** WebRTC produced something for the other side. */
    fun onLocalOffer(sdp: String): List<CallAction> =
        listOf(CallAction.Send(TogetherNote.CallOffer(sdp, state.withVideo)))

    fun onLocalAnswer(sdp: String): List<CallAction> =
        listOf(CallAction.Send(TogetherNote.CallAnswer(sdp)))

    fun onLocalCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int): List<CallAction> =
        if (state.isLive) listOf(CallAction.Send(TogetherNote.CallIce(candidate, sdpMid, sdpMLineIndex)))
        else emptyList()

    /** WebRTC says media is flowing. */
    fun onConnected(atServerMs: Long): List<CallAction> {
        if (!state.isLive) return emptyList()
        state = state.copy(
            state = CallState.ACTIVE,
            activeSinceServerMs = state.activeSinceServerMs ?: atServerMs,
        )
        return emptyList()
    }

    /**
     * The path dropped. Not the end of the call: a phone moving from wifi to mobile loses every
     * candidate it had and needs a moment to find new ones, and hanging up on that would make the
     * feature useless on a walk.
     */
    fun onDisconnected(): List<CallAction> {
        if (state.state != CallState.ACTIVE) return emptyList()
        state = state.copy(state = CallState.RECONNECTING)
        return emptyList()
    }

    fun onConnectionFailed(): List<CallAction> {
        if (!state.isLive) return emptyList()
        endLocally("connection failed")
        return listOf(CallAction.Send(TogetherNote.CallEnd("connection failed")), CallAction.Release)
    }

    /** Something arrived from the other phone. */
    fun onSignal(signal: CallSignal): List<CallAction> = when (val note = signal.note) {
        is TogetherNote.CallInvite -> {
            if (state.state == CallState.IDLE || state.state == CallState.ENDED) {
                reset()
                state = CallSessionState(
                    state = CallState.RINGING_IN,
                    role = CallRole.CALLEE,
                    withVideo = note.withVideo,
                    peerName = signal.senderName,
                    media = CallMediaState(cameraEnabled = note.withVideo),
                )
                emptyList()
            } else if (state.state == CallState.RINGING_OUT) {
                // Both pressed call. Rather than collide, treat it as answered and let the host
                // make the offer — the same tie-break used everywhere else.
                state = state.copy(state = CallState.CONNECTING, role = if (isHost()) CallRole.CALLER else CallRole.CALLEE)
                if (isHost()) listOf(CallAction.CreateOffer(state.withVideo)) else emptyList()
            } else {
                emptyList()
            }
        }

        is TogetherNote.CallAccept -> {
            if (state.state == CallState.RINGING_OUT) {
                state = state.copy(state = CallState.CONNECTING)
                if (isHost()) listOf(CallAction.CreateOffer(state.withVideo)) else emptyList()
            } else {
                emptyList()
            }
        }

        is TogetherNote.CallDecline -> {
            if (state.state == CallState.RINGING_OUT) {
                endLocally("declined")
                listOf(CallAction.Release)
            } else {
                emptyList()
            }
        }

        is TogetherNote.CallOffer -> {
            // An offer while we were ringing out means they accepted and got there first.
            if (state.isRinging || state.state == CallState.CONNECTING) {
                // Role is not touched: whoever pressed call first is still the caller, even
                // though the offer happens to be arriving from the other direction.
                state = state.copy(
                    state = CallState.CONNECTING,
                    withVideo = note.withVideo,
                    peerName = signal.senderName.ifEmpty { state.peerName },
                )
                remoteDescriptionApplied = true
                listOf(CallAction.AcceptOffer(note.sdp, note.withVideo)) + drainCandidates()
            } else {
                emptyList()
            }
        }

        is TogetherNote.CallAnswer -> {
            if (state.state == CallState.CONNECTING) {
                remoteDescriptionApplied = true
                listOf(CallAction.ApplyAnswer(note.sdp)) + drainCandidates()
            } else {
                emptyList()
            }
        }

        is TogetherNote.CallIce -> {
            if (!state.isLive) {
                emptyList()
            } else if (remoteDescriptionApplied) {
                listOf(CallAction.AddCandidate(note.candidate, note.sdpMid, note.sdpMLineIndex))
            } else {
                // Trickled ahead of the description it belongs to. Hold it.
                pending.add(note)
                emptyList()
            }
        }

        is TogetherNote.CallEnd -> {
            if (state.state != CallState.IDLE) {
                endLocally(note.reason.ifEmpty { "ended" })
                listOf(CallAction.Release)
            } else {
                emptyList()
            }
        }

        is TogetherNote.CallMedia -> {
            state = state.copy(
                media = state.media.copy(
                    peerMicEnabled = note.audioEnabled,
                    peerCameraEnabled = note.videoEnabled,
                ),
            )
            emptyList()
        }

        else -> emptyList()
    }

    private fun drainCandidates(): List<CallAction> {
        val held = pending.map { CallAction.AddCandidate(it.candidate, it.sdpMid, it.sdpMLineIndex) }
        pending = mutableListOf()
        return held
    }

    private fun endLocally(reason: String) {
        state = state.copy(state = CallState.ENDED, endedReason = reason, activeSinceServerMs = null)
        reset()
    }

    private fun reset() {
        pending = mutableListOf()
        remoteDescriptionApplied = false
    }

    /** Back to idle once the UI has shown why it ended. */
    fun dismissEnded() {
        if (state.state == CallState.ENDED) state = CallSessionState()
    }
}
