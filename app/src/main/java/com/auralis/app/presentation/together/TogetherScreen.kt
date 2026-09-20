package com.auralis.app.presentation.together

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.app.data.repository.SessionMemory
import com.auralis.app.together.EncryptionStrength
import com.auralis.app.together.ListeningStreak
import com.auralis.app.together.Invite
import com.auralis.app.together.Member
import com.auralis.app.together.PartnerClock
import com.auralis.app.together.QueueEntry
import com.auralis.app.together.SyncState
import com.auralis.app.together.TogetherChatMessage
import com.auralis.app.together.TogetherConnection
import com.auralis.app.together.TogetherNote
import com.auralis.app.together.TogetherRoom
import com.auralis.app.together.TogetherUiState
import com.auralis.app.ui.theme.*
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Together, as a place in the app rather than a sheet you summon.
 *
 * Two states, and they are genuinely different screens. Out of a session it is about getting into
 * one: your name, a big start button, an invite to paste, and the room you were in last. In a
 * session it is about the other person: where they are, what time it is there, whether the two
 * phones are actually together, and a way to say something.
 */
@Composable
fun TogetherScreen(
    viewModel: TogetherViewModel = hiltViewModel(),
    pendingInvite: Invite? = null,
    onInviteHandled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    // An invite that arrived from a shared link or a QR scan joins on its own: tapping it was the
    // consent, and making someone press a second button to honour their own tap is just friction.
    LaunchedEffect(pendingInvite) {
        pendingInvite?.let {
            viewModel.joinFrom(it)
            onInviteHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isActive) {
            LiveSession(state = state, viewModel = viewModel)
        } else {
            StartOrJoin(viewModel = viewModel)
        }
    }
}

// ---------------------------------------------------------------- not in a session

@Composable
private fun StartOrJoin(viewModel: TogetherViewModel) {
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val lastRoom by viewModel.lastRoom.collectAsState()
    val invite by viewModel.invite.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val ourSongs by viewModel.ourSongs.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val relayConfigured by viewModel.relayConfigured.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Together",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The same song, at the same moment, wherever you both are.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            }
        }

        if (!relayConfigured) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .surfaceCard(cornerRadius = 20.dp)
                        .padding(18.dp),
                ) {
                    Text(
                        text = "No relay yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WarningColor,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Together needs a small server to hold the shared clock. Deploy one " +
                            "with the steps in docs/RELAY.md, then paste its address into " +
                            "Settings \u203a Together. It is free, and it stays yours.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
            }
        }

        if (streak.days > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .surfaceCard(cornerRadius = 20.dp)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔥", fontSize = 22.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ListeningStreak.label(streak),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (streak.atRisk) WarningColor else TextPrimary,
                        )
                        if (streak.atRisk) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "One session today and it carries on",
                                fontSize = 12.sp,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }

        error?.let { message ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .surfaceCard(cornerRadius = 18.dp)
                        .clickable { viewModel.dismissError() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = DangerColor,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Dismiss", fontSize = 12.sp, color = TextTertiary)
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .surfaceCard(cornerRadius = 24.dp)
                    .padding(20.dp),
            ) {
                Text("You are", fontSize = 12.sp, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.nameDraft,
                    onValueChange = viewModel::onNameDraftChange,
                    placeholder = { Text("Your name", color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = auralisFieldColors(),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .surfaceCard(cornerRadius = 24.dp)
                    .padding(20.dp),
            ) {
                Text(
                    text = "Start a session",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (relayConfigured) "You get a code and a link. Share either one."
                    else "Set a relay address in Settings first.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::start,
                    enabled = !busy && relayConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = OnAccentColor,
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = OnAccentColor,
                        )
                    } else {
                        Text("Start listening together", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        invite?.let { pending ->
            item {
                InviteCard(
                    code = pending.code,
                    link = viewModel.inviteLink().orEmpty(),
                    onCopy = { clipboard.setText(AnnotatedString(viewModel.inviteLink().orEmpty())) },
                    onShare = { share(context, viewModel.shareText().orEmpty()) },
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .surfaceCard(cornerRadius = 24.dp)
                    .padding(20.dp),
            ) {
                Text(
                    text = "Join theirs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Paste the link they sent, or type the six characters.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.joinInput,
                    onValueChange = viewModel::onJoinInputChange,
                    placeholder = { Text("Code or link", color = TextTertiary) },
                    singleLine = true,
                    isError = viewModel.joinInputLooksWrong,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { viewModel.joinFromInput() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = auralisFieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = viewModel::joinFromInput,
                    enabled = relayConfigured &&
                        viewModel.joinInput.isNotBlank() && !viewModel.joinInputLooksWrong,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Join", color = AccentColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (ourSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Our songs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                Text(
                    text = "What you actually listened to together, most-played first",
                    fontSize = 12.sp,
                    color = TextTertiary,
                )
            }
            itemsIndexed(ourSongs, key = { _, track -> track.id }) { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .surfaceCard(cornerRadius = 16.dp)
                        .clickable { viewModel.playOurSongs(index) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            fontSize = 11.sp,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (memories.isNotEmpty()) {
            item {
                Text(
                    text = "Sessions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(memories, key = { it.id }) { memory ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .surfaceCard(cornerRadius = 16.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = memory.partnerName?.let { "With $it" } ?: "A session",
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                        Text(
                            text = sessionSubtitle(memory),
                            fontSize = 11.sp,
                            color = TextTertiary,
                        )
                    }
                }
            }
        }

        lastRoom?.let { room ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .surfaceCard(cornerRadius = 20.dp)
                        .clickable { viewModel.resume() }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (room.partnerName.isBlank()) "Go back to ${room.code}"
                            else "Go back to listening with ${room.partnerName}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Your last session is still open for 30 days",
                            fontSize = 12.sp,
                            color = TextTertiary,
                        )
                    }
                    Text("Resume", fontSize = 13.sp, color = AccentColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InviteCard(code: String, link: String, onCopy: () -> Unit, onShare: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(cornerRadius = 24.dp)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Their way in", fontSize = 12.sp, color = TextTertiary)
        Spacer(Modifier.height(10.dp))
        Text(
            text = code,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
            color = AccentColor,
        )
        Spacer(Modifier.height(16.dp))

        if (link.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(10.dp),
            ) {
                InviteQrCode(
                    content = link,
                    modifier = Modifier.fillMaxSize(),
                    foreground = Color.Black,
                    background = Color.White,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "The link carries a secret the relay never sees, so a scanned or shared " +
                    "invite keeps your messages private. Reading the code aloud cannot.",
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCopy, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp), tint = AccentColor)
                Spacer(Modifier.width(6.dp))
                Text("Copy link", color = AccentColor, fontSize = 13.sp)
            }
            Button(
                onClick = onShare,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = OnAccentColor,
                ),
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- in a session

@Composable
private fun LiveSession(state: TogetherUiState, viewModel: TogetherViewModel) {
    val room = state.room ?: return
    var knockFrom by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.knocks.collect { knock ->
            knockFrom = knock.senderName
            delay(3_000)
            knockFrom = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = knockFrom != null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentColorSoft)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("👋", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${knockFrom.orEmpty()} is thinking of you",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PartnerCard(room = room, connection = state.connection, onLeave = viewModel::leave) }
            item { SyncBadge(state = state) }
            item { CoupleActions(state = state, viewModel = viewModel) }

            state.unplayable?.let { ref ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(cornerRadius = 18.dp)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "\"${ref.title}\" is not available in this edition",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = WarningColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "They are still listening. Nothing here could find a copy of it.",
                            fontSize = 12.sp,
                            color = TextTertiary,
                        )
                    }
                }
            }

            if (state.queue.isNotEmpty()) {
                item {
                    Text(
                        text = "Up next, between you",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    )
                }
                items(state.queue, key = { it.track.key + it.addedBy }) { entry ->
                    SharedQueueRow(entry = entry, room = room, onRemove = { viewModel.removeFromQueue(entry.track) })
                }
            }

            item { EncryptionNote(strength = room.encryption) }

            if (state.chat.isNotEmpty()) {
                items(state.chat, key = { it.senderId + it.serverMs }) { message ->
                    ChatBubble(message)
                }
            }
        }

        ReactionTray(onReact = viewModel::react)
        ChatComposer(
            draft = viewModel.chatDraft,
            onDraftChange = viewModel::onChatDraftChange,
            onSend = viewModel::send,
        )
    }
}

/**
 * The three things that only make sense between two people: a knock, a song dedicated with
 * something said about it, and a sleep timer that stops both phones on the same beat.
 */
@Composable
private fun CoupleActions(state: TogetherUiState, viewModel: TogetherViewModel) {
    var dedicating by remember { mutableStateOf(false) }
    var choosingGoodnight by remember { mutableStateOf(false) }
    val playing = viewModel.nowPlaying()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(cornerRadius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(label = "👋  Knock", onClick = viewModel::knock, modifier = Modifier.weight(1f))
            ActionChip(
                label = "💌  Dedicate",
                enabled = playing != null,
                onClick = { dedicating = !dedicating },
                modifier = Modifier.weight(1f),
            )
            ActionChip(
                label = if (state.goodnightAtServerMs != null) "🌙  Set" else "🌙  Goodnight",
                enabled = state.clockReady,
                onClick = {
                    if (state.goodnightAtServerMs != null) viewModel.cancelGoodnight()
                    else choosingGoodnight = !choosingGoodnight
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.goodnightAtServerMs != null) {
            Text(
                text = "Both phones will fade out together. Tap 🌙 again to call it off.",
                fontSize = 11.sp,
                color = TextTertiary,
            )
        }

        AnimatedVisibility(visible = choosingGoodnight && state.goodnightAtServerMs == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in TogetherViewModel.GOODNIGHT_MINUTES) {
                    ActionChip(
                        label = "${minutes}m",
                        onClick = {
                            viewModel.goodnightIn(minutes)
                            choosingGoodnight = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AnimatedVisibility(visible = dedicating && playing != null) {
            Column {
                Text(
                    text = "Dedicating \u201c${playing?.title.orEmpty()}\u201d",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.dedicationDraft,
                    onValueChange = viewModel::onDedicationDraftChange,
                    placeholder = { Text("Say why", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = auralisFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.dedicateCurrent(viewModel.dedicationDraft)
                        dedicating = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = OnAccentColor,
                    ),
                ) {
                    Text("Send it", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceHighest)
            .hapticPress(scaleDown = 0.94f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) TextPrimary else TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PartnerCard(room: TogetherRoom, connection: TogetherConnection, onLeave: () -> Unit) {
    val partner = room.partner

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(cornerRadius = 24.dp)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(AccentColorSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (partner?.name ?: "?").take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentColor,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partner?.name ?: "Waiting for them to join",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = connectionLabel(connection, partner),
                    fontSize = 12.sp,
                    color = TextTertiary,
                )
            }
            TextButton(onClick = onLeave) {
                Text("Leave", color = DangerColor, fontSize = 13.sp)
            }
        }

        partner?.timeZone?.takeIf { it.isNotBlank() }?.let { zone ->
            val now = remember(zone) { Instant.now() }
            val theirTime = PartnerClock.timeFor(zone, now)
            val offset = PartnerClock.offsetLabel(zone, now = now)
            if (theirTime != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceHighest)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "It is $theirTime there",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                        offset?.let {
                            Spacer(Modifier.height(1.dp))
                            Text(text = it, fontSize = 11.sp, color = TextTertiary)
                        }
                    }
                    if (PartnerClock.isLateThere(zone, now)) {
                        Text("🌙", fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Room ${room.code}",
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = TextTertiary,
        )
    }
}

private fun connectionLabel(connection: TogetherConnection, partner: Member?): String = when (connection) {
    is TogetherConnection.Connecting -> "Connecting…"
    is TogetherConnection.Reconnecting -> "Reconnecting…"
    is TogetherConnection.Failed -> connection.reason
    is TogetherConnection.Idle -> "Not connected"
    is TogetherConnection.Connected -> when {
        partner == null -> "You are the only one here"
        partner.buffering -> "Buffering on their side"
        else -> "Listening with you"
    }
}

/**
 * Visible honesty about what the connection is doing. A player that quietly jumps is unsettling;
 * one that says "catching up" first is not.
 */
@Composable
private fun SyncBadge(state: TogetherUiState) {
    val (label, tint) = when {
        !state.clockReady -> "Measuring the connection…" to TextTertiary
        state.syncState == SyncState.IN_SYNC -> "In sync" to SuccessColor
        state.syncState == SyncState.CATCHING_UP -> "Catching up" to WarningColor
        state.syncState == SyncState.CORRECTED -> "Caught up" to WarningColor
        state.syncState == SyncState.WAITING_FOR_PEER -> "Waiting for them to load" to TextSecondary
        state.syncState == SyncState.LOADING_TRACK -> "Loading their song" to TextSecondary
        state.syncState == SyncState.PAUSED -> "Paused, both of you" to TextSecondary
        else -> "Waiting for an update" to TextTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(cornerRadius = 16.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        if (state.clockReady) {
            Text(
                text = "${state.driftMs.coerceAtLeast(-9999)} ms",
                fontSize = 11.sp,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun EncryptionNote(strength: EncryptionStrength) {
    val text = when (strength) {
        EncryptionStrength.LINK_SECRET ->
            "Messages here are encrypted with a secret only your two phones have."
        EncryptionStrength.CODE_ONLY ->
            "This session was opened from a typed code, so its key comes from those six " +
                "characters. Share the link next time for a stronger one."
    }
    Text(
        text = text,
        fontSize = 11.sp,
        color = if (strength == EncryptionStrength.LINK_SECRET) TextTertiary else WarningColor,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SharedQueueRow(entry: QueueEntry, room: TogetherRoom, onRemove: () -> Unit) {
    val who = room.members.firstOrNull { it.id == entry.addedBy }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(cornerRadius = 16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.track.title,
                fontSize = 14.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(entry.track.artist)
                    if (who != null) append(" · added by ${who.name}")
                },
                fontSize = 11.sp,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRemove) {
            Text("Remove", fontSize = 12.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun ChatBubble(message: TogetherChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.isMine) AccentColorSoft else SurfaceHighest)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!message.isMine) {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentColor,
                )
                Spacer(Modifier.height(2.dp))
            }

            when (val note = message.note) {
                null -> Text(
                    text = "Sent with a different invite, so this phone has no key for it",
                    fontSize = 12.sp,
                    color = TextTertiary,
                )

                is TogetherNote.Text -> Text(note.body, fontSize = 14.sp, color = TextPrimary)

                is TogetherNote.Dedication -> {
                    Text(
                        text = if (message.isMine) "You dedicated" else "Dedicated to you",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = TextTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note.track.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(note.track.artist, fontSize = 12.sp, color = TextSecondary)
                    if (note.note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "\u201c${note.note}\u201d",
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                    }
                }

                is TogetherNote.LyricMoment -> {
                    Text(
                        text = "\u201c${note.line}\u201d",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${note.track.title} \u00b7 ${note.track.artist}",
                        fontSize = 11.sp,
                        color = TextTertiary,
                    )
                }

                // Knocks, sleep timers and call signalling are all routed elsewhere before they
                // reach here; this branch exists so a new note kind cannot silently show nothing.
                else -> Text("\u2026", fontSize = 14.sp, color = TextTertiary)

            }
        }
    }
}

@Composable
private fun ReactionTray(onReact: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (emoji in TogetherViewModel.QUICK_REACTIONS) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .hapticPress(scaleDown = 0.88f)
                    .clickable { onReact(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun ChatComposer(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("Say something", color = TextTertiary) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = auralisFieldColors(),
        )
        Spacer(Modifier.width(8.dp))
        AnimatedVisibility(visible = draft.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(AccentColor)
                    .hapticPress(scaleDown = 0.9f)
                    .clickable { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = OnAccentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun auralisFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = BorderColor,
    cursorColor = AccentColor,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)

/** "3 songs · Tuesday" — enough to place it, without a paragraph. */
private fun sessionSubtitle(memory: SessionMemory): String {
    val songs = when (memory.trackCount) {
        0 -> "No songs yet"
        1 -> "1 song"
        else -> "${memory.trackCount} songs"
    }
    val day = java.time.Instant.ofEpochMilli(memory.startedAtMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    return "$songs \u00b7 $day"
}

private fun share(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Invite them to listen"))
}
