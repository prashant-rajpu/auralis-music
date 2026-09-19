package com.auralis.app.presentation.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.playback.*
import com.auralis.app.together.RelayEndpoints
import com.auralis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val lyricsProvider by viewModel.lyricsProvider.collectAsState()
    val lyricsAutoScroll by viewModel.lyricsAutoScroll.collectAsState()
    val lyricsFontSize by viewModel.lyricsFontSize.collectAsState()
    val offlineLyricsEnabled by viewModel.offlineLyricsEnabled.collectAsState()

    val infiniteRadioAutoplay by viewModel.infiniteRadioAutoplay.collectAsState()
    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val hapticIntensity by viewModel.hapticIntensity.collectAsState()
    val relayUrl by viewModel.relayUrl.collectAsState()
    val togetherName by viewModel.togetherDisplayName.collectAsState()
    var relayDraft by rememberSaveable(relayUrl) { mutableStateOf(relayUrl) }
    var relayRejected by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable(togetherName) { mutableStateOf(togetherName) }

    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val importInputText by viewModel.importInputText.collectAsState()
    val toastEvent by viewModel.toastEvent.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentPalette by viewModel.accentPalette.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()

    LaunchedEffect(toastEvent) {
        toastEvent?.let { event ->
            when (event) {
                is SettingsToastEvent.Success -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is SettingsToastEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            viewModel.clearToastEvent()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .surfacePill(borderWidth = 1.dp)
                        .hapticPress(scaleDown = 0.88f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = AccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Settings & Adjustments",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(modifier = Modifier.size(40.dp)) // Spacer to balance header
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // SECTION 0: APPEARANCE
                item {
                    SettingsSectionHeader(
                        icon = Icons.Default.Palette,
                        title = "Appearance",
                        subtitle = "Theme and accent, applied instantly across every screen"
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 20.dp)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Theme",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    val isSelected = themeMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isSelected) AccentColor.copy(alpha = 0.16f) else SurfaceElevated)
                                            .border(
                                                1.dp,
                                                if (isSelected) AccentColor else BorderColor,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable { viewModel.setThemeMode(mode) }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (mode) {
                                                    ThemeMode.SYSTEM -> Icons.Default.PhoneAndroid
                                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                                    ThemeMode.AMOLED -> Icons.Default.Contrast
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) AccentColor else TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = mode.displayName,
                                                color = TextPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Active",
                                                tint = AccentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                            Text(
                                text = "Accent",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AccentPalette.entries.forEach { palette ->
                                    val isSelected = accentPalette == palette
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(palette.base)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) TextPrimary else BorderColor,
                                                shape = CircleShape
                                            )
                                            .hapticPress(scaleDown = 0.9f)
                                            .clickable { viewModel.setAccentPalette(palette) }
                                            .semantics { contentDescription = palette.displayName },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = OnAccentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = accentPalette.displayName,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // SECTION 1: LYRICS ADJUSTMENTS & CUSTOMIZATION (Item 4)
                item {
                    SettingsSectionHeader(
                        icon = Icons.Default.Notes,
                        title = "Lyrics Customization",
                        subtitle = "Select providers, typography, and synchronization"
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 20.dp)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Lyrics Source Provider",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Provider Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                viewModel.availableLyricsProviders.forEach { provider ->
                                    val isSelected = lyricsProvider == provider
                                    val shortLabel = when (provider) {
                                        LyricsProvider.AUTO -> "Auto"
                                        LyricsProvider.LRCLIB -> "LRCLIB"
                                        LyricsProvider.LYRICS_OVH -> "Lyrics.ovh"
                                        LyricsProvider.NETEASE -> "NetEase"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) AccentColor else SurfaceColor)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) AccentColor else BorderColor,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { viewModel.setLyricsProvider(provider) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = shortLabel,
                                            color = if (isSelected) OnAccentColor else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                            // Lyrics Font Size
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Lyrics Font Size",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LyricsFontSize.values().forEach { size ->
                                        val isSelected = lyricsFontSize == size
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) AccentColor else SurfaceColor)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) AccentColor else BorderColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { viewModel.setLyricsFontSize(size) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = size.label,
                                                color = if (isSelected) OnAccentColor else TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                            // Auto Scroll Toggle
                            SettingsToggleRow(
                                title = "Auto-Scroll Synced Lyrics",
                                subtitle = "Smoothly follows audio playback in full player",
                                checked = lyricsAutoScroll,
                                onCheckedChange = { viewModel.toggleLyricsAutoScroll(it) }
                            )

                            // Offline Caching Toggle
                            SettingsToggleRow(
                                title = "Cache Lyrics for Offline",
                                subtitle = "Saves loaded lyrics locally with downloaded songs",
                                checked = offlineLyricsEnabled,
                                onCheckedChange = { viewModel.toggleOfflineLyrics(it) }
                            )
                        }
                    }
                }

                // SECTION 2: PLAYLIST SHARING, BACKUP & IMPORT/EXPORT (Item 5)
                item {
                    SettingsSectionHeader(
                        icon = Icons.Default.Share,
                        title = "Playlist Sharing & Transfer",
                        subtitle = "Serverless Base64 link sharing, import & CSV restore"
                    )
                }

                // Import Box
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 20.dp)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Import Playlist",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                TextButton(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { clipText ->
                                            viewModel.onImportInputChanged(clipText)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = AccentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Paste",
                                        color = AccentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Input Field
                            OutlinedTextField(
                                value = importInputText,
                                onValueChange = { viewModel.onImportInputChanged(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = "Paste auralis://playlist/... or Artist - Song list",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                },
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentColor,
                                    unfocusedBorderColor = BorderColor,
                                    focusedContainerColor = SurfaceColor,
                                    unfocusedContainerColor = SurfaceColor,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = AccentColor
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )

                            // Import Action Button
                            Button(
                                onClick = { viewModel.importPlaylistFromCurrentInput() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hapticPress(scaleDown = 0.96f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = OnAccentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Import to Library",
                                    color = OnAccentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Export Current Queue Box
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 20.dp)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Export Current Queue",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${currentQueue.size} songs currently loaded in queue",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val code = viewModel.exportQueueToBase64()
                                    if (code != null) {
                                        clipboardManager.setText(AnnotatedString(code))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceColor),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.hapticPress(scaleDown = 0.92f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = AccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy Code",
                                    color = AccentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Saved Playlists List
                if (userPlaylists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Saved & Shared Playlists (${userPlaylists.size})",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(userPlaylists, key = { it.id }) { pl ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .surfaceCard(cornerRadius = 16.dp)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pl.trackCount} tracks",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { viewModel.playSavedPlaylist(pl) },
                                        modifier = Modifier.size(34.dp).surfacePill().hapticPress(scaleDown = 0.88f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = AccentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val code = viewModel.exportSavedPlaylist(pl)
                                            clipboardManager.setText(AnnotatedString(code))
                                        },
                                        modifier = Modifier.size(34.dp).surfacePill().hapticPress(scaleDown = 0.88f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = TextPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteSavedPlaylist(pl.id) },
                                        modifier = Modifier.size(34.dp).surfacePill().hapticPress(scaleDown = 0.88f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = DangerColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3: PLAYBACK & ENGINE ADJUSTMENTS
                item {
                    SettingsSectionHeader(
                        icon = Icons.Default.GraphicEq,
                        title = "Playback Engine",
                        subtitle = "SponsorBlock auto-skip, Infinite radio, and Audio Bitrate"
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 20.dp)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Infinite Radio
                            SettingsToggleRow(
                                title = "Infinite Radio Autoplay",
                                subtitle = "Automatically queues similar tracks when current queue ends",
                                checked = infiniteRadioAutoplay,
                                onCheckedChange = { viewModel.toggleInfiniteRadio(it) }
                            )

                            if (viewModel.hasSegmentSkipper) {
                                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                                SettingsToggleRow(
                                    title = "SponsorBlock Music Auto-Skip",
                                    subtitle = "Auto-skips non-music video intros, dialogue, and sketches",
                                    checked = sponsorBlockEnabled,
                                    onCheckedChange = { viewModel.toggleSponsorBlock(it) }
                                )
                            }

                            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Streaming Quality",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Applies to catalogs that offer several bitrates",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AudioQualitySetting.values().forEach { quality ->
                                        val isSelected = audioQuality == quality
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) AccentColor else SurfaceColor)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) AccentColor else BorderColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { viewModel.setAudioQuality(quality) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (quality) {
                                                    AudioQualitySetting.HIGH -> "Best"
                                                    AudioQualitySetting.STANDARD -> "160 kbps"
                                                    AudioQualitySetting.DATA_SAVER -> "96 kbps"
                                                },
                                                color = if (isSelected) OnAccentColor else TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 4: TOGETHER
                item {
                    SettingsSectionHeader(
                        icon = Icons.Default.Favorite,
                        title = "Together",
                        subtitle = "Where your listening sessions are relayed, and who you are in them"
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 20.dp)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(
                                value = relayDraft,
                                onValueChange = {
                                    relayDraft = it
                                    relayRejected = false
                                },
                                label = { Text("Relay address", color = TextTertiary) },
                                singleLine = true,
                                isError = relayRejected,
                                supportingText = {
                                    Text(
                                        text = if (relayRejected) {
                                            "That is not an https address this app can reach"
                                        } else {
                                            "Your own worker from docs/RELAY.md, or leave it as it is"
                                        },
                                        fontSize = 11.sp,
                                        color = if (relayRejected) DangerColor else TextTertiary
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        relayRejected = !viewModel.setRelayUrl(relayDraft)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentColor,
                                        contentColor = OnAccentColor
                                    )
                                ) {
                                    Text("Save", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        relayDraft = RelayEndpoints.DEFAULT_BASE_URL
                                        relayRejected = !viewModel.setRelayUrl(relayDraft)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Reset", fontSize = 13.sp, color = TextSecondary)
                                }
                            }

                            HorizontalDivider(color = BorderColor)

                            OutlinedTextField(
                                value = nameDraft,
                                onValueChange = {
                                    nameDraft = it.take(64)
                                    viewModel.setTogetherDisplayName(nameDraft)
                                },
                                label = { Text("Your name in a session", color = TextTertiary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // SECTION 5: HAPTICS & TACTILE PHYSICS
                item {
                    SettingsSectionHeader(
                        icon = Icons.Default.Vibration,
                        title = "Tactile Physics & Feel",
                        subtitle = "Luxury haptic feedback on button taps and sliders"
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 20.dp)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HapticIntensity.values().forEach { intensity ->
                                val isSelected = hapticIntensity == intensity
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AccentColor else SurfaceColor)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) AccentColor else BorderColor,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.setHapticIntensity(intensity) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = intensity.label,
                                        color = if (isSelected) OnAccentColor else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .surfacePill(borderWidth = 1.dp)
                .background(AccentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccentColor,
                checkedTrackColor = AccentColor,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceColor
            ),
            modifier = Modifier.hapticPress(scaleDown = 0.90f)
        )
    }
}
