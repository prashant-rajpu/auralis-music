package com.auralis.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Glassmorphism + Light Baby Pink Music Player Theme (Single Source of Truth)
val BabyPinkBgStart = Color(0xFFFFF5F8)
val BabyPinkBgMiddle = Color(0xFFFFE4EC)
val BabyPinkBgEnd = Color(0xFFFFF0F5)
val BabyPinkBgCard = Color(0xFFFFDDE8)

val BabyPinkPrimary = Color(0xFFFFB6C1)          // Primary Pink (Active states, progress, accents)
val BabyPinkSoftRose = Color(0xFFFFC0CB)         // Soft Rose (Secondary accents, icons)
val BabyPinkAccent = Color(0xFFFF8DA1)           // Vibrant Accent Pink (Linear pill gradients)
val BabyPinkTextPrimary = Color(0xFF5C3A4A)      // Deep Dusty Rose (Primary text: titles, song names)
val BabyPinkTextSecondary = Color(0xFF9A7A87)    // Muted Rose (Secondary: artists, timestamps, labels)
val BabyPinkTextTertiary = Color(0xFFB89DA8)
val BabyPinkCardBg = BabyPinkBgCard
val BabyPinkBorder = Color(0x73FFB6C1)
val BabyPinkBackgroundStart = BabyPinkBgStart

// Glass Surfaces & Borders
val GlassSurface = Color(0x8CFFFFFF)            // rgba(255, 255, 255, 0.55) - Cards, panels, mini player
val GlassSurfaceStrong = Color(0xB3FFFFFF)      // rgba(255, 255, 255, 0.70) - Modals, notifications
val GlassBorder = Color(0x73FFB6C1)             // rgba(255, 182, 193, 0.45) - Soft glowing edges
val GlassBorderHighlight = Color(0x80FFFFFF)    // Subtle white inner light

val ProgressBarTrackPink = Color(0xFFFFD6E0)    // Very light pink track
val PlayButtonGlowPink = Color(0x66FFB6C1)      // Soft radial glow at 40% opacity
val NotificationTint = Color(0xD9FFF0F5)        // rgba(255, 240, 245, 0.85)

// Global Gradient Brush
val BabyPinkBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        BabyPinkBgStart,
        BabyPinkBgMiddle,
        BabyPinkBgEnd
    )
)
