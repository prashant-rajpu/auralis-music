package com.auralis.app.presentation.common

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.ui.theme.*

@Composable
fun TrackContextMenuBottomSheet(
    track: Track,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onViewArtist: (String) -> Unit,
    onToggleDownload: () -> Unit,
    isDownloaded: Boolean = track.isDownloaded,
    /** Supplied only when the menu is opened from inside a playlist. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var addingToPlaylist by remember { mutableStateOf(false) }

    // Nested rather than hoisted to every caller: the menu already has the track, so every screen
    // that shows the menu gets "Add to playlist" without threading state through itself.
    if (addingToPlaylist) {
        AddToPlaylistSheet(
            track = track,
            onDismiss = {
                addingToPlaylist = false
                onDismiss()
            }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = SurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.2.dp, BorderColor, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Track thumbnail, title, artist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(track.albumArtUrl)
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundElevated)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentColorSoft.copy(alpha = 0.3f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = track.qualityBadge,
                                    color = AccentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.hapticPress(scaleDown = 0.88f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Action 1: Play Next (Spotify / YT Music feature)
                ContextMenuOptionRow(
                    icon = Icons.Default.SkipNext,
                    title = "Play Next",
                    subtitle = "Insert directly after current song",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    }
                )

                // Action 2: Add to Queue
                ContextMenuOptionRow(
                    icon = Icons.Default.QueueMusic,
                    title = "Add to Queue",
                    subtitle = "Append to end of upcoming queue",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    }
                )

                // Action 3: Add to a playlist
                ContextMenuOptionRow(
                    icon = Icons.Default.PlaylistAdd,
                    title = "Add to Playlist",
                    subtitle = "Save it somewhere you will find it again",
                    onClick = { addingToPlaylist = true }
                )

                if (onRemoveFromPlaylist != null) {
                    ContextMenuOptionRow(
                        icon = Icons.Default.PlaylistRemove,
                        title = "Remove from Playlist",
                        subtitle = "Take it out of this playlist",
                        onClick = {
                            onRemoveFromPlaylist()
                            onDismiss()
                        }
                    )
                }

                // Action 4: Start Track Radio
                ContextMenuOptionRow(
                    icon = Icons.Default.Radio,
                    title = "Start Radio",
                    subtitle = "Stream similar tracks endlessly",
                    onClick = {
                        onStartRadio()
                        onDismiss()
                    }
                )

                // Action 4: View Artist Discography
                ContextMenuOptionRow(
                    icon = Icons.Default.Person,
                    title = "View Artist (${track.artist})",
                    subtitle = "Explore top tracks and releases",
                    onClick = {
                        onViewArtist(track.artist)
                        onDismiss()
                    }
                )

                // Action 5: Download or Delete Download (Internal Zero-Permission Storage)
                ContextMenuOptionRow(
                    icon = if (isDownloaded) Icons.Default.DeleteOutline else Icons.Default.Download,
                    title = if (isDownloaded) "Remove Download" else "Download for Offline",
                    subtitle = if (isDownloaded) "Delete from internal cache" else "Save offline without internet",
                    onClick = {
                        onToggleDownload()
                        onDismiss()
                    }
                )

                // Action 6: Share Song Link
                ContextMenuOptionRow(
                    icon = Icons.Default.Share,
                    title = "Share Song",
                    subtitle = "Share song info with friends or partner",
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Listen to ${track.title} on Auralis 💗")
                            val link = if (track.provider == Provider.YOUTUBE) {
                                "\nhttps://music.youtube.com/watch?v=${track.providerId}"
                            } else {
                                ""
                            }
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Listening to \"${track.title}\" by ${track.artist} on Auralis Music 💗✨$link"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share song via"))
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextMenuOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .hapticPress(scaleDown = 0.97f)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
