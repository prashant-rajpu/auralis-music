package com.auralis.app.presentation.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.auralis.app.data.repository.PlaylistSummary
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
    var openPlaylistId: String? by rememberSaveable { mutableStateOf(null) }

    BackHandler(enabled = openPlaylistId != null) { openPlaylistId = null }

    val openPlaylist = openPlaylistId
    if (openPlaylist != null) {
        PlaylistDetail(
            playlistId = openPlaylist,
            viewModel = viewModel,
            onBack = { openPlaylistId = null },
            onNavigateToArtist = onNavigateToArtist
        )
    } else {
        LibraryRoot(
            viewModel = viewModel,
            onOpenPlaylist = { openPlaylistId = it },
            onNavigateToArtist = onNavigateToArtist
        )
    }
}

@Composable
private fun LibraryRoot(
    viewModel: LibraryViewModel,
    onOpenPlaylist: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val section by viewModel.section.collectAsState()
    val filesState by viewModel.filesState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val hasPermission by viewModel.hasLocalAudioPermission.collectAsState()

    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                if (section == LibrarySection.PLAYLISTS) {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .surfacePill()
                            .hapticPress(scaleDown = 0.9f)
                    ) {
                        Icon(Icons.Default.Add, "New playlist", tint = AccentColor, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibrarySection.entries.forEach { entry ->
                    val count = when (entry) {
                        LibrarySection.PLAYLISTS -> playlists.size
                        LibrarySection.LIKED -> likedTracks.size
                        LibrarySection.DOWNLOADS -> filesState.downloads.size
                        LibrarySection.ON_DEVICE -> filesState.onDevice.size
                        LibrarySection.RECENT -> recentlyPlayed.size
                        LibrarySection.MOST_PLAYED -> mostPlayed.size
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
            LibrarySection.LIKED -> likedTracks
            LibrarySection.DOWNLOADS -> filesState.downloads
            LibrarySection.ON_DEVICE -> filesState.onDevice
            LibrarySection.RECENT -> recentlyPlayed
            LibrarySection.MOST_PLAYED -> mostPlayed
            LibrarySection.PLAYLISTS, LibrarySection.ARTISTS -> emptyList()
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

            when (section) {
                LibrarySection.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        item(key = "no-playlists") {
                            MessageCard(
                                "No playlists yet. Make one and add anything you like to it.",
                                actionLabel = "New playlist",
                                onAction = { showCreateDialog = true }
                            )
                        }
                    } else {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist.id) },
                                onRename = { renaming = playlist },
                                onDelete = { viewModel.deletePlaylist(playlist.id) }
                            )
                        }
                    }
                }

                LibrarySection.ARTISTS -> {
                    if (topArtists.isEmpty()) {
                        item(key = "no-artists") {
                            MessageCard("Play a few songs and the artists you return to show up here.")
                        }
                    } else {
                        item(key = "artists-header") { SectionHeader(title = "Artists you keep coming back to") }
                        items(topArtists, key = { it }) { artist ->
                            ArtistRow(artist = artist, onClick = { onNavigateToArtist(artist) })
                        }
                    }
                }

                else -> {
                    if (filesState.isLoading && tracks.isEmpty() && section.readsFiles()) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(color = AccentColor) }
                        }
                    } else if (filesState.error != null && section.readsFiles()) {
                        item(key = "error") {
                            MessageCard(filesState.error!!, actionLabel = "Try again", onAction = viewModel::refreshFiles)
                        }
                    } else if (tracks.isEmpty()) {
                        item(key = "empty") { MessageCard(emptyMessage(section, hasPermission)) }
                    } else {
                        item(key = "play-all") {
                            PlayAllRow(count = tracks.size, onPlayAll = { viewModel.playAll(tracks) })
                        }
                        items(tracks, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                onClick = { viewModel.play(track, tracks) },
                                onDownloadClick = { viewModel.toggleDownload(track) },
                                onMoreClick = { selectedTrack = track },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        if (section == LibrarySection.RECENT) {
                            item(key = "clear-history") {
                                TextButton(
                                    onClick = { viewModel.clearHistory() },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Text("Clear listening history", color = DangerColor, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            initialName = "",
            onConfirm = { viewModel.createPlaylist(it); showCreateDialog = false },
            onDismiss = { showCreateDialog = false }
        )
    }

    renaming?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = playlist.name,
            onConfirm = { viewModel.renamePlaylist(playlist.id, it); renaming = null },
            onDismiss = { renaming = null }
        )
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

/** Only these sections depend on the filesystem scan, so only they should show its spinner. */
private fun LibrarySection.readsFiles(): Boolean =
    this == LibrarySection.DOWNLOADS || this == LibrarySection.ON_DEVICE

@Composable
private fun PlaylistDetail(
    playlistId: String,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val tracks by viewModel.playlistTracks(playlistId).collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.hapticPress(scaleDown = 0.9f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (tracks.isEmpty()) {
                item(key = "empty") {
                    MessageCard("Nothing here yet. Add tracks from the ⋮ menu on any song.")
                }
            } else {
                item(key = "play-all") {
                    PlayAllRow(count = tracks.size, onPlayAll = { viewModel.playAll(tracks) })
                }
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

    selectedTrack?.let { track ->
        TrackContextMenuBottomSheet(
            track = track,
            onPlayNext = { viewModel.playNext(track); selectedTrack = null },
            onAddToQueue = { viewModel.addToQueue(track); selectedTrack = null },
            onStartRadio = { viewModel.startRadio(track); selectedTrack = null },
            onViewArtist = { artist -> selectedTrack = null; onNavigateToArtist(artist) },
            onToggleDownload = { viewModel.toggleDownload(track); selectedTrack = null },
            onRemoveFromPlaylist = {
                viewModel.removeFromPlaylist(playlistId, track.id)
                selectedTrack = null
            },
            onDismiss = { selectedTrack = null }
        )
    }
}

@Composable
private fun PlayAllRow(count: Int, onPlayAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onPlayAll,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.hapticPress(scaleDown = 0.96f)
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = OnAccentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play all", color = OnAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text("$count ${if (count == 1) "track" else "tracks"}", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .surfaceCard(cornerRadius = 16.dp)
            .hapticPress(scaleDown = 0.98f)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceHighest),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverArtUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(playlist.coverArtUrl).crossfade(250).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.QueueMusic, null, tint = AccentColor, modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.hapticPress(scaleDown = 0.85f)) {
                Icon(Icons.Default.MoreVert, "Playlist options", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = SurfaceElevated
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextSecondary) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = DangerColor) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = DangerColor) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        title = { Text(title, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name", color = TextTertiary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = BorderColor,
                    cursorColor = AccentColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = if (name.isNotBlank()) AccentColor else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

private fun emptyMessage(section: LibrarySection, hasPermission: Boolean): String = when (section) {
    LibrarySection.PLAYLISTS -> "No playlists yet."
    LibrarySection.LIKED -> "Nothing liked yet. Tap the heart on any track and it stays here."
    LibrarySection.DOWNLOADS -> "No downloads yet. Tap the download icon on any track to keep it offline."
    LibrarySection.ON_DEVICE ->
        if (hasPermission) "No audio files found on this device."
        else "Allow access to find music already on this device."
    LibrarySection.RECENT -> "Nothing played yet. Anything you listen to shows up here."
    LibrarySection.MOST_PLAYED -> "Play a few songs and your favourites collect here."
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
            Icon(Icons.Default.Person, null, tint = AccentColor, modifier = Modifier.size(22.dp))
        }
        Text(text = artist, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
