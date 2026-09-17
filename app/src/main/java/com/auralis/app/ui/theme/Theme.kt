package com.auralis.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GlassmorphismBabyPinkColorScheme = lightColorScheme(
    primary = BabyPinkPrimary,
    secondary = BabyPinkSoftRose,
    tertiary = PlayButtonGlowPink,
    background = BabyPinkBgStart,
    surface = GlassSurface,
    surfaceVariant = GlassSurfaceStrong,
    onPrimary = BabyPinkTextPrimary,
    onSecondary = BabyPinkTextPrimary,
    onTertiary = BabyPinkTextPrimary,
    onBackground = BabyPinkTextPrimary,
    onSurface = BabyPinkTextPrimary,
    onSurfaceVariant = BabyPinkTextSecondary,
    outline = GlassBorder
)

@Composable
fun AuralisTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = GlassmorphismBabyPinkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BabyPinkBgStart.toArgb()
            window.navigationBarColor = BabyPinkBgEnd.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                // Light theme requires dark status bar and navigation bar icons
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
