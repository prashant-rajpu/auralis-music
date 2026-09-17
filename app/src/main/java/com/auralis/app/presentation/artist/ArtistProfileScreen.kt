package com.auralis.app.presentation.artist

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.auralis.app.domain.model.Track
import com.auralis.app.network.ArtistProfile
import com.auralis.app.ui.theme.*

@Composable
fun ArtistProfileScreen(
    onNavigateBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    viewModel: ArtistProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BabyPinkBackgroundBrush)
    ) {
        when (val state = uiState) {
            is ArtistUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = BabyPinkPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            is ArtistUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = BabyPinkTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = { viewModel.loadArtistProfile() },
                            colors = ButtonDefaults.buttonColors(containerColor = BabyPinkPrimary)
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            }

            is ArtistUiState.Success -> {
                ArtistContent(
                    profile = state.profile,
                    onNavigateBack = onNavigateBack,
                    onPlayAll = { viewModel.playAllTopSongs() },
                    onStartRadio = { viewModel.startArtistRadio() },
                    onTrackClick = onTrackClick
                )
            }
        }
    }
}

@Composable
private fun ArtistContent(
    profile: ArtistProfile,
    onNavigateBack: () -> Unit,
    onPlayAll: () -> Unit,
    onStartRadio: () -> Unit,
    onTrackClick: (Track) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Hero Header Image & Artist Info
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // Banner Image
                if (profile.bannerUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(profile.bannerUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = profile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BabyPinkAccent)
                    )
                }

                // Gradient Overlay for smooth romantic look
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    BabyPinkBackgroundStart.copy(alpha = 0.85f),
                                    BabyPinkBackgroundStart
                                )
                            )
                        )
                )

                // Top Navigation Back Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(40.dp)
                        .glassPill(borderWidth = 1.dp)
                        .hapticPress(scaleDown = 0.88f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Artist Header Text
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = BabyPinkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = profile.monthlyListeners ?: "Verified Artist",
                            color = BabyPinkTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = profile.name,
                        color = BabyPinkTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Action Buttons: Play All & Radio
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = BabyPinkPrimary),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .hapticPress(scaleDown = 0.94f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play All",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = onStartRadio,
                    colors = ButtonDefaults.buttonColors(containerColor = BabyPinkCardBg),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(BabyPinkBorder)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .hapticPress(scaleDown = 0.94f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = BabyPinkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Start Radio",
                        color = BabyPinkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Bio section
        if (!profile.bio.isNullOrBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .glassCard(cornerRadius = 16.dp)
                        .padding(14.dp)
                ) {
                    Text(
                        text = profile.bio,
                        color = BabyPinkTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Top Songs Shelf
        item {
            Text(
                text = "Top Songs",
                color = BabyPinkTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        itemsIndexed(profile.topSongs, key = { _, track -> track.id }) { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTrackClick(track) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank Number
                Text(
                    text = "${index + 1}",
                    color = if (index < 3) BabyPinkPrimary else BabyPinkTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )

                // Artwork
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.albumArtUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = BabyPinkTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
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

                // Duration
                val min = (track.durationMs / 1000) / 60
                val sec = (track.durationMs / 1000) % 60
                Text(
                    text = String.format("%d:%02d", min, sec),
                    color = BabyPinkTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Discography / Releases Shelf
        if (profile.releases.isNotEmpty()) {
            item {
                Text(
                    text = "Albums & Singles",
                    color = BabyPinkTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp)
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(profile.releases, key = { it.browseId }) { release ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .glassCard(cornerRadius = 14.dp)
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(release.coverUrl ?: profile.bannerUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = release.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(114.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = release.title,
                                color = BabyPinkTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = release.year ?: "2024",
                                color = BabyPinkTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
