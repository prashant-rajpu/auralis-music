package com.auralis.app.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auralis.app.domain.model.Track
import com.auralis.app.playback.RepeatMode
import com.auralis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val isShuffle by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val dislikedTrackIds by viewModel.dislikedTrackIds.collectAsState()
    val isSoundProfilesVisible by viewModel.isSoundProfilesVisible.collectAsState()
    val soundProfile by viewModel.soundProfile.collectAsState()

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableStateOf(0f) }

    val track = currentTrack

    // Dynamic ambient background gradient
    val ambientGradient = remember(track?.title) {
        val hash = (track?.title?.hashCode() ?: 0)
        val hueR = 0x2A + (Math.abs(hash) % 0x1A)
        val hueG = 0x0A + (Math.abs(hash shr 2) % 0x10)
        val hueB = 0x15 + (Math.abs(hash shr 4) % 0x18)
        Brush.verticalGradient(
            colors = listOf(
                Color(hueR, hueG, hueB),
                Color(0xFF141414),
                YtMusicBlack,
                YtMusicBlack
            )
        )
    }

    Scaffold(
        containerColor = YtMusicBlack,
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
                            color = YtMusicTextTertiary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = track?.source ?: "Auralis Music",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
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
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setSoundProfilesVisible(true) }) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Equalizer & FX",
                            tint = YtMusicRed,
                            modifier = Modifier.size(24.dp)
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
                    .background(ambientGradient)
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                                            .clip(RoundedCornerShape(20.dp))
                                            .border(1.dp, YtMusicBorder, RoundedCornerShape(20.dp))
                                            .background(YtMusicCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = track.albumArtUrl,
                                            contentDescription = "Album Artwork",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                PlayerScreenTab.UP_NEXT -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(YtMusicSurface)
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Up Next Queue (${queue.size})",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "Autoplay On",
                                                color = YtMusicRed,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            itemsIndexed(queue) { index, item ->
                                                val isCurrent = item.id == track.id
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (isCurrent) YtMusicCard else Color.Transparent)
                                                        .clickable { viewModel.playTrackFromQueue(index) }
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    AsyncImage(
                                                        model = item.albumArtUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(6.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.title,
                                                            color = if (isCurrent) YtMusicRed else Color.White,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 14.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = item.artist,
                                                            color = YtMusicTextSecondary,
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (isCurrent) {
                                                        Icon(
                                                            Icons.Default.GraphicEq,
                                                            contentDescription = "Playing",
                                                            tint = YtMusicRed,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                PlayerScreenTab.LYRICS -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(YtMusicSurface)
                                    ) {
                                        SyncedLyricsView(
                                            lyrics = lyrics,
                                            currentPositionMs = currentPositionMs,
                                            onSeekTo = { viewModel.seekTo(it) }
                                        )
                                    }
                                }

                                PlayerScreenTab.RELATED -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(YtMusicSurface)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            text = "About This Track",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 18.sp
                                        )
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = YtMusicCard),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text("Artist", color = YtMusicTextSecondary, fontSize = 12.sp)
                                                Text(track.artist, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("Audio Quality", color = YtMusicTextSecondary, fontSize = 12.sp)
                                                Text(track.qualityBadge, color = YtMusicRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("Audio Engine", color = YtMusicTextSecondary, fontSize = 12.sp)
                                                Text("Dual-ExoPlayer Crossfade (50ms interpolation)", color = Color.White, fontSize = 13.sp)
                                            }
                                        }
                                        Button(
                                            onClick = { viewModel.setSoundProfilesVisible(true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = YtMusicPill),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = YtMusicRed)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Customize Equalizer & Audio FX", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Track Title, Artist, and Thumbs Up / Down Pill Row
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
                                    fontSize = 22.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = YtMusicTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2B1417), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = track.qualityBadge,
                                        color = YtMusicRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Thumbs Up / Down Pill Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleDislike(track.id) }) {
                                Icon(
                                    imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                    contentDescription = "Dislike",
                                    tint = if (isDisliked) YtMusicRed else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleLike(track.id) }) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "Like",
                                    tint = if (isLiked) YtMusicRed else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress Scrubber
                    val totalDuration = if (durationMs > 0L) durationMs else track.durationMs.coerceAtLeast(30000L)
                    val currentPos = if (isDraggingSlider) dragSliderValue.toLong() else currentPositionMs
                    val sliderPos = currentPos.toFloat().coerceIn(0f, totalDuration.toFloat())

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
                            thumbColor = YtMusicRed,
                            activeTrackColor = YtMusicRed,
                            inactiveTrackColor = Color(0x33FFFFFF)
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
                            color = YtMusicTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format("%d:%02d", totalSec / 60, totalSec % 60),
                            color = YtMusicTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Media Controls Row: Shuffle | Prev | Play/Pause | Next | Repeat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        IconButton(onClick = { viewModel.toggleShuffle() }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) YtMusicRed else YtMusicTextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Skip Previous
                        IconButton(
                            onClick = { viewModel.skipPrevious() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // YouTube Music Iconic Filled Play/Pause Button
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(72.dp)
                                .shadow(elevation = 12.dp, shape = CircleShape),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(42.dp),
                                tint = Color.Black
                            )
                        }

                        // Skip Next
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Repeat Mode Button
                        IconButton(onClick = { viewModel.toggleRepeat() }) {
                            val (repeatIcon, repeatTint) = when (repeatMode) {
                                RepeatMode.OFF -> Pair(Icons.Default.Repeat, YtMusicTextSecondary)
                                RepeatMode.ALL -> Pair(Icons.Default.Repeat, YtMusicRed)
                                RepeatMode.ONE -> Pair(Icons.Default.RepeatOne, YtMusicRed)
                            }
                            Icon(
                                repeatIcon,
                                contentDescription = "Repeat",
                                tint = repeatTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // YouTube Music Trademark 3-Tab Segmented Pill Bar: [ UP NEXT ] [ LYRICS ] [ RELATED ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(YtMusicPill)
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
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else YtMusicTextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}
