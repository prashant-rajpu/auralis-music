package com.auralis.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val YtMusicColorScheme = darkColorScheme(
    primary = YtMusicRed,
    secondary = YtMusicTextPrimary,
    tertiary = YtMusicRedGlow,
    background = YtMusicBlack,
    surface = YtMusicSurface,
    surfaceVariant = YtMusicCard,
    onPrimary = Color.White,
    onSecondary = YtMusicBlack,
    onTertiary = Color.White,
    onBackground = YtMusicTextPrimary,
    onSurface = YtMusicTextPrimary,
    onSurfaceVariant = YtMusicTextSecondary,
    outline = YtMusicBorder
)

@Composable
fun AuralisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep false for signature YouTube Music OLED black
    content: @Composable () -> Unit
) {
    val colorScheme = YtMusicColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = YtMusicBlack.toArgb()
            window.navigationBarColor = YtMusicBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
