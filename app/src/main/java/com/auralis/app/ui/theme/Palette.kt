package com.auralis.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw palette values. Nothing outside this file should reference these directly — screens use the
 * semantic tokens in [AuralisColors], so a theme change is one place rather than a hundred.
 */

// Neutral ramp, dark
internal val Neutral950 = Color(0xFF0B0B0F)
internal val Neutral900 = Color(0xFF121218)
internal val Neutral850 = Color(0xFF16161C)
internal val Neutral800 = Color(0xFF1E1E26)
internal val Neutral700 = Color(0xFF2A2A34)
internal val Neutral600 = Color(0xFF3A3A46)

// Neutral ramp, light
internal val Neutral0 = Color(0xFFFFFFFF)
internal val Neutral50 = Color(0xFFFAFAFC)
internal val Neutral100 = Color(0xFFF2F2F5)
internal val Neutral200 = Color(0xFFE4E4EA)
internal val Neutral300 = Color(0xFFCFCFD8)

// Text
internal val TextOnDarkPrimary = Color(0xFFF4F4F7)
internal val TextOnDarkSecondary = Color(0xFFA2A2B0)
internal val TextOnDarkTertiary = Color(0xFF6C6C7A)
internal val TextOnLightPrimary = Color(0xFF131317)
internal val TextOnLightSecondary = Color(0xFF5C5C69)
internal val TextOnLightTertiary = Color(0xFF90909C)

// Status — same hue in both modes, but a lighter tint reads on dark and a darker one on light
internal val SuccessOnDark = Color(0xFF34D399)
internal val SuccessOnLight = Color(0xFF059669)
internal val WarningOnDark = Color(0xFFFBBF24)
internal val WarningOnLight = Color(0xFFB45309)
internal val DangerOnDark = Color(0xFFF87171)
internal val DangerOnLight = Color(0xFFDC2626)

internal val PureBlack = Color(0xFF000000)
internal val AmoledSurface = Color(0xFF0A0A0D)
internal val AmoledElevated = Color(0xFF141418)

/** Accents are deliberately saturated so they read on both a near-black and an off-white field. */
enum class AccentPalette(
    val id: String,
    val displayName: String,
    val base: Color,
    val bright: Color,
    val soft: Color
) {
    ROSE("rose", "Rose", Color(0xFFFF4D79), Color(0xFFFF7BA0), Color(0xFFFFA7C0)),
    VIOLET("violet", "Violet", Color(0xFF8B5CF6), Color(0xFFA78BFA), Color(0xFFC9B8FC)),
    CYAN("cyan", "Cyan", Color(0xFF06B6D4), Color(0xFF38D3ED), Color(0xFF8EE6F5)),
    AMBER("amber", "Amber", Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFFCD97E)),
    EMERALD("emerald", "Emerald", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF86E7C0)),
    SLATE("slate", "Graphite", Color(0xFF94A3B8), Color(0xFFCBD5E1), Color(0xFFE2E8F0));

    companion object {
        val Default = ROSE
        fun fromId(id: String?): AccentPalette = entries.firstOrNull { it.id == id } ?: Default
    }
}
