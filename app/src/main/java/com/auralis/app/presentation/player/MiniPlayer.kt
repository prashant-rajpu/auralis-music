package com.auralis.app.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.auralis.app.ui.theme.*

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToFullPlayer: () -> Unit = {}
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val jamSession by viewModel.jamSession.collectAsState()
    val lastJamAction by viewModel.lastJamAction.collectAsState()

    if (currentTrack != null) {
        val isLiked = likedTrackIds.contains(currentTrack!!.id)
        val progress = if (durationMs > 0L) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        MiniPlayerContent(
            track = currentTrack!!,
            isPlaying = isPlaying,
            isLiked = isLiked,
            isJamActive = jamSession != null,
            lastJamAction = lastJamAction,
            jamParticipants = jamSession?.participants?.filter { it != jamSession?.username }?.joinToString().orEmpty(),
            progress = progress,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onSkipNextClick = { viewModel.skipNext() },
            onLikeClick = { viewModel.toggleLike(currentTrack!!.id) },
            onClick = onNavigateToFullPlayer
        )
    }
}

@Composable
private fun MiniPlayerContent(
    track: Track,
    isPlaying: Boolean,
    isLiked: Boolean,
    isJamActive: Boolean,
    lastJamAction: String?,
    jamParticipants: String,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onLikeClick: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), ambientColor = PlayButtonGlowPink)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurfaceStrong)
            .border(1.2.dp, GlassBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Column {
            // Soft pink progress line at the top of the mini player (per spec)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = BabyPinkPrimary,
                trackColor = ProgressBarTrackPink
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Miniature rounded album art
                AsyncImage(
                    model = track.albumArtUrl,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BabyPinkBgMiddle)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track Title & Artist
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = BabyPinkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (lastJamAction != null) "💖 $lastJamAction" else if (isJamActive) "🎧 Jam with ${jamParticipants.ifEmpty { "Partner" }}" else "${track.artist} • ${track.source}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isJamActive || lastJamAction != null) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isJamActive || lastJamAction != null) BabyPinkPrimary else BabyPinkTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Heart (Like) Shortcut
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) BabyPinkPrimary else BabyPinkTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause Button
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = BabyPinkTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Skip Next Button
                IconButton(
                    onClick = onSkipNextClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = BabyPinkTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
