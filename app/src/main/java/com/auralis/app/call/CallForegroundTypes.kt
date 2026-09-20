package com.auralis.app.call

import android.content.pm.ServiceInfo

/**
 * Which foreground-service types a call may legally claim.
 *
 * Pure, and separate from the service, because getting it wrong is not a degraded call — it is a
 * `SecurityException` thrown out of `startForeground`, which takes the whole process with it. A
 * type may only be claimed once its permission has been granted, and an audio-only call never
 * asks for the camera.
 *
 * Zero means the service must not start at all: with no microphone there is nothing for it to
 * hold open, and a foreground service claiming nothing is refused anyway.
 */
fun foregroundServiceTypes(microphone: Boolean, camera: Boolean): Int {
    var types = 0
    if (microphone) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    // Claimed whenever the permission is held rather than only while the camera is on, so a call
    // that starts as audio and grows a camera does not have to renegotiate its own type mid-call.
    if (camera) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
    return types
}
