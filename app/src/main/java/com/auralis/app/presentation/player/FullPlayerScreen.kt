package com.auralis.app.presentation.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.auralis.app.domain.model.Track
import com.auralis.app.playback.RepeatMode
import com.auralis.app.presentation.common.TrackContextMenuBottomSheet
import com.auralis.app.presentation.together.*
import com.auralis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {}
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val isShuffle by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val dislikedTrackIds by viewModel.dislikedTrackIds.collectAsState()
    val isSoundProfilesVisible by viewModel.isSoundProfilesVisible.collectAsState()
    val soundProfile by viewModel.soundProfile.collectAsState()
    val jamSession by viewModel.jamSession.collectAsState()
    val isJamSheetVisible by viewModel.isJamSheetVisible.collectAsState()
    val lastJamAction by viewModel.lastJamAction.collectAsState()
    val lastReaction by viewModel.lastReaction.collectAsState()
    val lastMemoryQuote by viewModel.lastMemoryQuote.collectAsState()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val sleepTimerMinutesRemaining by viewModel.sleepTimerMinutesRemaining.collectAsState()

    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }

    val track = currentTrack

    // Soft dreamy light baby pink background gradient
    val pinkDreamyGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                BabyPinkBgStart,
                BabyPinkBgMiddle,
                BabyPinkBgEnd,
                BabyPinkBgCard
            )
        )
    }

    Scaffold(
        containerColor = BabyPinkBgStart,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "PLAYING FROM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = BabyPinkTextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Auralis Master • " + (track?.qualityBadge ?: "320 kbps"),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = BabyPinkTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Player",
                            tint = BabyPinkTextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    // Sleep Timer button
                    IconButton(
                        onClick = { showSleepTimerSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerMinutesRemaining != null) BabyPinkPrimary else BabyPinkTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Playback Speed selector
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (playbackSpeed != 1.0f) BabyPinkPrimary.copy(alpha = 0.2f) else GlassSurfaceStrong)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { showSpeedSheet = true }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x",
                            color = if (playbackSpeed != 1.0f) BabyPinkPrimary else BabyPinkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setJamSheetVisible(true) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Together Mode 💗",
                            tint = if (jamSession != null) BabyPinkPrimary else BabyPinkTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setSoundProfilesVisible(true) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Equalizer & FX",
                            tint = BabyPinkPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        if (track != null) {
            val isLiked = likedTrackIds.contains(track.id)
            val isDisliked = dislikedTrackIds.contains(track.id)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pinkDreamyGradient)
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Live Jam Action Announcement (e.g. "Sneha played Kesariya")
                    AnimatedVisibility(
                        visible = lastJamAction != null,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(BabyPinkPrimary)
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(0.85f), Color.White.copy(0.2f))
                                    ),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = lastJamAction.orEmpty(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Together Mode Top Glass Strip (Section 11.3 C)
                    if (jamSession != null) {
                        val partnerName = jamSession!!.participants.firstOrNull { it != jamSession!!.username } ?: "Laddu"
                        TogetherTopGlassStrip(
                            partnerName = partnerName,
                            userName = jamSession!!.username,
                            onClick = { viewModel.setJamSheetVisible(true) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Center Content: Artwork vs Up Next vs Lyrics vs Related
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(targetState = activeTab, label = "player_center_crossfade") { tab ->
                            when (tab) {
                                PlayerScreenTab.ARTWORK -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .doubleBezelCard(outerRadius = 28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(track.albumArtUrl)
                                                .crossfade(300)
                                                .build(),
                                            contentDescription = "Album Artwork",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(28.dp))
                                        )
                                    }
                                }

                                PlayerScreenTab.UP_NEXT -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .glassCard(cornerRadius = 24.dp)
                                            .padding(14.dp)
                                    ) {
                                        val actualCurrentIndex = if (currentQueueIndex >= 0 && currentQueueIndex < queue.size) {
                                            currentQueueIndex
                                        } else {
                                            queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                        }
                                        val upcomingList = if (actualCurrentIndex + 1 < queue.size) {
                                            queue.subList(actualCurrentIndex + 1, queue.size)
                                        } else {
                                            emptyList()
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Queue & Up Next",
                                                    fontWeight = FontWeight.Bold,
                                                    color = BabyPinkTextPrimary,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = "${upcomingList.size} upcoming • Autoplay Radio On",
                                                    color = BabyPinkTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            if (upcomingList.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(GlassSurfaceStrong)
                                                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                                        .hapticPress(scaleDown = 0.92f)
                                                        .clickable { viewModel.clearUpcomingQueue() }
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = "Clear Upcoming",
                                                        color = BabyPinkPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 1. Currently Playing
                                            item {
                                                Text(
                                                    text = "NOW PLAYING",
                                                    color = BabyPinkPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(BabyPinkSoftRose.copy(alpha = 0.35f))
                                                        .border(1.2.dp, BabyPinkPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    AsyncImage(
                                                        model = track.albumArtUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(10.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = track.title,
                                                            color = BabyPinkTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = track.artist,
                                                            color = BabyPinkTextSecondary,
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Icon(
                                                        Icons.Default.GraphicEq,
                                                        contentDescription = "Playing",
                                                        tint = BabyPinkPrimary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }

                                            // 2. Upcoming Section Header
                                            item {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "UPCOMING (${upcomingList.size})",
                                                    color = BabyPinkTextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }

                                            if (upcomingList.isEmpty()) {
                                                item {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 20.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = "No upcoming tracks in manual queue 🎶",
                                                            color = BabyPinkTextSecondary,
                                                            fontSize = 13.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = "Infinite radio will automatically play matching songs next.",
                                                            color = BabyPinkTextSecondary.copy(alpha = 0.8f),
                                                            fontSize = 11.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Button(
                                                            onClick = { viewModel.startRadio() },
                                                            colors = ButtonDefaults.buttonColors(containerColor = BabyPinkPrimary),
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            Icon(Icons.Default.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Start Track Radio", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            } else {
                                                itemsIndexed(upcomingList) { relativeIndex, item ->
                                                    val absoluteIndex = actualCurrentIndex + 1 + relativeIndex
                                                    val canMoveUp = relativeIndex > 0
                                                    val canMoveDown = relativeIndex < upcomingList.size - 1

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(GlassSurfaceStrong)
                                                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                                            .clickable { viewModel.playTrackFromQueue(absoluteIndex) }
                                                            .padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        AsyncImage(
                                                            model = item.albumArtUrl,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(44.dp)
                                                                .clip(RoundedCornerShape(8.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = item.title,
                                                                color = BabyPinkTextPrimary,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = item.artist,
                                                                color = BabyPinkTextSecondary,
                                                                fontSize = 11.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        // Reorder Up Arrow
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.moveQueueItem(absoluteIndex, absoluteIndex - 1)
                                                            },
                                                            enabled = canMoveUp,
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.KeyboardArrowUp,
                                                                contentDescription = "Move Up",
                                                                tint = if (canMoveUp) BabyPinkPrimary else BabyPinkTextSecondary.copy(alpha = 0.3f),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }

                                                        // Reorder Down Arrow
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.moveQueueItem(absoluteIndex, absoluteIndex + 1)
                                                            },
                                                            enabled = canMoveDown,
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.KeyboardArrowDown,
                                                                contentDescription = "Move Down",
                                                                tint = if (canMoveDown) BabyPinkPrimary else BabyPinkTextSecondary.copy(alpha = 0.3f),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }

                                                        // Context Menu
                                                        IconButton(
                                                            onClick = { selectedTrackForMenu = item },
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.MoreVert,
                                                                contentDescription = "More",
                                                                tint = BabyPinkTextSecondary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }

                                                        // Delete Item
                                                        IconButton(
                                                            onClick = { viewModel.removeQueueItem(absoluteIndex) },
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Remove",
                                                                tint = BabyPinkTextSecondary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                PlayerScreenTab.LYRICS -> {
                                    val lyricsFontSize by viewModel.lyricsFontSize.collectAsState()
                                    val lyricsAutoScroll by viewModel.lyricsAutoScroll.collectAsState()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .glassCard(cornerRadius = 24.dp)
                                    ) {
                                        SyncedLyricsView(
                                            lyrics = lyrics,
                                            currentPositionFlow = viewModel.currentPositionMs,
                                            onSeekTo = { viewModel.seekTo(it) },
                                            lyricsFontSize = lyricsFontSize,
                                            autoScroll = lyricsAutoScroll
                                        )
                                    }
                                }

                                PlayerScreenTab.RELATED -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .glassCard(cornerRadius = 24.dp)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = "About This Track",
                                            fontWeight = FontWeight.Bold,
                                            color = BabyPinkTextPrimary,
                                            fontSize = 18.sp
                                        )
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = GlassSurfaceStrong),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text("Artist", color = BabyPinkTextSecondary, fontSize = 12.sp)
                                                Text(track.artist, color = BabyPinkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("Audio Quality", color = BabyPinkTextSecondary, fontSize = 12.sp)
                                                Text(track.qualityBadge, color = BabyPinkPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("Audio Engine", color = BabyPinkTextSecondary, fontSize = 12.sp)
                                                Text("Dual-ExoPlayer Crossfade Active", color = BabyPinkTextPrimary, fontSize = 13.sp)
                                            }
                                        }

                                        Button(
                                            onClick = { onNavigateToArtist(track.artist) },
                                            colors = ButtonDefaults.buttonColors(containerColor = BabyPinkPrimary),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().hapticPress(scaleDown = 0.94f)
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("View Full Artist Discography", color = Color.White, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { viewModel.startRadio() },
                                            colors = ButtonDefaults.buttonColors(containerColor = BabyPinkCardBg),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BabyPinkBorder)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().hapticPress(scaleDown = 0.94f)
                                        ) {
                                            Icon(Icons.Default.Radio, contentDescription = null, tint = BabyPinkPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Start Infinite Track Radio", color = BabyPinkPrimary, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { viewModel.setSoundProfilesVisible(true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = BabyPinkCardBg),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BabyPinkBorder)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().hapticPress(scaleDown = 0.94f)
                                        ) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = BabyPinkTextPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Customize Equalizer & Audio FX", color = BabyPinkTextPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Track Title, Artist, Quality Badge, and Heart/Dislike Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    letterSpacing = 0.4.sp
                                ),
                                color = BabyPinkTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BabyPinkTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onNavigateToArtist(track.artist) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GlassSurfaceStrong)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = track.qualityBadge,
                                        color = BabyPinkPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (jamSession != null && isLiked) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OurSongBadge()
                                }
                            }
                        }

                        // Heart (Like) & Dislike Pill Buttons with spring bounce
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleDislike(track.id) },
                                modifier = Modifier.hapticPress(scaleDown = 0.85f)
                            ) {
                                Icon(
                                    imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                    contentDescription = "Dislike",
                                    tint = if (isDisliked) BabyPinkPrimary else BabyPinkTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleLike(track.id) },
                                modifier = Modifier.hapticPress(scaleDown = 0.85f)
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (isLiked) BabyPinkPrimary else BabyPinkTextSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Isolated Progress Scrubber (Only this re-renders on position ticks)
                    PlayerScrubberSection(
                        viewModel = viewModel,
                        track = track
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Playback Controls Row: Shuffle | Prev | Play/Pause | Next | Repeat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        IconButton(
                            onClick = { viewModel.toggleShuffle() },
                            modifier = Modifier.hapticPress(scaleDown = 0.85f)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) BabyPinkPrimary else BabyPinkTextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Skip Previous
                        IconButton(
                            onClick = { viewModel.skipPrevious() },
                            modifier = Modifier
                                .size(48.dp)
                                .hapticPress(scaleDown = 0.88f)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = BabyPinkTextPrimary,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Primary Play/Pause Button with Soft Pink Glow and Specular Rim
                        val infiniteTransition = rememberInfiniteTransition(label = "play_pulse")
                        val breathingScale by infiniteTransition.animateFloat(
                            initialValue = 1.0f,
                            targetValue = if (isPlaying) 1.04f else 1.01f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1100, easing = FastOutSlowInEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "play_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .scale(breathingScale),
                            contentAlignment = Alignment.Center
                        ) {
                            if (jamSession != null) {
                                Box(
                                    modifier = Modifier
                                        .size(82.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x40FFB6C1))
                                        .border(1.dp, BabyPinkPrimary.copy(alpha = 0.5f), CircleShape)
                                )
                            }

                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.90f),
                                                BabyPinkSoftRose.copy(alpha = 0.30f)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .hapticPress(scaleDown = 0.90f),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = BabyPinkPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        // Skip Next
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier
                                .size(48.dp)
                                .hapticPress(scaleDown = 0.88f)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = BabyPinkTextPrimary,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Repeat Mode Button
                        IconButton(
                            onClick = { viewModel.toggleRepeat() },
                            modifier = Modifier.hapticPress(scaleDown = 0.85f)
                        ) {
                            val (repeatIcon, repeatTint) = when (repeatMode) {
                                RepeatMode.OFF -> Pair(Icons.Default.Repeat, BabyPinkTextSecondary)
                                RepeatMode.ALL -> Pair(Icons.Default.Repeat, BabyPinkPrimary)
                                RepeatMode.ONE -> Pair(Icons.Default.RepeatOne, BabyPinkPrimary)
                            }
                            Icon(
                                repeatIcon,
                                contentDescription = "Repeat",
                                tint = repeatTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Together Mode Live Reaction Bar or Start Button (Section 11.3 E & 11.3 A)
                    if (jamSession != null) {
                        TogetherLiveReactionsTray(
                            onSendReaction = { emoji -> viewModel.sendJamReaction(emoji) },
                            onTriggerQuote = { viewModel.sendMemoryQuote("I love you jaanaa 💋") },
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else {
                        TogetherModeButton(
                            onClick = { viewModel.setJamSheetVisible(true) },
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3-Tab Segmented Pill Bar: [ UP NEXT ] [ LYRICS ] [ RELATED ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassSurfaceStrong)
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PlayerSegmentPill(
                            label = "UP NEXT",
                            isSelected = activeTab == PlayerScreenTab.UP_NEXT,
                            onClick = { viewModel.selectTab(PlayerScreenTab.UP_NEXT) },
                            modifier = Modifier.weight(1f)
                        )

                        PlayerSegmentPill(
                            label = "LYRICS",
                            isSelected = activeTab == PlayerScreenTab.LYRICS,
                            onClick = { viewModel.selectTab(PlayerScreenTab.LYRICS) },
                            modifier = Modifier.weight(1f)
                        )

                        PlayerSegmentPill(
                            label = "RELATED",
                            isSelected = activeTab == PlayerScreenTab.RELATED,
                            onClick = { viewModel.selectTab(PlayerScreenTab.RELATED) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Floating Reaction Particles & Memory Quote Overlay (Section 11.3 E & 11.4)
                FloatingReactionParticles(triggerReaction = lastReaction)
                MemoryQuoteOverlay(
                    quote = lastMemoryQuote?.first,
                    sender = lastMemoryQuote?.second,
                    onDismiss = { viewModel.clearMemoryQuote() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }
    }

    if (isSoundProfilesVisible) {
        SoundProfilesBottomSheet(
            profile = soundProfile,
            onProfileChange = { viewModel.updateSoundProfile(it) },
            onDismiss = { viewModel.setSoundProfilesVisible(false) }
        )
    }

    if (isJamSheetVisible) {
        TogetherModeBottomSheet(
            session = jamSession,
            onStartTogether = { code, name ->
                viewModel.startJam(code, name)
                viewModel.setJamSheetVisible(false)
            },
            onJoinTogether = { code, name ->
                viewModel.joinJam(code, name)
                viewModel.setJamSheetVisible(false)
            },
            onLeaveTogether = {
                viewModel.leaveJam()
            },
            onSendReaction = { emoji ->
                viewModel.sendJamReaction(emoji)
            },
            onSendMemoryQuote = { quote ->
                viewModel.sendMemoryQuote(quote)
            },
            onDismiss = { viewModel.setJamSheetVisible(false) }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            remainingMinutes = sleepTimerMinutesRemaining,
            onSetTimer = { viewModel.setSleepTimer(it) },
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showSpeedSheet) {
        PlaybackSpeedBottomSheet(
            currentSpeed = playbackSpeed,
            onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (selectedTrackForMenu != null) {
        val menuTrack = selectedTrackForMenu!!
        TrackContextMenuBottomSheet(
            track = menuTrack,
            onPlayNext = { viewModel.playNext(menuTrack) },
            onAddToQueue = { viewModel.addToQueue(menuTrack) },
            onStartRadio = { viewModel.startRadio() },
            onViewArtist = { onNavigateToArtist(it) },
            onToggleDownload = { viewModel.downloadCurrentTrack() },
            onDismiss = { selectedTrackForMenu = null }
        )
    }
}

@Composable
private fun PlayerSegmentPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BabyPinkPrimary else Color.Transparent)
            .hapticPress(scaleDown = 0.95f)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) BabyPinkTextPrimary else BabyPinkTextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Isolated progress scrubber: only this micro-composable recomposes as playback position advances,
 * ensuring the rest of FullPlayerScreen remains silky smooth at 120 FPS.
 */
@Composable
private fun PlayerScrubberSection(
    viewModel: PlayerViewModel,
    track: Track
) {
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableStateOf(0f) }

    val totalDuration = if (durationMs > 0L) durationMs else track.durationMs.coerceAtLeast(30000L)
    val currentPos = if (isDraggingSlider) dragSliderValue.toLong() else currentPositionMs
    val sliderPos = currentPos.toFloat().coerceIn(0f, totalDuration.toFloat())

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPos,
            onValueChange = {
                isDraggingSlider = true
                dragSliderValue = it
            },
            onValueChangeFinished = {
                isDraggingSlider = false
                viewModel.seekTo(dragSliderValue.toLong())
            },
            valueRange = 0f..totalDuration.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = BabyPinkPrimary,
                inactiveTrackColor = ProgressBarTrackPink
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val currentSec = currentPos / 1000L
            val totalSec = totalDuration / 1000L
            Text(
                text = String.format("%d:%02d", currentSec / 60, currentSec % 60),
                color = BabyPinkTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format("%d:%02d", totalSec / 60, totalSec % 60),
                color = BabyPinkTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
