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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auralis.app.domain.model.Track
import com.auralis.app.presentation.jam.SpotifyJamBottomSheet
import com.auralis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sourceFilter by viewModel.sourceFilter.collectAsState()
    val selectedMood by viewModel.selectedMood.collectAsState()
    val jamSession by viewModel.jamSession.collectAsState()
    val lastJamAction by viewModel.lastJamAction.collectAsState()

    var showJamDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

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
                                .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = PlayButtonGlowPink)
                                .clip(CircleShape)
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

                    // Action Buttons: Jam Session & Search Toggle (Glass Pills)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showJamDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GlassSurfaceStrong)
                                .border(1.dp, GlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Live Jam",
                                tint = if (jamSession != null) BabyPinkPrimary else BabyPinkTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { isSearchExpanded = !isSearchExpanded },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSearchExpanded) BabyPinkPrimary else GlassSurfaceStrong)
                                .border(1.dp, GlassBorder, CircleShape)
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

                // Mood Filter Pills Bar (Horizontally scrollable glass pills)
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
                                .shadow(
                                    elevation = if (isSelected) 4.dp else 0.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    ambientColor = PlayButtonGlowPink
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) BabyPinkPrimary else GlassSurfaceStrong)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) BabyPinkPrimary else GlassBorder,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { viewModel.selectMood(mood) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
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

                // Frosted Glass Search Bar & Filters (Pill Shape)
                if (isSearchExpanded || searchQuery.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = {
                                Text("Search songs, albums, artists...", color = BabyPinkTextSecondary, fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = BabyPinkPrimary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = BabyPinkTextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = BabyPinkPrimary,
                                unfocusedBorderColor = GlassBorder,
                                containerColor = GlassSurfaceStrong,
                                textColor = BabyPinkTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filter Chips: All, Top Hits, 320 kbps Master, Lossless, Acoustic
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sources = listOf("All", "Top Hits", "320 kbps Master", "Lossless", "Acoustic")
                            sources.forEach { source ->
                                val isSelected = sourceFilter.equals(source, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) BabyPinkPrimary else GlassSurface)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                        .clickable { viewModel.selectSourceFilter(source) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = source,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else BabyPinkTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Active Spotify Jam Frosted Banner
                if (jamSession != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp), ambientColor = PlayButtonGlowPink)
                            .clip(RoundedCornerShape(18.dp))
                            .background(GlassSurfaceStrong)
                            .border(1.2.dp, GlassBorder, RoundedCornerShape(18.dp))
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
                                    text = "🎧 Spotify Jam Active: ${jamSession!!.jamId}",
                                    color = BabyPinkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = lastJamAction ?: "${jamSession!!.participants.size} listening together in sync",
                                    color = BabyPinkPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "Manage",
                            color = BabyPinkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Spotify Jam Bottom Sheet
                if (showJamDialog) {
                    SpotifyJamBottomSheet(
                        session = jamSession,
                        onStartJam = { code, name ->
                            viewModel.startJam(code, name)
                            showJamDialog = false
                        },
                        onJoinJam = { code, name ->
                            viewModel.joinJam(code, name)
                            showJamDialog = false
                        },
                        onLeaveJam = {
                            viewModel.leaveJam()
                        },
                        onDismiss = { showJamDialog = false }
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
                                        text = if (selectedTab == HomeTab.Downloaded) "Offline Downloads (${tracks.size})" else "Results for '$searchQuery'",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = BabyPinkTextPrimary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(tracks, key = { it.id }) { track ->
                                    GlassTrackListItem(
                                        track = track,
                                        onClick = { onTrackClick(track) },
                                        onDownloadClick = { viewModel.toggleDownload(track) }
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
                                                        onDownloadClick = { viewModel.toggleDownload(track) }
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
                                                        onClick = { onTrackClick(track) }
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
                                                        onClick = { onTrackClick(track) }
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
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp), ambientColor = PlayButtonGlowPink)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurfaceStrong)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.albumArtUrl,
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

        IconButton(onClick = onDownloadClick) {
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
    }
}

@Composable
fun GlassMusicCardItem(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp), ambientColor = PlayButtonGlowPink)
                .clip(RoundedCornerShape(18.dp))
                .background(GlassSurfaceStrong)
                .border(1.2.dp, GlassBorder, RoundedCornerShape(18.dp))
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle quality chip overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCCFFFFFF))
                    .border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = track.qualityBadge,
                    color = BabyPinkPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
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
