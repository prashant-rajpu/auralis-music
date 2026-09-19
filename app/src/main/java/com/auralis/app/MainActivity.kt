package com.auralis.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.auralis.app.playback.AuralisSettingsPreferences
import com.auralis.app.presentation.navigation.AuralisNavGraph
import com.auralis.app.ui.theme.AuralisTheme
import com.auralis.app.together.Invite
import com.auralis.app.together.TogetherInvite
import com.auralis.app.ui.theme.LocalHapticIntensity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsPreferences: AuralisSettingsPreferences

    /** An `auralis://join/CODE` link that has arrived but not yet been acted on. */
    private val pendingInvite = mutableStateOf<Invite?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ (One UI 8.5) so lockscreen & notification shade media controls are displayed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        pendingInvite.value = inviteFrom(intent)

        setContent {
            val hapticIntensity by settingsPreferences.hapticIntensity.collectAsState()
            val themeMode by settingsPreferences.themeMode.collectAsState()
            val accent by settingsPreferences.accentPalette.collectAsState()
            CompositionLocalProvider(LocalHapticIntensity provides hapticIntensity) {
                AuralisTheme(themeMode = themeMode, accent = accent) {
                    AuralisNavGraph(
                        pendingInvite = pendingInvite.value,
                        onInviteHandled = { pendingInvite.value = null },
                    )
                }
            }
        }
    }

    /** singleTop, so a second invite arrives here rather than as a second copy of the app. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inviteFrom(intent)?.let { pendingInvite.value = it }
    }

    private fun inviteFrom(intent: Intent?): Invite? {
        val data = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return null
        return TogetherInvite.parse(data.toString())
    }
}
