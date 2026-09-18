package com.auralis.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** What the user picked. SYSTEM follows the device setting. */
enum class ThemeMode(val id: String, val displayName: String) {
    SYSTEM("system", "Follow system"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "AMOLED black");

    companion object {
        val Default = SYSTEM
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** What SYSTEM resolves to once the device setting is known. */
enum class ResolvedThemeMode { LIGHT, DARK, AMOLED }

fun ThemeMode.resolve(systemInDark: Boolean): ResolvedThemeMode = when (this) {
    ThemeMode.SYSTEM -> if (systemInDark) ResolvedThemeMode.DARK else ResolvedThemeMode.LIGHT
    ThemeMode.LIGHT -> ResolvedThemeMode.LIGHT
    ThemeMode.DARK -> ResolvedThemeMode.DARK
    ThemeMode.AMOLED -> ResolvedThemeMode.AMOLED
}

@Composable
fun AuralisTheme(
    themeMode: ThemeMode = ThemeMode.Default,
    accent: AccentPalette = AccentPalette.Default,
    content: @Composable () -> Unit
) {
    val resolved = themeMode.resolve(isSystemInDarkTheme())
    val colors = remember(accent, resolved) { auralisColors(accent, resolved) }

    // Material components (sliders, text fields, sheets) read the Material scheme, so it is
    // derived from the same tokens instead of being a second source of truth.
    val materialScheme = remember(colors) {
        val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentSoft,
            onSecondary = colors.onAccent,
            tertiary = colors.accentBright,
            onTertiary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surfaceElevated,
            surfaceContainerHigh = colors.surfaceHighest,
            surfaceContainerHighest = colors.surfaceHighest,
            outline = colors.border,
            outlineVariant = colors.borderHighlight
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge to edge: let content draw under the bars and only control icon contrast
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
        }
    }

    CompositionLocalProvider(LocalAuralisColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content
        )
    }
}
