package com.auralis.app.call

import com.auralis.app.together.CallAction
import com.auralis.app.together.CallController
import com.auralis.app.together.CallSessionState
import com.auralis.app.together.IceServer
import com.auralis.app.together.TogetherScope
import com.auralis.app.together.TogetherSession
import com.auralis.app.together.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A call, from this phone's side.
 *
 * Three pieces meet here and nowhere else: [CallController] decides what should happen,
 * [CallMediaEngine] does it, and [TogetherSession] carries the handshake to the other phone —
 * encrypted, through the same channel as everything else said in the room, so the relay never
 * learns that a call is happening or what addresses were exchanged to make it work.
 *
 * Runs on the Together session's own single thread. The engine's callbacks arrive on WebRTC's
 * threads and the user's taps on the main one; funnelling both through here means the controller,
 * which is a plain state machine with no locking of its own, is only ever touched from one place.
 */
@Singleton
class CallSession @Inject constructor(
    private val session: TogetherSession,
    private val engine: CallMediaEngine,
    private val keepAlive: CallKeepAlive,
    private val clock: WallClock,
    @TogetherScope private val scope: CoroutineScope,
) {

    private val controller = CallController { session.state.value.room?.isHost == true }

    private val _state = MutableStateFlow(CallSessionState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            session.callSignals.collect { signal -> apply(controller.onSignal(signal)) }
        }
        // Leaving the room ends any call in it. A call that outlived the session it belongs to
        // would have no way to signal, so it would sit there looking live and doing nothing.
        scope.launch {
            session.state.map { it.isActive }.distinctUntilChanged().collect { active ->
                if (!active) apply(controller.hangUp("left the room"))
            }
        }
    }

    // --- what the user does ---------------------------------------------------------------------

    fun call(withVideo: Boolean) = onCall {
        apply(controller.start(withVideo, session.state.value.room?.partner?.name.orEmpty()))
    }

    fun accept() = onCall { apply(controller.accept()) }

    fun decline() = onCall { apply(controller.decline()) }

    fun hangUp() = onCall { apply(controller.hangUp()) }

    fun setMic(enabled: Boolean) = onCall {
        engine.setMicEnabled(enabled)
        apply(controller.setMic(enabled))
    }

    fun setCamera(enabled: Boolean) = onCall {
        engine.setCameraEnabled(enabled)
        apply(controller.setCamera(enabled))
    }

    fun switchCamera() = engine.switchCamera()

    /** Clears the "call ended" card once it has been shown. */
    fun dismissEnded() = onCall {
        controller.dismissEnded()
        _state.value = controller.state
    }

    // --- carrying it out ---------------------------------------------------------------------

    private fun onCall(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private fun apply(actions: List<CallAction>) {
        actions.forEach { action ->
            when (action) {
                is CallAction.CreateOffer -> {
                    engine.start(iceServers(), action.withVideo, events)
                    engine.createOffer()
                }

                is CallAction.AcceptOffer -> {
                    engine.start(iceServers(), action.withVideo, events)
                    engine.acceptOffer(action.sdp, action.withVideo)
                }

                is CallAction.ApplyAnswer -> engine.applyAnswer(action.sdp)

                is CallAction.AddCandidate ->
                    engine.addCandidate(action.candidate, action.sdpMid, action.sdpMLineIndex)

                CallAction.Release -> engine.release()

                is CallAction.Send -> session.sendCallSignal(action.note)
            }
        }
        _state.value = controller.state
        keepAlive(controller.state.isLive)
    }

    /**
     * A call is only allowed to keep the microphone and camera while something says it is running.
     * Started here rather than when the user presses call, so it covers a call this phone answered
     * as well as one it placed, and stops the moment the call is over either way.
     */
    private fun keepAlive(live: Boolean) {
        if (live == keepingAlive) return
        keepingAlive = live
        keepAlive.setRunning(live)
    }

    private var keepingAlive = false

    /**
     * Where to look for a path to the other phone.
     *
     * The relay hands these over on join. If it has not yet — an older relay, or a socket that
     * dropped before the answer came back — public STUN alone is still worth trying: it is enough
     * whenever the two networks will let the phones talk directly, and a call with no ICE servers
     * at all cannot connect under any circumstances.
     */
    private fun iceServers(): List<IceServer> =
        session.iceServers.value.ifEmpty { listOf(FALLBACK_STUN) }

    private val events = object : CallEngineEvents {
        override fun onLocalOffer(sdp: String) = onCall { apply(controller.onLocalOffer(sdp)) }

        override fun onLocalAnswer(sdp: String) = onCall { apply(controller.onLocalAnswer(sdp)) }

        override fun onLocalCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
            onCall { apply(controller.onLocalCandidate(candidate, sdpMid, sdpMLineIndex)) }

        override fun onConnected() = onCall { apply(controller.onConnected(serverNow())) }

        override fun onDisconnected() = onCall { apply(controller.onDisconnected()) }

        override fun onFailed(reason: String) = onCall { apply(controller.onConnectionFailed()) }
    }

    /** Room time, so the duration counter on both phones agrees about when the call started. */
    private fun serverNow(): Long = clock.nowMs() + session.state.value.clockOffsetMs

    private companion object {
        /** Free and unlimited, and the same one the relay falls back to. */
        val FALLBACK_STUN = IceServer(urls = listOf("stun:stun.cloudflare.com:3478"))
    }
}
