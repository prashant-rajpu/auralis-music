package com.auralis.app.presentation.player

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auralis.app.domain.model.SoundPreset
import com.auralis.app.domain.model.SoundProfile
import com.auralis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundProfilesBottomSheet(
    profile: SoundProfile,
    onProfileChange: (SoundProfile) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = YtMusicSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.dp, YtMusicBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audio FX & Equalizer",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Hardware DSP & Crossfade Mixing",
                            style = MaterialTheme.typography.bodySmall,
                            color = YtMusicTextSecondary
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = YtMusicRed, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Preset chips
                Text(
                    text = "Sound Profiles",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundPreset.values().forEach { preset ->
                        val isSelected = profile.preset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onProfileChange(profile.copy(preset = preset)) },
                            label = { Text(preset.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YtMusicRed,
                                selectedLabelColor = Color.White,
                                containerColor = YtMusicCard,
                                labelColor = YtMusicTextSecondary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bass Boost slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass Boost",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                    Text(
                        text = "${(profile.bassBoostStrength / 10)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = YtMusicRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = profile.bassBoostStrength.toFloat(),
                    onValueChange = { onProfileChange(profile.copy(bassBoostStrength = it.toInt())) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = YtMusicRed,
                        activeTrackColor = YtMusicRed,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Treble Boost slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Treble Boost",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                    Text(
                        text = "${(profile.trebleBoostStrength / 10)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = YtMusicRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = profile.trebleBoostStrength.toFloat(),
                    onValueChange = { onProfileChange(profile.copy(trebleBoostStrength = it.toInt())) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = YtMusicRed,
                        activeTrackColor = YtMusicRed,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Crossfade Duration slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dual-Engine Crossfade Duration",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                    Text(
                        text = "${profile.crossfadeDurationSec}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = YtMusicRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = profile.crossfadeDurationSec.toFloat(),
                    onValueChange = { onProfileChange(profile.copy(crossfadeDurationSec = it.toInt())) },
                    valueRange = 1f..12f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = YtMusicRed,
                        activeTrackColor = YtMusicRed,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
            }
        }
    }
}
