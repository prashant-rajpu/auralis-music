package com.auralis.app.presentation.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.presentation.common.SectionHeader
import com.auralis.app.presentation.common.TrackContextMenuBottomSheet
import com.auralis.app.presentation.common.TrackRow
import com.auralis.app.ui.theme.*

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel = hiltViewModel(),
    onNavigateToArtist: (String) -> Unit
) {
    val query by viewModel.query.collectAsState()
    val mood by viewModel.mood.collectAsState()
    val providerFilter by viewModel.providerFilter.collectAsState()
    val state by viewModel.state.collectAsState()

    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Explore",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Songs, artists, albums", color = TextTertiary, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentColor) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onQueryChange("") },
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
                modifier = Modifier.fillMaxWidth()
            )

            // Catalog filter: All, plus every online source compiled into this edition.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val providers: List<Provider?> = listOf(null) + viewModel.availableProviders
                providers.forEach { provider ->
                    FilterChipPill(
                        label = provider?.displayName ?: "All catalogs",
                        isSelected = providerFilter == provider,
                        onClick = { viewModel.selectProvider(provider) }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (query.isBlank()) {
                item(key = "moods") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "Moods and genres",
                            subtitle = mood?.let { "Showing ${it.label}" }
                        )
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(ExploreMood.entries.toList(), key = { it.name }) { entry ->
                                MoodTile(
                                    mood = entry,
                                    isSelected = mood == entry,
                                    onClick = { viewModel.selectMood(if (mood == entry) null else entry) }
                                )
                            }
                        }
                    }
                }
            }

            when (val current = state) {
                is ExploreState.Idle -> Unit

                is ExploreState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentColor)
                        }
                    }
                }

                is ExploreState.Results -> {
                    item(key = "results-header") {
                        SectionHeader(
                            title = when {
                                query.isNotBlank() -> "Results for \"$query\""
                                mood != null -> "${mood!!.label} picks"
                                else -> "Trending now"
                            },
                            subtitle = "${current.tracks.size} tracks"
                        )
                    }
                    items(current.tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { viewModel.play(track) },
                            onDownloadClick = { viewModel.toggleDownload(track) },
                            onMoreClick = { selectedTrack = track },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                is ExploreState.Error -> {
                    item(key = "error") {
                        MessageCard(message = current.message, actionLabel = "Try again", onAction = viewModel::retry)
                    }
                }
            }
        }
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
private fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .hapticPress(scaleDown = 0.92f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) AccentColor else SurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else BorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) OnAccentColor else TextSecondary
        )
    }
}

@Composable
private fun MoodTile(
    mood: ExploreMood,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(150.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AccentColor else SurfaceColor)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else BorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .hapticPress(scaleDown = 0.95f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = mood.emoji, fontSize = 18.sp)
        Text(
            text = mood.label,
            color = if (isSelected) OnAccentColor else TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
internal fun MessageCard(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .surfaceCard(cornerRadius = 20.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, color = TextSecondary, fontSize = 14.sp)
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.hapticPress(scaleDown = 0.96f)
            ) {
                Text(actionLabel, color = OnAccentColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
