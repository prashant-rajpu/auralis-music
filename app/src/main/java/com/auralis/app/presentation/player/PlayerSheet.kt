package com.auralis.app.presentation.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.auralis.app.ui.theme.BackgroundColor
import com.auralis.app.ui.theme.BorderHighlight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PlayerSheetValue { Collapsed, Expanded }

/**
 * Before the bottom chrome has been measured the anchors are unknown; this is only used for the
 * first frame, after which [PlayerSheet] swaps in the measured height.
 */
private const val ESTIMATED_COLLAPSED_HEIGHT_DP = 160

@Composable
fun rememberPlayerSheetState(): AnchoredDraggableState<PlayerSheetValue> = remember {
    AnchoredDraggableState(initialValue = PlayerSheetValue.Collapsed)
}

/**
 * The mini player and the full player are one surface that slides, not two destinations. Collapsed,
 * the sheet shows the mini player and the navigation bar; dragged up (or tapped) it becomes the
 * full player, and the two cross-fade over the drag rather than cutting between screens.
 *
 * The drag is attached to the collapsed chrome and to the handle at the top of the expanded player,
 * never to the player's own scrolling content, so the queue and lyrics lists still scroll normally.
 */
@Composable
fun PlayerSheet(
    state: AnchoredDraggableState<PlayerSheetValue>,
    containerHeightPx: Float,
    hasTrack: Boolean,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    collapsedContent: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val background = BackgroundColor

    var collapsedHeightPx by remember {
        mutableIntStateOf(with(density) { ESTIMATED_COLLAPSED_HEIGHT_DP.dp.roundToPx() })
    }

    LaunchedEffect(containerHeightPx, collapsedHeightPx) {
        if (containerHeightPx <= 0f) return@LaunchedEffect
        state.updateAnchors(
            DraggableAnchors {
                PlayerSheetValue.Collapsed at (containerHeightPx - collapsedHeightPx)
                PlayerSheetValue.Expanded at 0f
            }
        )
    }

    // A track can finish and clear while the player is open; drop back to the tabs rather than
    // leaving an empty full-screen player on top of them.
    LaunchedEffect(hasTrack) {
        if (!hasTrack && state.targetValue == PlayerSheetValue.Expanded) {
            state.animateTo(PlayerSheetValue.Collapsed)
        }
    }

    // Read in the draw/layout phase, never during composition: the offset changes every frame
    // while dragging, and recomposing the full player 60 times a second would drop frames.
    fun progress(): Float {
        val raw = state.progress(PlayerSheetValue.Collapsed, PlayerSheetValue.Expanded)
        return if (raw.isNaN()) 0f else raw.coerceIn(0f, 1f)
    }

    val isExpanded = state.targetValue == PlayerSheetValue.Expanded

    // Flips once per open/close rather than once per frame, so the full player is composed only
    // while it can actually be seen.
    val expandedVisible by remember(state) {
        derivedStateOf {
            val offset = state.offset
            state.targetValue == PlayerSheetValue.Expanded ||
                (!offset.isNaN() && offset < state.anchors.positionOf(PlayerSheetValue.Collapsed) - 1f)
        }
    }

    BackHandler(enabled = isExpanded) {
        scope.launch { state.animateTo(PlayerSheetValue.Collapsed) }
    }

    val dragModifier = Modifier.anchoredDraggable(
        state = state,
        orientation = Orientation.Vertical,
        enabled = hasTrack,
        flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset {
                val y = state.offset
                IntOffset(0, if (y.isNaN()) containerHeightPx.roundToInt() else y.roundToInt())
            }
            .drawBehind { drawRect(background, alpha = progress()) }
    ) {
        // Expanded: the full player, only composed once the sheet has actually started to open.
        if (expandedVisible) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = progress() }) {
                FullPlayerScreen(
                    onNavigateUp = { scope.launch { state.animateTo(PlayerSheetValue.Collapsed) } },
                    onNavigateToArtist = { artist ->
                        scope.launch { state.animateTo(PlayerSheetValue.Collapsed) }
                        onNavigateToArtist(artist)
                    }
                )

                // Narrow and centred so it never covers the top bar's back or overflow buttons.
                Box(
                    modifier = dragModifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .width(120.dp)
                        .height(36.dp)
                        .semantics { contentDescription = "Drag down to close the player" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BorderHighlight)
                    )
                }
            }
        }

        // Collapsed: the mini player plus the navigation bar, fading out as the sheet opens.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - progress() }
                .onSizeChanged { collapsedHeightPx = it.height }
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            collapsedContent(if (hasTrack) dragModifier else Modifier)
        }
    }
}

/** Expand the sheet, e.g. when the mini player is tapped. */
@Composable
fun rememberExpandPlayer(state: AnchoredDraggableState<PlayerSheetValue>): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(state) {
        { scope.launch { state.animateTo(PlayerSheetValue.Expanded) }; Unit }
    }
}
