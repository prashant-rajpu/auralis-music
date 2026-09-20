package com.auralis.app.call

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whatever is holding the microphone and camera open for a call.
 *
 * An interface so a test can watch a call start and stop without a Service, a notification channel
 * or an Android context — the same reason the transport, the player and the recorder are
 * interfaces. There is exactly one real implementation and it starts [CallService].
 */
fun interface CallKeepAlive {
    fun setRunning(live: Boolean)
}

@Singleton
class ServiceCallKeepAlive @Inject constructor(
    @ApplicationContext private val context: Context,
) : CallKeepAlive {
    override fun setRunning(live: Boolean) {
        if (live) CallService.start(context) else CallService.stop(context)
    }
}
