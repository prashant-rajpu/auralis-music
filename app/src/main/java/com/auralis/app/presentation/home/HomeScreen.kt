package com.auralis.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.app.domain.model.Track
import com.auralis.app.presentation.common.SectionHeader
import com.auralis.app.presentation.common.TrackCard
import com.auralis.app.presentation.common.TrackContextMenuBottomSheet
import com.auralis.app.presentation.together.TogetherModeBottomSheet
import com.auralis.app.presentation.together.TogetherModeButton
import com.auralis.app.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val feed by viewModel.feed.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val jamSession by viewModel.jamSession.collectAsState()
    val isJamConnected by viewModel.isJamConnected.collectAsState()

    val (greeting, greetingSubtitle) = remember { viewModel.greeting() }

    var showTogetherSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item {
                HomeHeader(
                    greeting = greeting,
                    subtitle = greetingSubtitle,
                    topArtists = topArtists,
                    isTogetherActive = jamSession != null,
                    togetherPartner = jamSession?.participants?.firstOrNull { it != jamSession?.username },
                    onSettingsClick = onNavigateToSettings,
                    onTogetherClick = { showTogetherSheet = true }
                )
            }

            if (recentTracks.isNotEmpty()) {
                item(key = "recent") {
                    TrackShelf(
                        title = "Jump back in",
                        subtitle = "Where you left off",
                        tracks = recentTracks,
                        onTrackClick = { viewModel.play(it, recentTracks) },
                        onTrackMenu = { selectedTrack = it }
                    )
                }
            }

            when (val state = feed) {
                is HomeFeedState.Loading -> {
                    items(3) { ShelfPlaceholder() }
                }

                is HomeFeedState.Ready -> {
                    items(state.shelves, key = { it.id }) { shelf ->
                        TrackShelf(
                            title = shelf.title,
                            subtitle = shelf.subtitle,
                            tracks = shelf.tracks,
                            onTrackClick = { viewModel.play(it, shelf.tracks) },
                            onTrackMenu = { selectedTrack = it }
                        )
                    }
                }

                is HomeFeedState.Empty -> {
                    item {
                        EmptyFeedCard(message = state.message, onRetry = { viewModel.refresh() })
                    }
                }
            }
        }
    }

    if (showTogetherSheet) {
        TogetherModeBottomSheet(
            session = jamSession,
            isConnected = isJamConnected,
            onReconnect = { viewModel.reconnectJam() },
            onStartTogether = { code, name -> viewModel.startJam(code, name) },
            onJoinTogether = { code, name -> viewModel.joinJam(code, name) },
            onLeaveTogether = { viewModel.leaveJam() },
            onDismiss = { showTogetherSheet = false }
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

@Composable
private fun HomeHeader(
    greeting: String,
    subtitle: String,
    topArtists: List<String>,
    isTogetherActive: Boolean,
    togetherPartner: String?,
    onSettingsClick: () -> Unit,
    onTogetherClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        .size(36.dp)
                        .surfacePill(borderWidth = 1.dp)
                        .background(AccentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = OnAccentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Auralis",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .surfacePill()
                    .hapticPress(scaleDown = 0.9f)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 14.sp
            )
            if (topArtists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "On repeat: " + topArtists.take(3).joinToString(" · "),
                    color = AccentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        TogetherModeButton(
            onClick = onTogetherClick,
            isTogetherActive = isTogetherActive,
            partnerName = togetherPartner
        )
    }
}

@Composable
private fun TrackShelf(
    title: String,
    subtitle: String?,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onTrackMenu: (Track) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = title, subtitle = subtitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackCard(
                    track = track,
                    onClick = { onTrackClick(track) },
                    onMoreClick = { onTrackMenu(track) }
                )
            }
        }
    }
}

/** Grey tiles in the shape of a shelf, so the feed does not jump as rows arrive. */
@Composable
private fun ShelfPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(150.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceElevated)
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceElevated)
                )
            }
        }
    }
}

@Composable
private fun EmptyFeedCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .surfaceCard(cornerRadius = 20.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.hapticPress(scaleDown = 0.96f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = OnAccentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try again", color = OnAccentColor, fontWeight = FontWeight.Bold)
        }
    }
}
