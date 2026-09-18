package com.auralis.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.presentation.common.TrackContextMenuBottomSheet
import com.auralis.app.presentation.player.PlaybackSpeedBottomSheet
import com.auralis.app.presentation.player.SleepTimerBottomSheet
import com.auralis.app.presentation.together.*
import com.auralis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sourceFilter by viewModel.sourceFilter.collectAsState()
    val selectedMood by viewModel.selectedMood.collectAsState()
    val jamSession by viewModel.jamSession.collectAsState()
    val lastJamAction by viewModel.lastJamAction.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val (timeGreeting, timeSubtitle) = remember { viewModel.getTimeOfDayGreeting() }
    val isJamConnected by viewModel.isJamConnected.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val sleepTimerMinutesRemaining by viewModel.sleepTimerMinutesRemaining.collectAsState()
    val isInfiniteRadioAutoplayEnabled by viewModel.isInfiniteRadioAutoplayEnabled.collectAsState()

    var showJamDialog by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BabyPinkBackgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                // Glassmorphism Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Soft Romantic Branding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .glassPill(borderWidth = 1.dp)
                                .background(BabyPinkPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Auralis",
                            color = BabyPinkTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Action Buttons: Jam Session & Search Toggle (Glass Pills with tactile bounce)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(38.dp)
                                .glassPill(borderWidth = 1.dp)
                                .hapticPress(scaleDown = 0.88f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings & Adjustments",
                                tint = BabyPinkTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        TogetherModeButton(
                            onClick = { showJamDialog = true },
                            isTogetherActive = jamSession != null,
                            partnerName = jamSession?.participants?.firstOrNull { it != jamSession?.username } ?: "Laddu"
                        )

                        IconButton(
                            onClick = { isSearchExpanded = !isSearchExpanded },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSearchExpanded) BabyPinkPrimary else GlassSurfaceStrong)
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(0.85f), Color.White.copy(0.25f))
                                    ),
                                    CircleShape
                                )
                                .hapticPress(scaleDown = 0.88f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchExpanded) Color.White else BabyPinkTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Mood Filter Pills Bar (Horizontally scrollable glass pills with fluid tactile squeeze)
                val moodList = listOf("All", "Energize", "Workout", "Relax", "Focus", "Party", "Romance")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    moodList.forEach { mood ->
                        val isSelected = selectedMood == mood
                        Box(
                            modifier = Modifier
                                .hapticPress(scaleDown = 0.93f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.horizontalGradient(listOf(BabyPinkPrimary, BabyPinkAccent))
                                    } else {
                                        Brush.linearGradient(listOf(GlassSurfaceStrong, GlassSurface))
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        if (isSelected) {
                                            listOf(Color.White.copy(0.85f), BabyPinkPrimary)
                                        } else {
                                            listOf(Color.White.copy(0.70f), Color.White.copy(0.15f))
                                        }
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { viewModel.selectMood(mood) }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = mood,
                                color = if (isSelected) Color.White else BabyPinkTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Frosted Glass Search Bar & Filters (Smooth animated expansion)
                AnimatedVisibility(
                    visible = isSearchExpanded || searchQuery.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = {
                                Text("Search songs, albums, artists, YouTube...", color = BabyPinkTextSecondary, fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = BabyPinkPrimary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onSearchQueryChange("") },
                                        modifier = Modifier.hapticPress(scaleDown = 0.88f)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = BabyPinkTextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BabyPinkPrimary,
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = GlassSurfaceStrong,
                                unfocusedContainerColor = GlassSurfaceStrong,
                                cursorColor = BabyPinkPrimary,
                                focusedTextColor = BabyPinkTextPrimary,
                                unfocusedTextColor = BabyPinkTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(Color.White.copy(0.85f), Color.White.copy(0.20f))
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Catalog filter chips: All plus every online source in this build
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sources: List<Provider?> = listOf(null) + viewModel.availableSources
                            sources.forEach { source ->
                                val isSelected = sourceFilter == source
                                Box(
                                    modifier = Modifier
                                        .hapticPress(scaleDown = 0.92f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.horizontalGradient(listOf(BabyPinkPrimary, BabyPinkAccent))
                                            } else {
                                                Brush.linearGradient(listOf(GlassSurfaceStrong, GlassSurface))
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.White.copy(0.85f) else GlassBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { viewModel.selectSourceFilter(source) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = source?.displayName ?: "All",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else BabyPinkTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Active Together Mode Frosted Banner (Section 11.2 & 11.3)
                if (jamSession != null) {
                    val partner = jamSession!!.participants.firstOrNull { it != jamSession!!.username } ?: "Laddu"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .glassCard(cornerRadius = 18.dp)
                            .hapticPress(scaleDown = 0.98f)
                            .clickable { showJamDialog = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(BabyPinkPrimary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "💗 Together with $partner • Laddu Sync",
                                    color = BabyPinkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = lastJamAction ?: "Listening together in real-time sync",
                                    color = BabyPinkPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "Manage 💗",
                            color = BabyPinkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Together Mode Bottom Sheet Modal
                if (showJamDialog) {
                    TogetherModeBottomSheet(
                        session = jamSession,
                        isConnected = isJamConnected,
                        onReconnect = { viewModel.reconnectJam() },
                        onStartTogether = { code, name ->
                            viewModel.startJam(code, name)
                        },
                        onJoinTogether = { code, name ->
                            viewModel.joinJam(code, name)
                        },
                        onLeaveTogether = {
                            viewModel.leaveJam()
                        },
                        onDismiss = { showJamDialog = false }
                    )
                }

                // Sleep Timer Modal from Home
                if (showSleepTimerSheet) {
                    SleepTimerBottomSheet(
                        remainingMinutes = sleepTimerMinutesRemaining,
                        onSetTimer = { viewModel.setSleepTimer(it) },
                        onDismiss = { showSleepTimerSheet = false }
                    )
                }

                // Playback Speed Modal from Home
                if (showSpeedSheet) {
                    PlaybackSpeedBottomSheet(
                        currentSpeed = playbackSpeed,
                        onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                        onDismiss = { showSpeedSheet = false }
                    )
                }

                // Track Context Menu (Play Next, Add to Queue, Radio, Share, Download)
                if (selectedTrackForMenu != null) {
                    val menuTrack = selectedTrackForMenu!!
                    TrackContextMenuBottomSheet(
                        track = menuTrack,
                        onPlayNext = { viewModel.playNext(menuTrack) },
                        onAddToQueue = { viewModel.addToQueue(menuTrack) },
                        onStartRadio = { viewModel.startRadio(menuTrack) },
                        onViewArtist = { onNavigateToArtist(it) },
                        onToggleDownload = { viewModel.toggleDownload(menuTrack) },
                        onDismiss = { selectedTrackForMenu = null }
                    )
                }

                // Main Feed Area
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BabyPinkPrimary)
                        }
                    }

                    is HomeUiState.Success -> {
                        val tracks = state.tracks
                        if (searchQuery.isNotBlank() || selectedTab == HomeTab.Downloaded) {
                            // Search Results or Offline Library List View
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                item {
                                    Text(
                                        text = if (selectedTab == HomeTab.Downloaded) "Library (${tracks.size})" else "Results for '$searchQuery'",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = BabyPinkTextPrimary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(tracks, key = { it.id }) { track ->
                                    GlassTrackListItem(
                                        track = track,
                                        onClick = { onTrackClick(track) },
                                        onDownloadClick = { viewModel.toggleDownload(track) },
                                        onMoreClick = { selectedTrackForMenu = track }
                                    )
                                }
                            }
                        } else {
                            // Glassmorphism Baby Pink Shelves / Carousels Feed
                            val quickPicks = tracks.take(4)
                            val trendingNow = tracks.drop(4).take(6)
                            val recommended = tracks.drop(10)

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
                            ) {
                                // Section 0: Time-of-Day Personalized Greeting & Mood Vibe
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .glassCard(cornerRadius = 20.dp)
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = timeGreeting,
                                            color = BabyPinkPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = timeSubtitle,
                                            color = BabyPinkTextSecondary,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (topArtists.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Favorites: " + topArtists.take(3).joinToString(" • "),
                                                color = BabyPinkTextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Section 0.3: Auralis Studio & Features Shelf
                                item {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = BabyPinkPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Auralis Pro Features",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = BabyPinkTextPrimary
                                                )
                                            }
                                            Text(
                                                text = "Quick Studio",
                                                color = BabyPinkPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // 1. Together Mode (Laddu Sync)
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .width(180.dp)
                                                        .glassCard(cornerRadius = 18.dp)
                                                        .hapticPress(scaleDown = 0.94f)
                                                        .clickable { showJamDialog = true }
                                                        .padding(12.dp)
                                                ) {
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(CircleShape)
                                                                    .background(BabyPinkPrimary),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Favorite,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = if (jamSession != null) "SYNCED 🟢" else "START 💗",
                                                                color = BabyPinkPrimary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Listen Together",
                                                            color = BabyPinkTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Sync music live with partner",
                                                            color = BabyPinkTextSecondary,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // 2. Sleep Timer
                                            item {
                                                val isTimerActive = sleepTimerMinutesRemaining != null
                                                Box(
                                                    modifier = Modifier
                                                        .width(170.dp)
                                                        .glassCard(cornerRadius = 18.dp)
                                                        .hapticPress(scaleDown = 0.94f)
                                                        .clickable { showSleepTimerSheet = true }
                                                        .padding(12.dp)
                                                ) {
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isTimerActive) BabyPinkPrimary else GlassSurfaceStrong),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Bedtime,
                                                                    contentDescription = null,
                                                                    tint = if (isTimerActive) Color.White else BabyPinkPrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = if (isTimerActive) "${sleepTimerMinutesRemaining}m" else "TIMER",
                                                                color = BabyPinkPrimary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Sleep Timer",
                                                            color = BabyPinkTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Gentle 10s volume fade-out",
                                                            color = BabyPinkTextSecondary,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // 3. Playback Speed
                                            item {
                                                val isSpeedActive = playbackSpeed != 1.0f
                                                Box(
                                                    modifier = Modifier
                                                        .width(170.dp)
                                                        .glassCard(cornerRadius = 18.dp)
                                                        .hapticPress(scaleDown = 0.94f)
                                                        .clickable { showSpeedSheet = true }
                                                        .padding(12.dp)
                                                ) {
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSpeedActive) BabyPinkPrimary else GlassSurfaceStrong),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Speed,
                                                                    contentDescription = null,
                                                                    tint = if (isSpeedActive) Color.White else BabyPinkPrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = "${playbackSpeed}x",
                                                                color = BabyPinkPrimary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Playback Speed",
                                                            color = BabyPinkTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "0.75x–2.0x pitch preserved",
                                                            color = BabyPinkTextSecondary,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // 4. Customization & Settings
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .width(170.dp)
                                                        .glassCard(cornerRadius = 18.dp)
                                                        .hapticPress(scaleDown = 0.94f)
                                                        .clickable { onNavigateToSettings() }
                                                        .padding(12.dp)
                                                ) {
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(CircleShape)
                                                                    .background(GlassSurfaceStrong),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Tune,
                                                                    contentDescription = null,
                                                                    tint = BabyPinkPrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = "CUSTOMIZE",
                                                                color = BabyPinkPrimary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Pink Themes & FX",
                                                            color = BabyPinkTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Theme, lyrics & radio",
                                                            color = BabyPinkTextSecondary,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // 5. Infinite Radio Autoplay
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .width(170.dp)
                                                        .glassCard(cornerRadius = 18.dp)
                                                        .hapticPress(scaleDown = 0.94f)
                                                        .clickable {
                                                            viewModel.toggleInfiniteRadioAutoplay(!isInfiniteRadioAutoplayEnabled)
                                                        }
                                                        .padding(12.dp)
                                                ) {
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isInfiniteRadioAutoplayEnabled) BabyPinkPrimary else GlassSurfaceStrong),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Radio,
                                                                    contentDescription = null,
                                                                    tint = if (isInfiniteRadioAutoplayEnabled) Color.White else BabyPinkPrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = if (isInfiniteRadioAutoplayEnabled) "ACTIVE 📻" else "PAUSED",
                                                                color = if (isInfiniteRadioAutoplayEnabled) BabyPinkPrimary else BabyPinkTextSecondary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Infinite Radio",
                                                            color = BabyPinkTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = if (isInfiniteRadioAutoplayEnabled) "Endless smart stream ON" else "Tap to enable autoplay",
                                                            color = BabyPinkTextSecondary,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Section 0.5: Jump Back In (Recently Played from Personalization Engine)
                                if (recentTracks.isNotEmpty()) {
                                    item {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.History,
                                                        contentDescription = null,
                                                        tint = BabyPinkPrimary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Jump Back In",
                                                        style = MaterialTheme.typography.titleLarge.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = BabyPinkTextPrimary
                                                    )
                                                }
                                                Text(
                                                    text = "Recently Played",
                                                    color = BabyPinkTextSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                items(recentTracks, key = { "recent_${it.id}" }) { track ->
                                                    GlassMusicCardItem(
                                                        track = track,
                                                        onClick = { onTrackClick(track) },
                                                        onMoreClick = { selectedTrackForMenu = track }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Section 1: Quick Picks Shelf (Frosted Glass Cards)
                                if (quickPicks.isNotEmpty()) {
                                    item {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                            Text(
                                                text = "QUICK PICKS",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    letterSpacing = 1.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = BabyPinkTextSecondary
                                            )
                                            Text(
                                                text = if (selectedMood == "All") "Start radio from a song" else "$selectedMood Vibes",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = BabyPinkTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                quickPicks.forEach { track ->
                                                    GlassTrackListItem(
                                                        track = track,
                                                        onClick = { onTrackClick(track) },
                                                        onDownloadClick = { viewModel.toggleDownload(track) },
                                                        onMoreClick = { selectedTrackForMenu = track }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Section 2: Trending Now (Horizontal Frosted Glass Cards Shelf)
                                if (trendingNow.isNotEmpty()) {
                                    item {
                                        Column {
                                            Text(
                                                text = "Trending Now",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = BabyPinkTextPrimary,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                items(trendingNow, key = { it.id }) { track ->
                                                    GlassMusicCardItem(
                                                        track = track,
                                                        onClick = { onTrackClick(track) },
                                                        onMoreClick = { selectedTrackForMenu = track }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Section 3: Recommended For You (Horizontal Shelf)
                                if (recommended.isNotEmpty()) {
                                    item {
                                        Column {
                                            Text(
                                                text = "Recommended For You",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = BabyPinkTextPrimary,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                items(recommended, key = { it.id }) { track ->
                                                    GlassMusicCardItem(
                                                        track = track,
                                                        onClick = { onTrackClick(track) },
                                                        onMoreClick = { selectedTrackForMenu = track }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is HomeUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.message,
                                    color = BabyPinkTextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Button(
                                    onClick = { viewModel.loadTrendingTracks() },
                                    colors = ButtonDefaults.buttonColors(containerColor = BabyPinkPrimary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
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
fun GlassTrackListItem(
    track: Track,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp)
            .hapticPress(scaleDown = 0.98f)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(track.albumArtUrl)
                .crossfade(250)
                .build(),
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BabyPinkBgMiddle)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = BabyPinkTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist,
                    color = BabyPinkTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(text = "•", color = BabyPinkTextSecondary, fontSize = 11.sp)
                Text(text = track.source, color = BabyPinkTextSecondary, fontSize = 11.sp)
            }
        }

        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier.hapticPress(scaleDown = 0.85f)
        ) {
            if (track.isDownloaded) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = BabyPinkPrimary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = BabyPinkTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.hapticPress(scaleDown = 0.85f)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More Options",
                tint = BabyPinkTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun GlassMusicCardItem(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .hapticPress(scaleDown = 0.96f)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .doubleBezelCard(outerRadius = 20.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.albumArtUrl)
                    .crossfade(250)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )
            // Subtle frosted glass quality chip overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xD9FFFFFF))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White, BabyPinkSoftRose.copy(0.4f))
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = track.qualityBadge,
                    color = BabyPinkPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xB3FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = BabyPinkTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            color = BabyPinkTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = BabyPinkTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
