package com.auralis.app.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auralis.app.ui.theme.NeonCyan
import com.auralis.app.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close Player")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (currentTrack != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = currentTrack!!.albumArtUrl,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.DarkGray)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = currentTrack!!.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack!!.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E3A2B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentTrack!!.qualityBadge,
                                color = SpotifyGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Streamed via ${currentTrack!!.source}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Seek bar placeholder
                Slider(
                    value = 0f,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0:00", style = MaterialTheme.typography.labelMedium)
                    val durationSec = (currentTrack!!.durationMs / 1000).coerceAtLeast(30)
                    val min = durationSec / 60
                    val sec = durationSec % 60
                    Text(String.format("%d:%02d", min, sec), style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.downloadCurrentTrack() }, modifier = Modifier.size(48.dp)) {
                        if (currentTrack!!.isDownloaded) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download Song",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    IconButton(onClick = { /* Previous */ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                    }

                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    IconButton(onClick = { /* Next */ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = { /* Favorite */ }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Favorite", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
