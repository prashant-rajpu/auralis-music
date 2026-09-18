package com.auralis.app.presentation.together

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auralis.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Together Mode "Listen Together 💗" Pill / Button with subtle breathing glow animation.
 * Adheres strictly to Section 11.3 A.
 */
@Composable
fun TogetherModeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTogetherActive: Boolean = false,
    partnerName: String = "Laddu"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "together_button_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "together_pulse_scale"
    )

    Box(
        modifier = modifier
            .scale(pulseScale)
            .hapticPress(scaleDown = 0.94f)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    if (isTogetherActive) {
                        listOf(AccentColor, AccentColorBright)
                    } else {
                        listOf(AccentColorSoft, AccentColor)
                    }
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(OnAccentColor.copy(alpha = 0.55f), AccentColor.copy(alpha = 0.40f))
                ),
                RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Together",
                tint = if (isTogetherActive) OnAccentColor else TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isTogetherActive) "With $partnerName 💗" else "Listen Together 💗",
                color = if (isTogetherActive) OnAccentColor else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Top Glass Strip for Together Now Playing Screen.
 * Two small circular partner avatars with an accent border + text + pulsing heart + "Synced" indicator.
 * Adheres strictly to Section 11.3 C.
 */
@Composable
fun TogetherTopGlassStrip(
    partnerName: String,
    userName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heart_pulse")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .hapticPress(scaleDown = 0.97f)
            .surfacePanel(cornerRadius = 20.dp)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Linked Avatars
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-6).dp)
        ) {
            // User Avatar Circle
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(AccentColor)
                    .border(1.5.dp, OnAccentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase().ifEmpty { "B" },
                    color = OnAccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Partner Avatar Circle
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(AccentColorBright)
                    .border(1.5.dp, OnAccentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = partnerName.take(1).uppercase().ifEmpty { "L" },
                    color = OnAccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Center Together Text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Together with $partnerName 💗",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                fontSize = 13.sp
            )
            Text(
                text = "Laddu Sync • Babu & Wifeeee Session",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        // Pulsing Synced Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33FFB6C1))
                .border(0.5.dp, AccentColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Synced",
                tint = AccentColor,
                modifier = Modifier
                    .size(12.dp)
                    .scale(heartScale)
            )
            Text(
                text = "Synced",
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Floating Live Reactions Tray (❤️ 😘 🥰 🔥) + Memory Quote trigger.
 * Adheres strictly to Section 11.3 E.
 */
@Composable
fun TogetherLiveReactionsTray(
    onSendReaction: (emoji: String) -> Unit,
    onTriggerQuote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reactions = listOf("❤️", "😘", "🥰", "🔥")

    Box(
        modifier = modifier
            .surfacePanel(cornerRadius = 24.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            reactions.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .hapticPress(scaleDown = 0.85f)
                        .clip(CircleShape)
                        .background(OnAccentColor.copy(alpha = 0.65f))
                        .border(1.dp, BorderColor, CircleShape)
                        .clickable { onSendReaction(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 18.sp
                    )
                }
            }

            // Romantic Memory Quote button ("I love you jaanaa 💋")
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .hapticPress(scaleDown = 0.90f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(listOf(AccentColor, AccentColorBright))
                    )
                    .border(1.dp, OnAccentColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .clickable { onTriggerQuote() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "💋", fontSize = 14.sp)
                    Text(
                        text = "jaanaa",
                        color = OnAccentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Floating Reaction Particles:
 * When one user sends a reaction, it gently floats upward on the screen with soft accent particles.
 * Adheres strictly to Section 11.3 E.
 */
data class FloatingParticle(
    val id: Long,
    val emoji: String,
    val startXFraction: Float,
    val speed: Float,
    val size: Float
)

@Composable
fun FloatingReactionParticles(
    triggerReaction: Pair<String, String>?, // (emoji, sender)
    modifier: Modifier = Modifier
) {
    val particles = remember { mutableStateListOf<FloatingParticle>() }

    LaunchedEffect(triggerReaction) {
        if (triggerReaction != null) {
            val emoji = triggerReaction.first
            // Spawn 7 romantic particles with varied positions
            repeat(7) { i ->
                val particle = FloatingParticle(
                    id = System.currentTimeMillis() + i,
                    emoji = emoji,
                    startXFraction = Random.nextFloat().coerceIn(0.15f, 0.85f),
                    speed = Random.nextFloat().coerceIn(0.8f, 1.3f),
                    size = Random.nextFloat().coerceIn(24f, 38f)
                )
                particles.add(particle)
            }
            delay(3500)
            particles.clear()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            SingleFloatingParticle(particle = particle)
        }
    }
}

@Composable
private fun SingleFloatingParticle(particle: FloatingParticle) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(particle.id) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (2800 / particle.speed).toInt(),
                easing = FastOutSlowInEasing
            )
        )
    }

    val progress = animProgress.value
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val offsetY = -progress * 600f
    val swayX = kotlin.math.sin(progress * 6f) * 40f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY
                translationX = swayX
                this.alpha = alpha
            },
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = (particle.startXFraction * 300).dp)
                .padding(bottom = 80.dp)
        ) {
            Text(
                text = particle.emoji,
                fontSize = particle.size.sp
            )
        }
    }
}

/**
 * Romantic Memory Quote Overlay:
 * Soft glass quote from chats appears briefly (“I love you jaanaa 💋”).
 * Adheres strictly to Section 11.4.
 */
@Composable
fun MemoryQuoteOverlay(
    quote: String?,
    sender: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = quote != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        if (quote != null) {
            LaunchedEffect(quote) {
                delay(4000)
                onDismiss()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .surfacePanel(cornerRadius = 24.dp)
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            listOf(OnAccentColor.copy(alpha = 0.55f), AccentColor)
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "💌 Memory From Chats",
                        color = AccentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "\"$quote\"",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "— With love, ${sender ?: "Your Partner"} 💗",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * End Session Romantic Confirmation Dialog:
 * Floating hearts + "Miss you already 💗".
 * Adheres strictly to Section 11.2 (5) & 11.4.
 */
@Composable
fun EndSessionRomanticDialog(
    partnerName: String,
    onConfirmLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xF7FFF5F8),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(OnAccentColor.copy(alpha = 0.55f), AccentColor.copy(alpha = 0.40f))
                    ),
                    RoundedCornerShape(26.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💗",
                    fontSize = 42.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Miss you already 💗",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Are you sure you want to end this Together Mode session with $partnerName? Your playback will no longer be synchronized.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stay Together 💗", color = OnAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onConfirmLeave()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Leave", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * "Our Song" Special Double-Heart Badge:
 * Songs you both loved get a special heart.
 * Adheres strictly to Section 11.4.
 */
@Composable
fun OurSongBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(listOf(AccentColor, AccentColorBright))
            )
            .border(0.8.dp, OnAccentColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Our Song",
            tint = OnAccentColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "Our Song 💖",
            color = OnAccentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
