package com.auralis.app.call

import com.auralis.app.together.IceServer

/**
 * What a call needs from the machinery that actually moves audio and video.
 *
 * An interface so [CallSession] — the part that decides *when* each of these happens — can be
 * tested without a camera, a microphone or a second phone. Call setup is exactly the kind of thing
 * that is miserable to debug by installing an APK and trying again.
 */
interface CallMediaEngine {

    /**
     * Builds the peer connection and opens the microphone, and the camera if [withVideo].
     *
     * [iceServers] is where it looks for a path to the other phone. An empty list is not fatal —
     * two phones on the same wifi will still find each other — but it is what a call across the
     * world needs, and it is why the relay mints TURN credentials.
     */
    fun start(iceServers: List<IceServer>, withVideo: Boolean, events: CallEngineEvents)

    fun createOffer()

    fun acceptOffer(sdp: String, withVideo: Boolean)

    fun applyAnswer(sdp: String)

    fun addCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)

    fun setMicEnabled(enabled: Boolean)

    fun setCameraEnabled(enabled: Boolean)

    fun switchCamera()

    /** Tears everything down and gives the camera and microphone back. */
    fun release()
}

/** What the engine tells the state machine. Every one of these arrives off the WebRTC threads. */
interface CallEngineEvents {
    fun onLocalOffer(sdp: String)
    fun onLocalAnswer(sdp: String)
    fun onLocalCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)

    /** Media is flowing. */
    fun onConnected()

    /** The path dropped. Not the end of the call — WebRTC may well find another. */
    fun onDisconnected()

    fun onFailed(reason: String)
}
