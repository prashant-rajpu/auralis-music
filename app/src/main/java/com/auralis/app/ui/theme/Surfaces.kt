package com.auralis.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The surface modifiers below are the app's only card/panel/pill recipes. They are composable so
 * they can read [LocalAuralisColors]: a hairline that is a white highlight on a dark field has to
 * become a dark hairline on a light one, or every card in light mode looks like a smudge.
 */

/** Hairline that catches light at the top edge and fades into the plain border colour. */
@Composable
private fun edgeBrush(highlightAlpha: Float): Brush {
    val colors = LocalAuralisColors.current
    return Brush.verticalGradient(
        listOf(
            colors.borderHighlight.copy(alpha = highlightAlpha),
            colors.border,
            colors.border.copy(alpha = 0.6f)
        )
    )
}

/** Standard content card: track rows, shelf tiles, settings groups. */
@Composable
fun Modifier.surfaceCard(
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(SurfaceColor)
    .border(borderWidth, edgeBrush(0.9f), RoundedCornerShape(cornerRadius))

/** Raised panel: bottom sheets, dialogs, the mini player, the nav bar. */
@Composable
fun Modifier.surfacePanel(
    cornerRadius: Dp = 26.dp,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(SurfaceElevated)
    .border(borderWidth, edgeBrush(1f), RoundedCornerShape(cornerRadius))

/** Capsule: filter chips, tags, icon wrappers. */
@Composable
fun Modifier.surfacePill(
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(CircleShape)
    .background(SurfaceElevated)
    .border(borderWidth, edgeBrush(0.8f), CircleShape)

/** Concentric frame around album art — an outer bezel a shade lighter than the art's backdrop. */
@Composable
fun Modifier.artworkFrame(
    outerRadius: Dp = 22.dp
): Modifier {
    val colors = LocalAuralisColors.current
    return this
        .clip(RoundedCornerShape(outerRadius))
        .background(
            Brush.verticalGradient(listOf(colors.surfaceHighest, colors.surfaceElevated))
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(colors.borderHighlight, colors.border)
            ),
            shape = RoundedCornerShape(outerRadius)
        )
}

/** Accent-tinted rim for active state: the playing row, the current chip, the play button. */
@Composable
fun Modifier.accentGlowBorder(
    glowColor: Color = AccentColor,
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.2.dp
): Modifier = this.border(
    width = borderWidth,
    brush = Brush.verticalGradient(
        listOf(
            glowColor.copy(alpha = 0.90f),
            glowColor.copy(alpha = 0.40f),
            glowColor.copy(alpha = 0.15f)
        )
    ),
    shape = RoundedCornerShape(cornerRadius)
)

/**
 * Spring "squeeze" while pressed plus haptic feedback on tap, honoring the user's haptic
 * intensity setting. Observes the pointer directly (Initial pass, never consuming), so it works
 * in front of any clickable without sharing its interaction source and never steals its events.
 */
fun Modifier.hapticPress(
    scaleDown: Float = 0.97f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val externallyPressed by source.collectIsPressedAsState()
    var pointerPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val intensity = LocalHapticIntensity.current
    val scale by animateFloatAsState(
        targetValue = if (externallyPressed || pointerPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "haptic_spring_scale"
    )
    this
        .pointerInput(intensity) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pointerPressed = true
                var isTap = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                        if ((tracked.position - down.position).getDistance() > slop) isTap = false
                        if (event.changes.none { it.pressed }) break
                    }
                } finally {
                    pointerPressed = false
                }
                // Only a tap (not the start of a scroll) earns a tick
                if (isTap) intensity.feedbackType?.let(haptic::performHapticFeedback)
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Micro animated equalizer bars for active tracks, now-playing headers, and mini-players.
 */
@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    barColor: Color = AccentColor,
    barCount: Int = 3,
    maxHeight: Dp = 16.dp,
    isPlaying: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_anim")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "eq_bar_1"
    )

    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "eq_bar_2"
    )

    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "eq_bar_3"
    )

    val fractions = listOf(h1, h2, h3)

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount) {
            val frac = if (isPlaying) fractions[i % fractions.size] else 0.25f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction = frac.coerceIn(0.2f, 1f))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}
