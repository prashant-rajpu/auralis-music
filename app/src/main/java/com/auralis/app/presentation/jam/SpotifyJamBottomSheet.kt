package com.auralis.app.presentation.jam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auralis.app.network.JamSession
import com.auralis.app.ui.theme.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyJamBottomSheet(
    session: JamSession?,
    onStartJam: (jamId: String, username: String) -> Unit,
    onJoinJam: (jamId: String, username: String) -> Unit,
    onLeaveJam: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isHostTab by remember { mutableStateOf(true) }
    var generatedCode by remember { mutableStateOf("JAM-${Random.nextInt(1000, 9999)}") }
    var joinCodeInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF7FFF5F8),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), ambientColor = PlayButtonGlowPink)
                .border(1.2.dp, GlassBorder, RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = PlayButtonGlowPink)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(BabyPinkPrimary, BabyPinkSoftRose)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Jam",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (session != null) "Live Jam Session" else "Start a Spotify Jam",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = BabyPinkTextPrimary
                )

                Text(
                    text = if (session != null)
                        "Listening in real-time with your partner"
                    else
                        "Listen to the exact same music in real-time, anywhere in the world",
                    style = MaterialTheme.typography.bodySmall,
                    color = BabyPinkTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (session != null) {
                    // Active Jam Screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 18.dp, elevation = 4.dp)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(BabyPinkPrimary)
                                )
                                Text(
                                    text = "SYNCHRONIZED AUDIO BROADCAST",
                                    color = BabyPinkPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = session.jamId,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                ),
                                color = BabyPinkTextPrimary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Share and Copy Buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Jam Code", session.jamId))
                                        Toast.makeText(context, "Jam code copied: ${session.jamId}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceStrong),
                                    border = BorderStroke(1.dp, GlassBorder),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BabyPinkTextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy", color = BabyPinkTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Join my music Jam on Auralis! Jam Room Code: ${session.jamId}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Jam Code with Partner"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BabyPinkPrimary),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Participants List
                    Text(
                        text = "Participants in Jam (${session.participants.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = BabyPinkTextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session.participants.forEach { participant ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GlassSurfaceStrong)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (participant == session.username) BabyPinkPrimary else BabyPinkSoftRose),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = participant.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = participant + if (participant == session.username) " (You)" else "",
                                    color = BabyPinkTextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = "Synced",
                                    tint = BabyPinkPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onLeaveJam()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x26FFB6C1)),
                        border = BorderStroke(1.dp, GlassBorder),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (session.isHost) "End Jam Session" else "Leave Jam",
                            color = BabyPinkTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                } else {
                    // Not In A Jam: Start / Join Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassSurfaceStrong)
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(elevation = if (isHostTab) 4.dp else 0.dp, shape = RoundedCornerShape(16.dp), ambientColor = PlayButtonGlowPink)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isHostTab) BabyPinkPrimary else Color.Transparent)
                                .clickable { isHostTab = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start a Jam",
                                color = if (isHostTab) Color.White else BabyPinkTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(elevation = if (!isHostTab) 4.dp else 0.dp, shape = RoundedCornerShape(16.dp), ambientColor = PlayButtonGlowPink)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!isHostTab) BabyPinkPrimary else Color.Transparent)
                                .clickable { isHostTab = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Join a Jam",
                                color = if (!isHostTab) Color.White else BabyPinkTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (isHostTab) {
                        // Start a Jam
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = { Text("Your Name (e.g. Prashant)", color = BabyPinkTextSecondary, fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = BabyPinkPrimary,
                                unfocusedBorderColor = GlassBorder,
                                containerColor = GlassSurfaceStrong,
                                textColor = BabyPinkTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(cornerRadius = 14.dp, elevation = 2.dp)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Your Jam Room Code", color = BabyPinkTextSecondary, fontSize = 11.sp)
                                    Text(
                                        text = generatedCode,
                                        color = BabyPinkTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                IconButton(onClick = { generatedCode = "JAM-${Random.nextInt(1000, 9999)}" }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = BabyPinkPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                val name = usernameInput.ifBlank { "Host" }
                                onStartJam(generatedCode, name)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabyPinkPrimary),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = PlayButtonGlowPink)
                        ) {
                            Text("Start Jam & Invite Partner", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                    } else {
                        // Join a Jam
                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { joinCodeInput = it.uppercase() },
                            placeholder = { Text("Enter Partner's Jam Code (e.g. JAM-4829)", color = BabyPinkTextSecondary, fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = BabyPinkPrimary,
                                unfocusedBorderColor = GlassBorder,
                                containerColor = GlassSurfaceStrong,
                                textColor = BabyPinkTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = { Text("Your Name (e.g. Sneha)", color = BabyPinkTextSecondary, fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = BabyPinkPrimary,
                                unfocusedBorderColor = GlassBorder,
                                containerColor = GlassSurfaceStrong,
                                textColor = BabyPinkTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (joinCodeInput.isNotBlank()) {
                                    val name = usernameInput.ifBlank { "Partner" }
                                    onJoinJam(joinCodeInput, name)
                                }
                            },
                            enabled = joinCodeInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BabyPinkPrimary,
                                disabledContainerColor = BabyPinkPrimary.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = PlayButtonGlowPink)
                        ) {
                            Text("Join Partner's Jam", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
