package com.auralis.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassCard(
    cornerRadius: Dp = 24.dp,
    borderWidth: Dp = 1.2.dp,
    elevation: Dp = 6.dp
): Modifier = this
    .shadow(elevation = elevation, shape = RoundedCornerShape(cornerRadius), ambientColor = PlayButtonGlowPink)
    .clip(RoundedCornerShape(cornerRadius))
    .background(GlassSurface)
    .border(borderWidth, GlassBorder, RoundedCornerShape(cornerRadius))

fun Modifier.glassPanel(
    cornerRadius: Dp = 28.dp,
    borderWidth: Dp = 1.2.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(GlassSurfaceStrong)
    .border(borderWidth, GlassBorder, RoundedCornerShape(cornerRadius))

fun Modifier.glassPill(
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(CircleShape)
    .background(GlassSurfaceStrong)
    .border(borderWidth, GlassBorder, CircleShape)
