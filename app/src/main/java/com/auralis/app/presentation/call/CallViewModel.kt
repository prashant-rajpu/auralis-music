package com.auralis.app.presentation.call

import androidx.lifecycle.ViewModel
import com.auralis.app.call.CallSession
import com.auralis.app.call.WebRtcEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * The call, for the screen.
 *
 * Thin on purpose: [CallSession] already owns the state machine and the threading, and the only
 * thing the UI needs beyond it is the two video tracks and the EGL context to draw them with.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    private val call: CallSession,
    private val engine: WebRtcEngine,
) : ViewModel() {

    val state = call.state
    val localVideo = engine.localVideo
    val remoteVideo = engine.remoteVideo
    val eglBase get() = engine.eglBase

    fun call(withVideo: Boolean) = call.call(withVideo)
    fun accept() = call.accept()
    fun decline() = call.decline()
    fun hangUp() = call.hangUp()
    fun setMic(enabled: Boolean) = call.setMic(enabled)
    fun setCamera(enabled: Boolean) = call.setCamera(enabled)
    fun switchCamera() = call.switchCamera()
    fun dismissEnded() = call.dismissEnded()
}
