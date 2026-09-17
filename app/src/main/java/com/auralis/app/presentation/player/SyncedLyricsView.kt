package com.auralis.app.presentation.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.app.domain.model.LyricLine
import com.auralis.app.ui.theme.BabyPinkPrimary
import com.auralis.app.ui.theme.BabyPinkTextPrimary
import com.auralis.app.ui.theme.BabyPinkTextSecondary
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SyncedLyricsView(
    lyrics: List<LyricLine>,
    currentPositionFlow: StateFlow<Long>,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by currentPositionFlow.collectAsState()
    SyncedLyricsView(
        lyrics = lyrics,
        currentPositionMs = currentPositionMs,
        onSeekTo = onSeekTo,
        modifier = modifier
    )
}

@Composable
fun SyncedLyricsView(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Lyrics not available for this track",
                    style = MaterialTheme.typography.titleMedium,
                    color = BabyPinkTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Synced LRC will load automatically when found",
                    style = MaterialTheme.typography.bodySmall,
                    color = BabyPinkTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()

    // Find the currently active line index based on playback position
    val activeIndex = lyrics.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)

    // Smoothly auto-scroll so active line stays centered
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.size) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = index == activeIndex
            val alpha by animateFloatAsState(targetValue = if (isActive) 1.0f else 0.4f, label = "lyric_alpha")
            val fontSize = if (isActive) 24.sp else 19.sp
            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            val color = if (isActive) BabyPinkTextPrimary else BabyPinkTextSecondary

            Text(
                text = line.text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
                textAlign = TextAlign.Start,
                lineHeight = 32.sp,
                letterSpacing = 0.4.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .clickable { onSeekTo(line.timestampMs) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}
