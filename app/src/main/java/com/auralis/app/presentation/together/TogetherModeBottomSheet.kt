package com.auralis.app.presentation.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auralis.app.network.JamProtocolHelper
import com.auralis.app.network.JamSession
import com.auralis.app.ui.theme.*
import kotlin.random.Random

/**
 * Together Mode (Couple Sync) Bottom Sheet Modal
 * "Laddu Sync" / "Babu & Wifeeee Session"
 * Listen-together dialog: create or join a session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherModeBottomSheet(
    session: JamSession?,
    isConnected: Boolean = true,
    onReconnect: () -> Unit = {},
    onStartTogether: (jamId: String, username: String) -> Unit,
    onJoinTogether: (jamId: String, username: String) -> Unit,
    onLeaveTogether: () -> Unit,
    onSendReaction: (emoji: String) -> Unit = {},
    onSendMemoryQuote: (quote: String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isHostTab by remember { mutableStateOf(true) }
    var generatedCode by remember { mutableStateOf("BABU-${Random.nextInt(1000, 9999)}") }
    var joinCodeInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("Babu") }
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    val partnerName = remember(session) {
        session?.participants?.firstOrNull { it != session.username } ?: "Laddu"
    }

    if (showLeaveConfirmation) {
        EndSessionRomanticDialog(
            partnerName = partnerName,
            onConfirmLeave = {
                showLeaveConfirmation = false
                onLeaveTogether()
                onDismiss()
            },
            onDismiss = { showLeaveConfirmation = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(BorderHighlight, AccentColor.copy(alpha = 0.45f))
                    ),
                    RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Linked Profile Circles (Section 11.3 B)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AccentColor)
                            .border(2.dp, SurfaceElevated, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (session != null) session.username.take(1).uppercase() else "B",
                            color = OnAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    // Pulsing Heart Center
                    val infiniteTransition = rememberInfiniteTransition(label = "modal_heart")
                    val heartScale by infiniteTransition.animateFloat(
                        initialValue = 0.90f,
                        targetValue = 1.20f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "modal_heart_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .scale(heartScale)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.dp, AccentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "💗", fontSize = 16.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AccentColorBright)
                            .border(2.dp, SurfaceElevated, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (session != null) partnerName.take(1).uppercase() else "L",
                            color = OnAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (session != null) "Together Mode Active 💗" else "Together Mode 💗",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextPrimary
                )

                Text(
                    text = if (session != null)
                        "Listening with $partnerName • Laddu Sync"
                    else
                        "Laddu Sync • Babu & Wifeeee Session\nListen in real-time sync with your partner",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (session != null) {
                    // Active Together Mode Session Screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 18.dp)
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "jam_status_pulse")
                                val statusAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "status_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isConnected) SuccessColor.copy(alpha = statusAlpha)
                                            else WarningColor.copy(alpha = statusAlpha)
                                        )
                                )
                                Text(
                                    text = if (isConnected) "REAL-TIME SYNC ACTIVE 🟢" else "RECONNECTING... 🟡",
                                    color = if (isConnected) SuccessColor else WarningColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                if (!isConnected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Retry",
                                        color = AccentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.clickable { onReconnect() }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = session.jamId,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                ),
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Share and Copy Buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Together Code", session.jamId))
                                        Toast.makeText(context, "Session code copied: ${session.jamId}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                                    border = BorderStroke(1.dp, BorderColor),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Code", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Hey my love 💗 Babu wants to listen to music together with you on Auralis! Join our Together Session with room code: ${session.jamId} 🎶💏"
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Invite Partner to Together Mode 💗"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = OnAccentColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Invite Partner", color = OnAccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Reactions Tray inside the Modal (Section 11.3 E)
                    Text(
                        text = "Send Live Reaction 💖",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    TogetherLiveReactionsTray(
                        onSendReaction = { emoji ->
                            onSendReaction(emoji)
                            Toast.makeText(context, "Sent $emoji to $partnerName 💗", Toast.LENGTH_SHORT).show()
                        },
                        onTriggerQuote = {
                            onSendMemoryQuote("I love you jaanaa 💋")
                            Toast.makeText(context, "Sent love note to $partnerName 💋", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Participants in Together Mode
                    Text(
                        text = "Synced Lovers (${session.participants.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session.participants.forEach { participant ->
                            val isMe = participant == session.username
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (isMe) AccentColor else AccentColorBright),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = participant.take(1).uppercase(),
                                        color = OnAccentColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = participant + if (isMe) " (You)" else " 💗",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = "Synced",
                                    tint = AccentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { showLeaveConfirmation = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x26FFB6C1)),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Leave Together Mode 💗",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                } else {
                    // Not in Together Mode: Start / Join Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .hapticPress(scaleDown = 0.94f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isHostTab) AccentColor else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isHostTab) OnAccentColor.copy(alpha = 0.85f) else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { isHostTab = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start Session 💗",
                                color = if (isHostTab) OnAccentColor else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .hapticPress(scaleDown = 0.94f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!isHostTab) AccentColor else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (!isHostTab) OnAccentColor.copy(alpha = 0.85f) else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { isHostTab = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Join Partner 💏",
                                color = if (!isHostTab) OnAccentColor else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Romantic Name Preset Chips ("Laddu 💖", "Babu 💗", "Wifeeee 🥰")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Babu 💗", "Laddu 💖", "Wifeeee 🥰").forEach { preset ->
                            val cleanName = preset.split(" ").first()
                            val isSelected = usernameInput == cleanName
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .hapticPress(scaleDown = 0.92f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) AccentColor else SurfaceElevated)
                                    .border(1.dp, if (isSelected) OnAccentColor.copy(alpha = 0.7f) else BorderColor, RoundedCornerShape(14.dp))
                                    .clickable { usernameInput = cleanName }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) OnAccentColor else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isHostTab) {
                        // Start Session Tab
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("Your Name", color = TextSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentColor,
                                unfocusedBorderColor = BorderColor,
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceElevated,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .surfaceCard(cornerRadius = 16.dp)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Together Session Code", color = TextSecondary, fontSize = 11.sp)
                                    Text(
                                        text = generatedCode,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                IconButton(
                                    onClick = { generatedCode = "BABU-${Random.nextInt(1000, 9999)}" },
                                    modifier = Modifier.hapticPress(scaleDown = 0.88f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = AccentColor)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val name = usernameInput.ifBlank { "Babu" }
                                onStartTogether(generatedCode, name)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(OnAccentColor.copy(alpha = 0.55f), AccentColorBright.copy(alpha = 0.30f))
                                    ),
                                    RoundedCornerShape(22.dp)
                                )
                                .hapticPress(scaleDown = 0.94f)
                        ) {
                            Text("Start & Invite Laddu 💗", color = OnAccentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                    } else {
                        // Receiver Side Invitation / Join Card (Section 11.2 (2))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .surfaceCard(cornerRadius = 16.dp)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "💌", fontSize = 22.sp)
                                Text(
                                    text = "Babu wants to listen with you 💗",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { joinCodeInput = it.uppercase() },
                            placeholder = { Text("Enter Partner's Code (e.g. BABU-4829)", color = TextSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentColor,
                                unfocusedBorderColor = BorderColor,
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceElevated,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = { Text("Your Name (e.g. Laddu)", color = TextSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentColor,
                                unfocusedBorderColor = BorderColor,
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceElevated,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Maybe later", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    if (JamProtocolHelper.isValidJamCode(joinCodeInput)) {
                                        val name = usernameInput.ifBlank { "Laddu" }
                                        onJoinTogether(joinCodeInput, name)
                                    }
                                },
                                enabled = JamProtocolHelper.isValidJamCode(joinCodeInput),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentColor,
                                    disabledContainerColor = AccentColor.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .hapticPress(scaleDown = 0.94f)
                            ) {
                                Text("Join Together 💗", color = OnAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
