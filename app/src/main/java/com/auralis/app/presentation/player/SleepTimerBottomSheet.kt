package com.auralis.app.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auralis.app.ui.theme.*

@Composable
fun SleepTimerBottomSheet(
    remainingMinutes: Int?,
    onSetTimer: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = GlassSurfaceStrong,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.2.dp, GlassBorder, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BabyPinkPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Sleep Timer",
                                color = BabyPinkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Gentle 10s volume fade-out",
                                color = BabyPinkTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.hapticPress(scaleDown = 0.88f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = BabyPinkTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Active Timer Status Banner
                if (remainingMinutes != null && remainingMinutes > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 16.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Timer Active 🌙",
                                color = BabyPinkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$remainingMinutes minutes remaining",
                                color = BabyPinkTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                onSetTimer(null)
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = BabyPinkPrimary)
                        ) {
                            Text("Turn Off", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = "Select Duration",
                    color = BabyPinkTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Timer Duration Presets
                val presets = listOf(15, 30, 45, 60, 90)
                presets.forEach { minutes ->
                    val isCurrent = remainingMinutes == minutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCurrent) {
                                    Brush.horizontalGradient(listOf(BabyPinkPrimary, BabyPinkAccent))
                                } else {
                                    Brush.linearGradient(listOf(GlassSurfaceStrong, GlassSurface))
                                }
                            )
                            .border(
                                1.dp,
                                if (isCurrent) Color.White.copy(alpha = 0.8f) else GlassBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .hapticPress(scaleDown = 0.97f)
                            .clickable {
                                onSetTimer(minutes)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$minutes Minutes",
                            color = if (isCurrent) Color.White else BabyPinkTextPrimary,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isCurrent) Color.White else BabyPinkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
