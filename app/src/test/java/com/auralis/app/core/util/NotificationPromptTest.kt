package com.auralis.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPromptTest {

    private fun step(
        enabled: Boolean = false,
        granted: Boolean = false,
        asked: Boolean = false,
        canAskAgain: Boolean = false,
        sdk: Int = 36,
    ) = NotificationPrompt.next(enabled, granted, asked, canAskAgain, sdk)

    @Test
    fun `notifications that are on need nothing`() {
        assertEquals(NotificationStep.NONE, step(enabled = true, granted = true, asked = true))
    }

    @Test
    fun `the first time, ask`() {
        assertEquals(NotificationStep.ASK, step(asked = false, canAskAgain = false))
    }

    @Test
    fun `after one denial the dialog can still be shown, so ask again`() {
        assertEquals(NotificationStep.ASK, step(asked = true, canAskAgain = true))
    }

    /**
     * The one that was stuck. After the second denial Android returns instantly without showing a
     * dialog, so asking again is a button that does nothing — the permission looks permanently
     * inactive. Settings is the only way back.
     */
    @Test
    fun `after the dialog stops appearing, send them to settings`() {
        assertEquals(NotificationStep.OPEN_SETTINGS, step(asked = true, canAskAgain = false))
    }

    @Test
    fun `granted but still off means the app was blocked in settings, and asking would change nothing`() {
        assertEquals(NotificationStep.OPEN_SETTINGS, step(granted = true, asked = true))
        // Even if this app never asked: an upgrade from before Android 13 grants it silently.
        assertEquals(NotificationStep.OPEN_SETTINGS, step(granted = true, asked = false))
    }

    @Test
    fun `before android 13 there is no permission to ask for`() {
        assertEquals(NotificationStep.OPEN_SETTINGS, step(sdk = 32, asked = false))
        assertEquals(NotificationStep.OPEN_SETTINGS, step(sdk = 28, asked = true, canAskAgain = true))
    }
}
