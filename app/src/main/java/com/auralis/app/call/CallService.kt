package com.auralis.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.auralis.app.MainActivity
import com.auralis.app.R
import com.auralis.app.together.CallState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

/**
 * Keeps a call alive when the screen goes off.
 *
 * Android cuts the microphone and camera off an app that is not visible, unless a foreground
 * service with the matching type says otherwise. Without this the call would end the moment the
 * phone locked — which, for two people who fall asleep on a call, is the moment that matters.
 *
 * Started and stopped by [CallSession]; it has no controls of its own beyond hanging up, because
 * everything else about a call needs the screen anyway.
 */
@AndroidEntryPoint
class CallService : Service() {

    @Inject lateinit var call: CallSession

    private var scope: CoroutineScope? = null
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            call.hangUp()
            stopSelf()
            return START_NOT_STICKY
        }

        // Posted immediately: a foreground service that does not show a notification within a few
        // seconds of being started is killed, and the system counts from startForegroundService().
        startInForeground(notification(peerName = "", state = CallState.CONNECTING))

        val running = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        watcher?.cancel()
        watcher = running.launch {
            call.state.collectLatest { state ->
                if (state.state == CallState.IDLE || state.state == CallState.ENDED) {
                    stopSelf()
                    return@collectLatest
                }
                notificationManager()?.notify(
                    NOTIFICATION_ID,
                    notification(state.peerName, state.state),
                )
            }
        }

        // Not sticky: a restarted service would have no call to belong to.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Declaring both keeps the camera usable when it is switched on mid-call; a call that
            // started as audio still has to be able to become a video one.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(peerName: String, state: CallState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hangUp = PendingIntent.getService(
            this,
            1,
            Intent(this, CallService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (peerName.isBlank()) "On a call" else "On a call with $peerName")
            .setContentText(
                when (state) {
                    CallState.RECONNECTING -> "Reconnecting…"
                    CallState.CONNECTING -> "Connecting…"
                    else -> "Tap to go back"
                },
            )
            .setContentIntent(open)
            .addAction(0, "Hang up", hangUp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Calls",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while a call is running so it survives the screen turning off"
            setShowBadge(false)
        }
        notificationManager()?.createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager? =
        getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "auralis_call_channel"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_HANG_UP = "com.auralis.app.call.HANG_UP"

        /**
         * Only ever called while the app is on screen — the user pressed call, or answered one.
         * A microphone or camera foreground service started from the background is refused
         * outright on newer Android, so there is no point trying.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallService::class.java)) }
        }
    }
}
