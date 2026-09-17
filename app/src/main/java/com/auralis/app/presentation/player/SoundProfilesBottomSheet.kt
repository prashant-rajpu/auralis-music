package com.auralis.app.presentation.player

import androidx.compose.foundation.background
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
import com.auralis.app.domain.model.SoundPreset
import com.auralis.app.domain.model.SoundProfile
import com.auralis.app.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundProfilesBottomSheet(
    profile: SoundProfile,
    onProfileChange: (SoundProfile) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Audio FX & Sound Profiles",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tune frequency response and seamless crossfade",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Preset chips
            Text(
                text = "Genre Preset",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
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
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bass Boost slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bass Boost",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "${(profile.bassBoostStrength / 10)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyGreen
                )
            }
            Slider(
                value = profile.bassBoostStrength.toFloat(),
                onValueChange = { onProfileChange(profile.copy(bassBoostStrength = it.toInt())) },
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(
                    thumbColor = SpotifyGreen,
                    activeTrackColor = SpotifyGreen
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "${(profile.trebleBoostStrength / 10)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyGreen
                )
            }
            Slider(
                value = profile.trebleBoostStrength.toFloat(),
                onValueChange = { onProfileChange(profile.copy(trebleBoostStrength = it.toInt())) },
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(
                    thumbColor = SpotifyGreen,
                    activeTrackColor = SpotifyGreen
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
                    text = "Dual-Player Crossfade",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "${profile.crossfadeDurationSec}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyGreen
                )
            }
            Slider(
                value = profile.crossfadeDurationSec.toFloat(),
                onValueChange = { onProfileChange(profile.copy(crossfadeDurationSec = it.toInt())) },
                valueRange = 1f..12f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = SpotifyGreen,
                    activeTrackColor = SpotifyGreen
                )
            )
        }
    }
}
