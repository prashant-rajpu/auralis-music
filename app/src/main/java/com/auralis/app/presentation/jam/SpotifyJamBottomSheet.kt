package com.auralis.app.presentation.jam

import androidx.compose.runtime.Composable
import com.auralis.app.network.JamSession
import com.auralis.app.presentation.together.TogetherModeBottomSheet

/**
 * Backward compatibility alias for TogetherModeBottomSheet.
 * Redirects to the Glassmorphism Baby Pink Together Mode (Couple Sync) sheet.
 */
@Composable
fun SpotifyJamBottomSheet(
    session: JamSession?,
    onStartJam: (jamId: String, username: String) -> Unit,
    onJoinJam: (jamId: String, username: String) -> Unit,
    onLeaveJam: () -> Unit,
    onDismiss: () -> Unit
) {
    TogetherModeBottomSheet(
        session = session,
        onStartTogether = onStartJam,
        onJoinTogether = onJoinJam,
        onLeaveTogether = onLeaveJam,
        onDismiss = onDismiss
    )
}
