package com.auralis.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.auralis.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground MediaSessionService that hosts the Auralis MediaSession.
 * Enables Android SystemUI notification miniplayer, lockscreen controls,
 * and background media playback across all Android versions.
 *
 * Two things here exist because the obvious wiring does not work for this app.
 *
 * **The session is registered by hand.** Media3 adds a session to its service when a
 * `MediaController` binds, or when a media-button or custom-action intent arrives. The player here
 * lives in the UI process and nothing binds a controller, so the plain start intent
 * [PlaybackManager] sends matched neither — Media3 had no session, posted no notification, and the
 * service sat started but never foreground until Android killed the app for it.
 *
 * **The foreground slot is held before Media3 gets to it.** Even registered, Media3 only
 * foregrounds a service whose player is playing or buffering, and a partner's paused state arriving
 * over Together starts this service and then pauses the player. The ten-second contract for
 * `startForegroundService()` is Android's, not Media3's, so it is met here, unconditionally, with
 * a placeholder that Media3's real notification then replaces. See [ForegroundHold].
 */
@UnstableApi
@AndroidEntryPoint
class AuralisMediaSessionService : MediaSessionService() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    private val main = Handler(Looper.getMainLooper())
    private val releaseIfStillPlaceholder = Runnable { releasePlaceholderIfUnused() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // The same id as the placeholder, so Media3's notification replaces ours in place rather
        // than appearing beside it.
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationIdProvider { NOTIFICATION_ID }
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.media_playback_channel_name)
            .build()
        setMediaNotificationProvider(notificationProvider)

        playbackManager.mediaSession?.let { session ->
            runCatching { addSession(session) }
                .onFailure { Log.w(TAG, "could not register the media session", it) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return playbackManager.mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.getBooleanExtra(EXTRA_FROM_APP, false) == true) holdForeground()
        return result
    }

    /**
     * Satisfies `startForegroundService()` straight away.
     *
     * Called for every start, not just the first: each one carries its own ten-second deadline, and
     * `play()` starts the service on every resume. When a notification is already showing it is
     * re-posted as it is rather than replaced with the placeholder, so resuming does not blink
     * Media3's artwork and controls away.
     */
    private fun holdForeground() {
        val showing = shownNotification()
        val notification = showing ?: placeholder()

        val held = runCatching { startForegroundCompat(notification) }
            .onFailure { Log.w(TAG, "could not go foreground", it) }
            .isSuccess
        if (!held) {
            // Refused — typically because the app was in the background when the start arrived.
            // Nothing is worth taking the process down for.
            stopSelf()
            return
        }

        main.removeCallbacks(releaseIfStillPlaceholder)
        main.postDelayed(releaseIfStillPlaceholder, ForegroundHold.GRACE_MS)
    }

    private fun releasePlaceholderIfUnused() {
        val player = playbackManager.activePlayerInstance
        val showing = shownNotificationRecord()
        val release = ForegroundHold.shouldRelease(
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            shown = showing != null,
            shownIsPlaceholder = showing?.notification?.extras?.getBoolean(EXTRA_PLACEHOLDER) == true,
        )
        if (release) runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    private fun shownNotificationRecord() = runCatching {
        getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.firstOrNull { it.id == NOTIFICATION_ID }
    }.getOrNull()

    private fun shownNotification(): Notification? = shownNotificationRecord()?.notification

    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Getting your music ready")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addExtras(android.os.Bundle().apply { putBoolean(EXTRA_PLACEHOLDER, true) })
            .build()

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playbackManager.activePlayerInstance
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        main.removeCallbacks(releaseIfStillPlaceholder)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Auralis music playback controls and status"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "auralis_media_playback_channel"

        /** Media3's own default, pinned so the placeholder and the real thing share one slot. */
        const val NOTIFICATION_ID = 1001

        /** Marks the start intent as ours, so Media3's own media-button starts are left to Media3. */
        const val EXTRA_FROM_APP = "com.auralis.app.playback.FROM_APP"

        private const val EXTRA_PLACEHOLDER = "com.auralis.app.playback.PLACEHOLDER"
        private const val TAG = "AuralisMediaService"
    }
}
