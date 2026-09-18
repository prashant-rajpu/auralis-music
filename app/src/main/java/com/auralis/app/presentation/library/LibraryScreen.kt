package com.auralis.app.presentation.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.auralis.app.domain.model.Track
import com.auralis.app.presentation.common.LocalAudioPermissionCard
import com.auralis.app.presentation.common.SectionHeader
import com.auralis.app.presentation.common.TrackContextMenuBottomSheet
import com.auralis.app.presentation.common.TrackRow
import com.auralis.app.presentation.explore.MessageCard
import com.auralis.app.ui.theme.*

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToArtist: (String) -> Unit
) {
    val section by viewModel.section.collectAsState()
    val state by viewModel.state.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val hasPermission by viewModel.hasLocalAudioPermission.collectAsState()

    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshLocalAudioPermission() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshLocalAudioPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibrarySection.entries.forEach { entry ->
                    val count = when (entry) {
                        LibrarySection.DOWNLOADS -> state.downloads.size
                        LibrarySection.ON_DEVICE -> state.onDevice.size
                        LibrarySection.RECENT -> recentTracks.size
                        LibrarySection.ARTISTS -> topArtists.size
                    }
                    SectionChip(
                        label = entry.label,
                        count = count,
                        isSelected = section == entry,
                        onClick = { viewModel.selectSection(entry) }
                    )
                }
            }
        }

        val tracks = when (section) {
            LibrarySection.DOWNLOADS -> state.downloads
            LibrarySection.ON_DEVICE -> state.onDevice
            LibrarySection.RECENT -> recentTracks
            LibrarySection.ARTISTS -> emptyList()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (section == LibrarySection.ON_DEVICE && !hasPermission) {
                item(key = "permission") {
                    LocalAudioPermissionCard(
                        onGrantClick = { permissionLauncher.launch(viewModel.localAudioPermission) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            when {
                section == LibrarySection.ARTISTS -> {
                    if (topArtists.isEmpty()) {
                        item(key = "no-artists") {
                            MessageCard("Play a few songs and the artists you return to show up here.")
                        }
                    } else {
                        item(key = "artists-header") {
                            SectionHeader(title = "Artists you keep coming back to")
                        }
                        items(topArtists, key = { it }) { artist ->
                            ArtistRow(artist = artist, onClick = { onNavigateToArtist(artist) })
                        }
                    }
                }

                state.isLoading && tracks.isEmpty() -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentColor)
                        }
                    }
                }

                state.error != null -> {
                    item(key = "error") {
                        MessageCard(state.error!!, actionLabel = "Try again", onAction = viewModel::refresh)
                    }
                }

                tracks.isEmpty() -> {
                    item(key = "empty") {
                        MessageCard(emptyMessage(section, hasPermission))
                    }
                }

                else -> {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { viewModel.play(track, tracks) },
                            onDownloadClick = { viewModel.toggleDownload(track) },
                            onMoreClick = { selectedTrack = track },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    selectedTrack?.let { track ->
        TrackContextMenuBottomSheet(
            track = track,
            onPlayNext = { viewModel.playNext(track); selectedTrack = null },
            onAddToQueue = { viewModel.addToQueue(track); selectedTrack = null },
            onStartRadio = { viewModel.startRadio(track); selectedTrack = null },
            onViewArtist = { artist -> selectedTrack = null; onNavigateToArtist(artist) },
            onToggleDownload = { viewModel.toggleDownload(track); selectedTrack = null },
            onDismiss = { selectedTrack = null }
        )
    }
}

private fun emptyMessage(section: LibrarySection, hasPermission: Boolean): String = when (section) {
    LibrarySection.DOWNLOADS -> "No downloads yet. Tap the download icon on any track to keep it offline."
    LibrarySection.ON_DEVICE ->
        if (hasPermission) "No audio files found on this device."
        else "Allow access to find music already on this device."
    LibrarySection.RECENT -> "Nothing played yet. Anything you listen to shows up here."
    LibrarySection.ARTISTS -> "Play a few songs and the artists you return to show up here."
}

@Composable
private fun SectionChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .hapticPress(scaleDown = 0.92f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) AccentColor else SurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else BorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = if (count > 0) "$label · $count" else label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) OnAccentColor else TextSecondary
        )
    }
}

@Composable
private fun ArtistRow(artist: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .surfaceCard(cornerRadius = 16.dp)
            .hapticPress(scaleDown = 0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = artist,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}
