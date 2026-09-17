package com.auralis.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ultra-performance Glass Card with hardware-accelerated specular lighting gradient rim.
 * Eliminates heavy RenderNode software shadow blur masks that cause frame drops.
 */
fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 0.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(GlassSurface)
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.25f),
                BabyPinkSoftRose.copy(alpha = 0.20f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Heavy Frosted Glass Panel for bottom sheets, dialogs, and navigation pills.
 */
fun Modifier.glassPanel(
    cornerRadius: Dp = 26.dp,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(GlassSurfaceStrong)
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.90f),
                Color.White.copy(alpha = 0.35f),
                BabyPinkSoftRose.copy(alpha = 0.25f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Frosted Glass Capsule / Pill for filter chips, tags, and icon wrappers.
 */
fun Modifier.glassPill(
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(CircleShape)
    .background(GlassSurfaceStrong)
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.80f),
                Color.White.copy(alpha = 0.20f)
            )
        ),
        shape = CircleShape
    )

/**
 * Luxury Double-Bezel card enclosure (Machined Hardware aesthetic).
 * Concentric outer rim with translucent ambient core.
 */
fun Modifier.doubleBezelCard(
    outerRadius: Dp = 22.dp,
    innerPadding: Dp = 2.dp
): Modifier = this
    .clip(RoundedCornerShape(outerRadius))
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.45f),
                BabyPinkSoftRose.copy(alpha = 0.15f)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.80f),
                BabyPinkSoftRose.copy(alpha = 0.30f)
            )
        ),
        shape = RoundedCornerShape(outerRadius)
    )

/**
 * Haptic Spring Press Physics:
 * Simulates physical button depression with Apple/Linear-tier kinetic tension on touch.
 */
fun Modifier.hapticPress(
    scaleDown: Float = 0.97f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "haptic_spring_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
