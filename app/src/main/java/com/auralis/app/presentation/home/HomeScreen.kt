package com.auralis.app.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val hasLocalAudioPermission by viewModel.hasLocalAudioPermission.collectAsState()

    var showJamDialog by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshLocalAudioPermission() }

    // The user can grant the permission in system settings and come back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshLocalAudioPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBrush)
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
                                .surfacePill(borderWidth = 1.dp)
                                .background(AccentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Logo",
                                tint = OnAccentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Auralis",
                            color = TextPrimary,
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
                                .surfacePill(borderWidth = 1.dp)
                                .hapticPress(scaleDown = 0.88f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings & Adjustments",
                                tint = TextPrimary,
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
                                .background(if (isSearchExpanded) AccentColor else SurfaceElevated)
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(BorderHighlight, BorderColor)
                                    ),
                                    CircleShape
                                )
                                .hapticPress(scaleDown = 0.88f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchExpanded) OnAccentColor else TextPrimary,
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
                                        Brush.horizontalGradient(listOf(AccentColor, AccentColorBright))
                                    } else {
                                        Brush.linearGradient(listOf(SurfaceElevated, SurfaceColor))
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        if (isSelected) {
                                            listOf(OnAccentColor.copy(alpha = 0.55f), AccentColor)
                                        } else {
                                            listOf(BorderHighlight, BorderColor)
                                        }
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { viewModel.selectMood(mood) }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = mood,
                                color = if (isSelected) OnAccentColor else TextPrimary,
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
                                Text("Search songs, albums, artists, YouTube...", color = TextSecondary, fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = AccentColor)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onSearchQueryChange("") },
                                        modifier = Modifier.hapticPress(scaleDown = 0.88f)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentColor,
                                unfocusedBorderColor = BorderColor,
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceElevated,
                                cursorColor = AccentColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(BorderHighlight, BorderColor)
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
                                                Brush.horizontalGradient(listOf(AccentColor, AccentColorBright))
                                            } else {
                                                Brush.linearGradient(listOf(SurfaceElevated, SurfaceColor))
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) OnAccentColor.copy(alpha = 0.6f) else BorderColor,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { viewModel.selectSourceFilter(source) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = source?.displayName ?: "All",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) OnAccentColor else TextPrimary
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
                            .surfaceCard(cornerRadius = 18.dp)
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
                                    .background(AccentColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "💗 Together with $partner • Laddu Sync",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = lastJamAction ?: "Listening together in real-time sync",
                                    color = AccentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "Manage 💗",
                            color = TextPrimary,
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
                            CircularProgressIndicator(color = AccentColor)
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
                                if (selectedTab == HomeTab.Downloaded && !hasLocalAudioPermission) {
                                    item {
                                        LocalAudioPermissionCard(
                                            onGrantClick = { permissionLauncher.launch(viewModel.localAudioPermission) }
                                        )
                                    }
                                }
                                item {
                                    Text(
                                        text = if (selectedTab == HomeTab.Downloaded) "Library (${tracks.size})" else "Results for '$searchQuery'",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary,
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
                            // Shelves / carousels feed
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
                                            .surfaceCard(cornerRadius = 20.dp)
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = timeGreeting,
                                            color = AccentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = timeSubtitle,
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (topArtists.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Favorites: " + topArtists.take(3).joinToString(" • "),
                                                color = TextPrimary,
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
                                                    tint = AccentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Auralis Pro Features",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = TextPrimary
                                                )
                                            }
                                            Text(
                                                text = "Quick Studio",
                                                color = AccentColor,
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
                                                        .surfaceCard(cornerRadius = 18.dp)
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
                                                                    .background(AccentColor),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Favorite,
                                                                    contentDescription = null,
                                                                    tint = OnAccentColor,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = if (jamSession != null) "SYNCED 🟢" else "START 💗",
                                                                color = AccentColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Listen Together",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Sync music live with partner",
                                                            color = TextSecondary,
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
                                                        .surfaceCard(cornerRadius = 18.dp)
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
                                                                    .background(if (isTimerActive) AccentColor else SurfaceElevated),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Bedtime,
                                                                    contentDescription = null,
                                                                    tint = if (isTimerActive) OnAccentColor else AccentColor,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = if (isTimerActive) "${sleepTimerMinutesRemaining}m" else "TIMER",
                                                                color = AccentColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Sleep Timer",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Gentle 10s volume fade-out",
                                                            color = TextSecondary,
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
                                                        .surfaceCard(cornerRadius = 18.dp)
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
                                                                    .background(if (isSpeedActive) AccentColor else SurfaceElevated),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Speed,
                                                                    contentDescription = null,
                                                                    tint = if (isSpeedActive) OnAccentColor else AccentColor,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = "${playbackSpeed}x",
                                                                color = AccentColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Playback Speed",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "0.75x–2.0x pitch preserved",
                                                            color = TextSecondary,
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
                                                        .surfaceCard(cornerRadius = 18.dp)
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
                                                                    .background(SurfaceElevated),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Tune,
                                                                    contentDescription = null,
                                                                    tint = AccentColor,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = "CUSTOMIZE",
                                                                color = AccentColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Appearance & FX",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Theme, lyrics & radio",
                                                            color = TextSecondary,
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
                                                        .surfaceCard(cornerRadius = 18.dp)
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
                                                                    .background(if (isInfiniteRadioAutoplayEnabled) AccentColor else SurfaceElevated),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Radio,
                                                                    contentDescription = null,
                                                                    tint = if (isInfiniteRadioAutoplayEnabled) OnAccentColor else AccentColor,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = if (isInfiniteRadioAutoplayEnabled) "ACTIVE 📻" else "PAUSED",
                                                                color = if (isInfiniteRadioAutoplayEnabled) AccentColor else TextSecondary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Infinite Radio",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = if (isInfiniteRadioAutoplayEnabled) "Endless smart stream ON" else "Tap to enable autoplay",
                                                            color = TextSecondary,
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
                                                        tint = AccentColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Jump Back In",
                                                        style = MaterialTheme.typography.titleLarge.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = TextPrimary
                                                    )
                                                }
                                                Text(
                                                    text = "Recently Played",
                                                    color = TextSecondary,
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
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = if (selectedMood == "All") "Start radio from a song" else "$selectedMood Vibes",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = TextPrimary
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
                                                color = TextPrimary,
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
                                                color = TextPrimary,
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
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                if (selectedTab == HomeTab.Downloaded && !hasLocalAudioPermission) {
                                    LocalAudioPermissionCard(
                                        onGrantClick = { permissionLauncher.launch(viewModel.localAudioPermission) }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                Button(
                                    onClick = { viewModel.loadTrendingTracks() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = OnAccentColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry", color = OnAccentColor, fontWeight = FontWeight.Bold)
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
            .surfaceCard(cornerRadius = 16.dp)
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
                .background(BackgroundElevated)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = TextPrimary,
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
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(text = "•", color = TextSecondary, fontSize = 11.sp)
                Text(text = track.source, color = TextSecondary, fontSize = 11.sp)
            }
        }

        if (track.provider != Provider.LOCAL) {
        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier.hapticPress(scaleDown = 0.85f)
        ) {
            if (track.isDownloaded) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = AccentColor,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.hapticPress(scaleDown = 0.85f)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More Options",
                tint = TextSecondary,
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
                .artworkFrame(outerRadius = 20.dp)
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
                            listOf(BorderHighlight, AccentColorSoft.copy(0.4f))
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = track.qualityBadge,
                    color = AccentColor,
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
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LocalAudioPermissionCard(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .surfaceCard(cornerRadius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Play music on this device",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Text(
            text = "Allow access to your audio files and they appear here alongside your downloads.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Button(
            onClick = onGrantClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.hapticPress(scaleDown = 0.96f)
        ) {
            Text("Allow access", color = OnAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
