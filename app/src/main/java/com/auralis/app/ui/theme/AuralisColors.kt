package com.auralis.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Semantic colors for one resolved theme: a mode (light/dark/AMOLED) plus an accent. */
@Immutable
data class AuralisColors(
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighest: Color,
    val border: Color,
    val borderHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentBright: Color,
    val accentSoft: Color,
    val accentGlow: Color,
    val onAccent: Color,
    val track: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean
)

fun auralisColors(accent: AccentPalette, mode: ResolvedThemeMode): AuralisColors = when (mode) {
    ResolvedThemeMode.LIGHT -> AuralisColors(
        background = Neutral50,
        backgroundElevated = Neutral100,
        surface = Neutral0,
        surfaceElevated = Neutral0,
        surfaceHighest = Neutral100,
        border = Neutral200,
        borderHighlight = Neutral300,
        textPrimary = TextOnLightPrimary,
        textSecondary = TextOnLightSecondary,
        textTertiary = TextOnLightTertiary,
        accent = accent.base,
        accentBright = accent.bright,
        accentSoft = accent.soft,
        accentGlow = accent.base.copy(alpha = 0.18f),
        onAccent = Color.White,
        track = Neutral200,
        success = SuccessOnLight,
        warning = WarningOnLight,
        danger = DangerOnLight,
        isDark = false
    )

    ResolvedThemeMode.DARK -> AuralisColors(
        background = Neutral950,
        backgroundElevated = Neutral900,
        surface = Neutral850,
        surfaceElevated = Neutral800,
        surfaceHighest = Neutral700,
        border = Neutral700,
        borderHighlight = Neutral600,
        textPrimary = TextOnDarkPrimary,
        textSecondary = TextOnDarkSecondary,
        textTertiary = TextOnDarkTertiary,
        accent = accent.base,
        accentBright = accent.bright,
        accentSoft = accent.soft,
        accentGlow = accent.base.copy(alpha = 0.22f),
        onAccent = Color.White,
        track = Neutral700,
        success = SuccessOnDark,
        warning = WarningOnDark,
        danger = DangerOnDark,
        isDark = true
    )

    // Pure black so OLED pixels switch off; surfaces stay barely lifted for separation
    ResolvedThemeMode.AMOLED -> AuralisColors(
        background = PureBlack,
        backgroundElevated = PureBlack,
        surface = AmoledSurface,
        surfaceElevated = AmoledElevated,
        surfaceHighest = Neutral800,
        border = Neutral800,
        borderHighlight = Neutral700,
        textPrimary = TextOnDarkPrimary,
        textSecondary = TextOnDarkSecondary,
        textTertiary = TextOnDarkTertiary,
        accent = accent.base,
        accentBright = accent.bright,
        accentSoft = accent.soft,
        accentGlow = accent.base.copy(alpha = 0.25f),
        onAccent = Color.White,
        track = Neutral800,
        success = SuccessOnDark,
        warning = WarningOnDark,
        danger = DangerOnDark,
        isDark = true
    )
}

val LocalAuralisColors = staticCompositionLocalOf { auralisColors(AccentPalette.Default, ResolvedThemeMode.DARK) }

/**
 * Short composable aliases so screens read as `AccentColor` rather than
 * `LocalAuralisColors.current.accent`.
 */
val AuralisColorScheme: AuralisColors
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current

val BackgroundColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.background

val BackgroundElevated: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.backgroundElevated

val SurfaceColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.surface

val SurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.surfaceElevated

val SurfaceHighest: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.surfaceHighest

val BorderColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.border

val BorderHighlight: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.borderHighlight

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.textSecondary

val TextTertiary: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.textTertiary

val AccentColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.accent

val AccentColorBright: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.accentBright

val AccentColorSoft: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.accentSoft

val AccentGlow: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.accentGlow

val OnAccentColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.onAccent

val TrackColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.track

val SuccessColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.success

val WarningColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.warning

val DangerColor: Color
    @Composable @ReadOnlyComposable get() = LocalAuralisColors.current.danger

/** A near-flat wash; the old theme's three-stop pink gradient read as a gradient, not a surface. */
val BackgroundBrush: Brush
    @Composable @ReadOnlyComposable get() {
        val c = LocalAuralisColors.current
        return Brush.verticalGradient(listOf(c.background, c.backgroundElevated, c.background))
    }
