package com.auralis.app.call

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule this encodes is not a nicety: a foreground service that claims a type whose permission
 * was never granted throws out of `startForeground`, and that takes the process with it.
 */
class CallForegroundTypesTest {

    private val mic = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    private val cam = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

    @Test
    fun `a voice call claims the microphone and not the camera`() {
        // The one that was crashing: the voice button asks for RECORD_AUDIO only, on purpose, so
        // claiming the camera as well was a SecurityException on every audio call.
        assertEquals(mic, foregroundServiceTypes(microphone = true, camera = false))
    }

    @Test
    fun `a video call claims both`() {
        assertEquals(mic or cam, foregroundServiceTypes(microphone = true, camera = true))
    }

    @Test
    fun `nothing granted means the service must not start`() {
        assertEquals(0, foregroundServiceTypes(microphone = false, camera = false))
    }

    @Test
    fun `a camera without a microphone is still not a call worth holding the screen for`() {
        // Possible if someone revokes the microphone in Settings mid-call. There is nothing to
        // keep open, and claiming the camera alone would be refused anyway.
        assertEquals(cam, foregroundServiceTypes(microphone = false, camera = true))
    }
}
