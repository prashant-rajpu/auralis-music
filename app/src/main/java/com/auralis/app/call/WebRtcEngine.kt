package com.auralis.app.call

import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import com.auralis.app.together.IceServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The media half of a call: one peer connection, one microphone, one camera.
 *
 * Deliberately has no opinion about whose turn it is to offer, whether the other person has picked
 * up, or what happens when both people press call at once. [com.auralis.app.together.CallController]
 * owns all of that; this executes what it decides.
 *
 * Two things here are specific to what this app is for. The audio track is tagged
 * `USAGE_VOICE_COMMUNICATION`, so the call rides the voice stream while the music keeps playing on
 * the media stream — the two of you listening to the same song *and* talking over it is the point,
 * not an edge case. And the audio device module is left to the platform's own echo canceller
 * rather than forcing `MODE_IN_COMMUNICATION`, which on most phones would drag the music into the
 * earpiece. The cost is that on speakerphone the music leaks back through the microphone; the UI
 * says so and suggests headphones rather than pretending otherwise.
 */
@Singleton
class WebRtcEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : CallMediaEngine {

    /** Shared by the renderers, so it is created once and outlives any one call. */
    val eglBase: EglBase by lazy { EglBase.create() }

    private val factory: PeerConnectionFactory by lazy { buildFactory() }

    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    /**
     * Which renderer is currently drawing which track.
     *
     * The engine owns this rather than the screen because of the order things happen in. A
     * composable detaches its renderer on the main thread, after the next frame; the engine
     * releases a call on its own thread, immediately. Left to themselves, the track is disposed
     * while a renderer is still attached to it — a use-after-free down in native code, which is
     * not an exception and cannot be caught. Holding the pairs here means every sink can be taken
     * off before anything is disposed.
     */
    private val sinks = LinkedHashMap<VideoSink, VideoTrack>()

    /** Set once the peer connection has been disposed; nothing may be asked of it afterwards. */
    private var closed = true

    private var connection: PeerConnection? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var events: CallEngineEvents? = null

    /** Guards against a late callback from a connection that has already been torn down. */
    private var generation = 0

    override fun start(iceServers: List<IceServer>, withVideo: Boolean, events: CallEngineEvents) {
        release()
        this.events = events
        closed = false
        val era = ++generation

        val configuration = PeerConnection.RTCConfiguration(iceServers.map(::toIceServer)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            // TURN over TCP and TLS is how a call gets out of a network that blocks UDP, which is
            // the case this app has to survive rather than the exotic one.
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            // Keep looking after the first path is found: a phone that moves from wifi to mobile
            // has to be able to hand over without the call dropping.
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            enableImplicitRollback = true
        }

        connection = factory.createPeerConnection(configuration, observer(era)) ?: run {
            events.onFailed("could not open a connection")
            return
        }

        openMicrophone()
        if (withVideo) openCamera()
    }

    override fun createOffer() {
        if (closed) return
        val connection = this.connection ?: return
        connection.createOffer(
            onCreated { description ->
                connection.setLocalDescription(silentObserver(), description)
                events?.onLocalOffer(description.description)
            },
            MediaConstraints(),
        )
    }

    override fun acceptOffer(sdp: String, withVideo: Boolean) {
        if (closed) return
        val connection = this.connection ?: return
        if (withVideo && videoTrack == null) openCamera()
        connection.setRemoteDescription(
            onSet {
                connection.createAnswer(
                    onCreated { description ->
                        connection.setLocalDescription(silentObserver(), description)
                        events?.onLocalAnswer(description.description)
                    },
                    MediaConstraints(),
                )
            },
            SessionDescription(SessionDescription.Type.OFFER, sdp),
        )
    }

    override fun applyAnswer(sdp: String) {
        if (closed) return
        connection?.setRemoteDescription(
            silentObserver(),
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    override fun addCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        // A candidate can arrive moments after the call ended. The object is disposed rather than
        // null by then, so a null check alone would not save it.
        if (closed) return
        runCatching { connection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate)) }
    }

    override fun setMicEnabled(enabled: Boolean) {
        if (closed) return
        runCatching { audioTrack?.setEnabled(enabled) }
    }

    /**
     * Turning the camera off stops the capture rather than only disabling the track, so the
     * indicator light goes out and the battery stops paying for it.
     */
    override fun setCameraEnabled(enabled: Boolean) {
        if (closed) return
        if (enabled && videoTrack == null) {
            openCamera()
            videoTrack?.let { track -> connection?.addTrack(track, listOf(STREAM_ID)) }
            return
        }
        videoTrack?.setEnabled(enabled)
        runCatching { if (enabled) capturer?.startCapture(WIDTH, HEIGHT, FPS) else capturer?.stopCapture() }
    }

    override fun switchCamera() {
        if (closed) return
        runCatching { capturer?.switchCamera(null) }
    }

    /**
     * Starts drawing [track] into [sink], remembering the pair so it can be undone in time.
     *
     * A sink draws one track at a time, so binding it to a new one detaches it from the old.
     */
    fun bindSink(track: VideoTrack, sink: VideoSink) {
        synchronized(sinks) {
            detachLocked(sink)
            if (runCatching { track.addSink(sink) }.isSuccess) sinks[sink] = track
        }
    }

    fun unbindSink(sink: VideoSink) {
        synchronized(sinks) { detachLocked(sink) }
    }

    private fun detachLocked(sink: VideoSink) {
        val track = sinks.remove(sink) ?: return
        runCatching { track.removeSink(sink) }
    }

    private fun detachAllSinks() {
        synchronized(sinks) {
            sinks.forEach { (sink, track) -> runCatching { track.removeSink(sink) } }
            sinks.clear()
        }
    }

    override fun release() {
        generation++
        closed = true
        events = null

        // Before anything is disposed, and deliberately first: a renderer still attached to a
        // track that is about to be freed is the one failure here that does not throw.
        detachAllSinks()

        _localVideo.value = null
        _remoteVideo.value = null

        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { surfaceHelper?.dispose() }
        surfaceHelper = null
        runCatching { videoTrack?.dispose() }
        videoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { audioTrack?.dispose() }
        audioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { connection?.dispose() }
        connection = null
    }

    // --- building the pieces --------------------------------------------------------------------

    private fun buildFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions(),
        )

        val audioDevice = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .createAudioDeviceModule()

        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true),
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private fun openMicrophone() {
        val source = factory.createAudioSource(MediaConstraints())
        val track = factory.createAudioTrack(AUDIO_TRACK_ID, source)
        audioSource = source
        audioTrack = track
        connection?.addTrack(track, listOf(STREAM_ID))
    }

    private fun openCamera() {
        val enumerator: CameraEnumerator =
            if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context)
            else Camera1Enumerator(true)

        // Front camera, because this is a call with one specific person.
        val name = enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)
            ?: enumerator.deviceNames.firstOrNull()
            ?: return
        val created = runCatching { enumerator.createCapturer(name, null) }.getOrNull() ?: return

        val helper = SurfaceTextureHelper.create("AuralisCapture", eglBase.eglBaseContext)
        val source = factory.createVideoSource(created.isScreencast)
        created.initialize(helper, context, source.capturerObserver)
        runCatching { created.startCapture(WIDTH, HEIGHT, FPS) }.onFailure {
            Log.w(TAG, "camera would not start", it)
        }

        val track = factory.createVideoTrack(VIDEO_TRACK_ID, source)
        surfaceHelper = helper
        videoSource = source
        capturer = created
        videoTrack = track
        _localVideo.value = track
        connection?.addTrack(track, listOf(STREAM_ID))
    }

    private fun toIceServer(server: IceServer): PeerConnection.IceServer =
        PeerConnection.IceServer.builder(server.urls)
            .setUsername(server.username.orEmpty())
            .setPassword(server.credential.orEmpty())
            .createIceServer()

    // --- callbacks ------------------------------------------------------------------------------

    private fun observer(era: Int) = object : PeerConnection.Observer {
        private val live: Boolean get() = era == generation

        override fun onIceCandidate(candidate: IceCandidate) {
            if (!live) return
            events?.onLocalCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            if (!live) return
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> events?.onConnected()
                PeerConnection.PeerConnectionState.DISCONNECTED -> events?.onDisconnected()
                PeerConnection.PeerConnectionState.FAILED -> events?.onFailed("no route to them")
                else -> Unit
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            if (!live) return
            val track = transceiver.receiver?.track() ?: return
            if (track is VideoTrack) _remoteVideo.value = track
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            if (!live) return
            val track: MediaStreamTrack? = receiver.track()
            if (track is VideoTrack) _remoteVideo.value = track
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit

        /**
         * Nothing renegotiates here. Both tracks are added before the offer is made, and the
         * camera being switched on mid-call goes through a fresh offer from the controller rather
         * than a silent renegotiation this layer would have to arbitrate.
         */
        override fun onRenegotiationNeeded() = Unit
    }

    private inline fun onCreated(crossinline block: (SessionDescription) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = block(description)
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) {
            events?.onFailed(error)
        }
        override fun onSetFailure(error: String) {
            events?.onFailed(error)
        }
    }

    private inline fun onSet(crossinline block: () -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = block()
        override fun onCreateFailure(error: String) {
            events?.onFailed(error)
        }
        override fun onSetFailure(error: String) {
            events?.onFailed(error)
        }
    }

    private fun silentObserver() = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) {
            Log.w(TAG, "create failed: $error")
        }

        override fun onSetFailure(error: String) {
            Log.w(TAG, "set failed: $error")
        }
    }

    private companion object {
        const val TAG = "WebRtcEngine"
        const val STREAM_ID = "auralis"
        const val AUDIO_TRACK_ID = "auralis-audio"
        const val VIDEO_TRACK_ID = "auralis-video"

        /** Modest on purpose: this is a call over mobile data between two continents. */
        const val WIDTH = 640
        const val HEIGHT = 480
        const val FPS = 24
    }
}
