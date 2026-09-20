package com.auralis.app.presentation.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.app.together.CallState
import com.auralis.app.ui.theme.*
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * The call, over everything else.
 *
 * Deliberately an overlay rather than a destination: a call happens *while* you are listening
 * together, and navigating away from the music to take it would be the wrong shape. Nothing is
 * drawn at all unless there is a call, so it costs nothing the rest of the time.
 */
@Composable
fun CallOverlay(viewModel: CallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    AnimatedVisibility(
        visible = state.state != CallState.IDLE,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        when (state.state) {
            CallState.RINGING_IN -> IncomingCall(viewModel)
            CallState.ENDED -> CallEnded(viewModel)
            else -> LiveCall(viewModel)
        }
    }
}

@Composable
private fun IncomingCall(viewModel: CallViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Asked for at the moment of answering rather than at install: "why does my music player want
    // the camera" is a fair question, and the honest answer only makes sense here.
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) viewModel.accept() else viewModel.decline()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.peerName.ifBlank { "They" },
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.withVideo) "is calling, with video" else "is calling",
                fontSize = 14.sp,
                color = TextSecondary,
            )
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                CallButton(
                    icon = Icons.Filled.CallEnd,
                    background = Color(0xFFD9483B),
                    label = "Decline",
                    onClick = viewModel::decline,
                )
                CallButton(
                    icon = Icons.Filled.Call,
                    background = Color(0xFF2FA84F),
                    label = "Answer",
                    onClick = {
                        val wanted = needed(state.withVideo)
                        // Nothing to ask for on the second call of the evening, and a permission
                        // sheet flashing past an answered call looks like a bug.
                        if (wanted.all { granted(context, it) }) viewModel.accept()
                        else permissions.launch(wanted)
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveCall(viewModel: CallViewModel) {
    val state by viewModel.state.collectAsState()
    val remote by viewModel.remoteVideo.collectAsState()
    val local by viewModel.localVideo.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (remote != null && state.media.peerCameraEnabled) {
            VideoSurface(track = remote, eglBase = viewModel.eglBase, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.peerName.ifBlank { "Them" },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (state.media.peerCameraEnabled) "Camera starting…" else "Camera off",
                        fontSize = 13.sp,
                        color = TextTertiary,
                    )
                }
            }
        }

        if (local != null && state.media.cameraEnabled) {
            VideoSurface(
                track = local,
                eglBase = viewModel.eglBase,
                mirror = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 108.dp, height = 156.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
        ) {
            Text(
                text = state.peerName.ifBlank { "Them" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Text(text = statusLine(state.state), fontSize = 12.sp, color = TextTertiary)
            if (!state.media.peerMicEnabled) {
                Text(text = "Their microphone is off", fontSize = 12.sp, color = WarningColor)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CallButton(
                icon = if (state.media.micEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                background = if (state.media.micEnabled) SurfaceHighest else Color(0xFF7A2B24),
                label = if (state.media.micEnabled) "Mute" else "Unmute",
                onClick = { viewModel.setMic(!state.media.micEnabled) },
            )
            CallButton(
                icon = if (state.media.cameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                background = if (state.media.cameraEnabled) SurfaceHighest else Color(0xFF7A2B24),
                label = if (state.media.cameraEnabled) "Camera off" else "Camera on",
                onClick = { viewModel.setCamera(!state.media.cameraEnabled) },
            )
            if (state.media.cameraEnabled) {
                CallButton(
                    icon = Icons.Filled.Cameraswitch,
                    background = SurfaceHighest,
                    label = "Flip",
                    onClick = viewModel::switchCamera,
                )
            }
            CallButton(
                icon = Icons.Filled.CallEnd,
                background = Color(0xFFD9483B),
                label = "End",
                onClick = viewModel::hangUp,
            )
        }
    }
}

@Composable
private fun CallEnded(viewModel: CallViewModel) {
    val state by viewModel.state.collectAsState()

    // Shown long enough to read, then out of the way by itself. Making someone dismiss the end of
    // a call they were in is a small insult.
    LaunchedEffect(state.endedReason) {
        delay(2_500)
        viewModel.dismissEnded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = viewModel::dismissEnded),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Call ${state.endedReason.ifBlank { "ended" }}",
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = TextTertiary)
    }
}

/**
 * A WebRTC video track, drawn.
 *
 * The renderer is a platform view with a native handle behind it, so it has to be initialised once
 * and released exactly once. Attaching and detaching the track is separate from that: the track
 * changes during a call — a camera switched on, a reconnect — and re-creating the surface each
 * time would flash black.
 */
@Composable
private fun VideoSurface(
    track: VideoTrack?,
    eglBase: EglBase,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    val renderer = remember(eglBase) { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                renderer.value = this
            }
        },
        onRelease = { view ->
            renderer.value = null
            runCatching { view.release() }
        },
    )

    DisposableEffect(track, renderer.value) {
        val view = renderer.value
        if (view != null && track != null) runCatching { track.addSink(view) }
        onDispose {
            if (view != null && track != null) runCatching { track.removeSink(view) }
        }
    }
}

private fun statusLine(state: CallState): String = when (state) {
    CallState.RINGING_OUT -> "Ringing…"
    CallState.CONNECTING -> "Connecting…"
    CallState.RECONNECTING -> "Reconnecting…"
    CallState.ACTIVE -> "Connected"
    else -> ""
}

/** Video needs the camera too; audio-only should not ask for it. */
internal fun needed(withVideo: Boolean): Array<String> =
    if (withVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    else arrayOf(Manifest.permission.RECORD_AUDIO)

internal fun granted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
