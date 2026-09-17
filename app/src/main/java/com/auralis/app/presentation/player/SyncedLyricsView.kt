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
import com.auralis.app.ui.theme.SpotifyGreen

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
            Text(
                text = "Looking for synchronized lyrics...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // Find the currently active line index based on playback position
    val activeIndex = lyrics.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)

    // Smoothly auto-scroll so active line stays near the center
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
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = index == activeIndex
            val alpha by animateFloatAsState(targetValue = if (isActive) 1.0f else 0.4f, label = "lyric_alpha")
            val fontSize = if (isActive) 22.sp else 18.sp
            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            val color = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onBackground

            Text(
                text = line.text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .clickable { onSeekTo(line.timestampMs) }
                    .padding(vertical = 6.dp)
            )
        }
    }
}
