package com.auralis.app.core.util

/** What to do about notifications being off, which is not the same answer every time. */
enum class NotificationStep {
    /** They are on. Nothing to say. */
    NONE,

    /** The system dialog can still be shown, so show it. */
    ASK,

    /** The system dialog will not appear again; the only way back is the app's Settings page. */
    OPEN_SETTINGS,
}

/**
 * Whether to ask for notification permission, or to send the user to Settings instead.
 *
 * The distinction is the whole point. Android shows the permission dialog at most twice; after the
 * second denial a further `requestPermissions` call returns instantly without showing anything.
 * An app that keeps calling it looks, from the user's side, like a permission that cannot be
 * granted — which is what "the media notification permission is not active" is. From there the
 * only route is the app's own notification settings, so that is what the user has to be sent to.
 *
 * Pure, because [askedBefore] and [canAskAgain] are the two facts Android does not hand over
 * directly and getting the combination wrong is invisible until someone is stuck on it.
 */
object NotificationPrompt {

    fun next(
        enabled: Boolean,
        /** The runtime permission itself, which only exists from Android 13. */
        permissionGranted: Boolean,
        /** Whether this app has ever put the system dialog on screen. */
        askedBefore: Boolean,
        /** `shouldShowRequestPermissionRationale`: true after exactly one denial. */
        canAskAgain: Boolean,
        sdk: Int,
    ): NotificationStep = when {
        enabled -> NotificationStep.NONE

        // Before Android 13 there is no permission to ask for: if they are off, someone switched
        // them off in Settings, and Settings is where they come back on.
        sdk < 33 -> NotificationStep.OPEN_SETTINGS

        // Granted but still not enabled means the app, or its channel, was blocked in Settings.
        // Asking again would return "granted" instantly and change nothing.
        permissionGranted -> NotificationStep.OPEN_SETTINGS

        !askedBefore -> NotificationStep.ASK
        canAskAgain -> NotificationStep.ASK
        else -> NotificationStep.OPEN_SETTINGS
    }
}
