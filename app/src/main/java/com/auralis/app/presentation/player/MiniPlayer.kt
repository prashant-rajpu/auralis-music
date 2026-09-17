package com.auralis.app.presentation.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.auralis.app.domain.model.Track
import com.auralis.app.ui.theme.*

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToFullPlayer: () -> Unit = {}
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val jamSession by viewModel.jamSession.collectAsState()
    val lastJamAction by viewModel.lastJamAction.collectAsState()

    if (currentTrack != null) {
        val isLiked = likedTrackIds.contains(currentTrack!!.id)

        MiniPlayerContent(
            track = currentTrack!!,
            isPlaying = isPlaying,
            isLiked = isLiked,
            isJamActive = jamSession != null,
            lastJamAction = lastJamAction,
            jamParticipants = jamSession?.participants?.filter { it != jamSession?.username }?.joinToString().orEmpty(),
            viewModel = viewModel,
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
    viewModel: PlayerViewModel,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onLikeClick: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .hapticPress(scaleDown = 0.985f)
            .glassPanel(cornerRadius = 22.dp)
            .clickable { onClick() }
    ) {
        Column {
            // Decoupled Progress Bar to isolate 200ms recompositions from the rest of the MiniPlayer
            IsolatedMiniProgressBar(viewModel = viewModel)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Miniature rounded album art with smooth crossfade and Together badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.albumArtUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BabyPinkBgMiddle)
                    )

                    if (isJamActive) {
                        Box(
                            modifier = Modifier
                                .offset(x = 4.dp, y = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BabyPinkPrimary)
                                .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "💗 Together",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

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
                        text = if (lastJamAction != null) "💖 $lastJamAction" else if (isJamActive) "Together with ${jamParticipants.ifEmpty { "Laddu" }} 💗 • Synced" else "${track.artist} • ${track.qualityBadge}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isJamActive || lastJamAction != null) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isJamActive || lastJamAction != null) BabyPinkPrimary else BabyPinkTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Heart (Like) Shortcut with haptic bounce
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier
                        .size(38.dp)
                        .hapticPress(scaleDown = 0.88f)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) BabyPinkPrimary else BabyPinkTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause Button with tactile kinetic scale
                val playButtonScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1.05f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "play_button_scale"
                )

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            scaleX = playButtonScale
                            scaleY = playButtonScale
                        }
                        .hapticPress(scaleDown = 0.88f)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = BabyPinkPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Skip Next Button with haptic bounce
                IconButton(
                    onClick = onSkipNextClick,
                    modifier = Modifier
                        .size(38.dp)
                        .hapticPress(scaleDown = 0.88f)
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

/**
 * Isolated progress bar: only this micro-composable re-evaluates when current position ticks,
 * completely eliminating full-card recomposition jank.
 */
@Composable
private fun IsolatedMiniProgressBar(viewModel: PlayerViewModel) {
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    val progress = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LinearProgressIndicator(
        progress = progress,
        modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp),
        color = BabyPinkPrimary,
        trackColor = ProgressBarTrackPink
    )
}
