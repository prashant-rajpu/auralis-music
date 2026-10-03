package com.auralis.app.presentation.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.auralis.app.core.util.NotificationPrompt
import com.auralis.app.core.util.NotificationStep
import com.auralis.app.ui.theme.AccentColor
import com.auralis.app.ui.theme.TextPrimary
import com.auralis.app.ui.theme.TextSecondary
import com.auralis.app.ui.theme.surfacePanel

/**
 * Says so when notifications are off, and does the right thing about it.
 *
 * Without notifications there is no media notification: no lock-screen controls, nothing in the
 * shade, and on some phones nothing to bring the app back to while the screen is off. The old code
 * asked once at startup and then never mentioned it again, so a user who dismissed the dialog twice
 * — after which Android stops showing it — was left with a permission that looked broken and no way
 * to fix it from inside the app.
 *
 * It asks for the permission once, on first launch, as before. Everything after that is this
 * banner: it appears only once something is loaded to play, and its button either re-shows the
 * system dialog or, when Android will not show it any more, opens the app's notification settings.
 */
@Composable
fun NotificationsBanner(
    showWhen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    // Bumped to recompute: coming back from Settings changes the answer without any callback.
    var refresh by remember { mutableIntStateOf(0) }
    var dismissed by rememberSaveable { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Recorded the moment the dialog has been through, whatever the answer was: from here on
        // "never asked" is no longer true, and that is the fact Android does not keep for us.
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        refresh++
    }

    // Read, not remembered: it has to reflect the current state each time `refresh` ticks.
    val step = remember(refresh) { currentStep(context, prefs.getBoolean(KEY_ASKED, false)) }

    LaunchedEffect(Unit) {
        // The one automatic ask. Only if the dialog can actually be shown, and only the first
        // time — every later prompt is the user's own tap on the banner below.
        if (step == NotificationStep.ASK && !prefs.getBoolean(KEY_ASKED, false)) {
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    if (!showWhen || step == NotificationStep.NONE || dismissed > 0) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .surfacePanel(cornerRadius = 18.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Notifications are off",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Playback controls won't show on your lock screen or in the notification shade.",
            fontSize = 12.sp,
            color = TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (step == NotificationStep.ASK) "Turn on" else "Open settings",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentColor,
                modifier = Modifier.clickable {
                    if (step == NotificationStep.ASK) {
                        runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
            Text(
                text = "Not now",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.clickable { dismissed++ },
            )
        }
    }
}

private fun currentStep(context: Context, askedBefore: Boolean): NotificationStep {
    val activity = context.findActivity()
    val granted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val canAskAgain = Build.VERSION.SDK_INT >= 33 && activity != null &&
        activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)

    return NotificationPrompt.next(
        // The app-level switch and the runtime permission both feed this, which is why it is the
        // single source of truth rather than the permission check on its own.
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        // Before 13 nothing is "granted" in the runtime sense; `next` handles that by SDK.
        permissionGranted = granted && Build.VERSION.SDK_INT >= 33,
        askedBefore = askedBefore,
        canAskAgain = canAskAgain,
        sdk = Build.VERSION.SDK_INT,
    )
}

private fun openNotificationSettings(context: Context) {
    val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // A few OEM builds do not resolve the dedicated page; the general app-details screen always
    // exists and has the notifications toggle one tap away.
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(app) }
        .recoverCatching { context.startActivity(fallback) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PREFS = "auralis_prompts"
private const val KEY_ASKED = "asked_notifications"
