package com.auralis.app.presentation.together

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.data.repository.CoupleRepository
import com.auralis.app.data.repository.TogetherMailboxRepository
import com.auralis.app.domain.model.Track
import com.auralis.app.playback.PlaybackManager
import com.auralis.app.together.EncryptionStrength
import com.auralis.app.together.Invite
import com.auralis.app.together.RelayEndpoints
import com.auralis.app.together.TogetherInvite
import com.auralis.app.together.TogetherPlayer
import com.auralis.app.together.TogetherPreferences
import com.auralis.app.together.TogetherSession
import com.auralis.app.together.StoredNote
import com.auralis.app.together.Streak
import com.auralis.app.together.TrackRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TogetherViewModel @Inject constructor(
    private val session: TogetherSession,
    private val preferences: TogetherPreferences,
    private val player: TogetherPlayer,
    private val couple: CoupleRepository,
    private val mailbox: TogetherMailboxRepository,
    private val playback: PlaybackManager,
) : ViewModel() {

    val state = session.state
    val reactions = session.reactions
    val knocks = session.knocks
    val displayName = preferences.displayName
    val lastRoom = preferences.lastRoom
    val relayUrl = preferences.relayUrl
    val relayConfigured = preferences.isConfigured

    /** What the two of you have built up. Empty until a session has actually happened. */
    val streak = couple.streak.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Streak(0, false))
    val ourSongs = couple.ourSongs(limit = 30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories = couple.memories(limit = 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Everything the two of you have said in this room, whether or not a session is running.
     *
     * Read from the room this phone remembers rather than from the live session, which is the
     * whole point: the conversation should be there when the app opens, before anyone has
     * reconnected to anything, and it should still be there tomorrow.
     */
    val conversation = preferences.lastRoom
        .flatMapLatest { room -> room?.let { mailbox.messages(it.code) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<StoredNote>())

    /** The invite for the room this phone created, so it can be shared and scanned. */
    private val _invite = MutableStateFlow<Invite?>(null)
    val invite = _invite.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    var joinInput by mutableStateOf("")
        private set

    var chatDraft by mutableStateOf("")
        private set

    var nameDraft by mutableStateOf(preferences.displayName.value)
        private set

    var dedicationDraft by mutableStateOf("")
        private set

    val encryption: EncryptionStrength
        get() = state.value.room?.encryption ?: EncryptionStrength.CODE_ONLY

    /** True while what was typed could not be a room to join, so the button can say so quietly. */
    val joinInputLooksWrong: Boolean
        get() = joinInput.isNotBlank() && TogetherInvite.parse(joinInput) == null

    fun onJoinInputChange(value: String) {
        joinInput = value
        _error.value = null
    }

    fun onChatDraftChange(value: String) {
        chatDraft = value
    }

    fun onDedicationDraftChange(value: String) {
        dedicationDraft = value.take(500)
    }

    fun onNameDraftChange(value: String) {
        nameDraft = value.take(64)
    }

    fun saveName() {
        preferences.setDisplayName(nameDraft)
    }

    fun start() {
        if (_busy.value) return
        preferences.setDisplayName(nameDraft)
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            session.host(myName()).fold(
                onSuccess = { _invite.value = it },
                onFailure = { _error.value = it.message ?: "Could not reach the relay" },
            )
            _busy.value = false
        }
    }

    fun joinFromInput() {
        val invite = TogetherInvite.parse(joinInput)
        if (invite == null) {
            _error.value = "That does not look like an invite"
            return
        }
        preferences.setDisplayName(nameDraft)
        _invite.value = invite
        session.join(invite, myName())
        joinInput = ""
    }

    /** From a shared link or a QR scan that arrived as an intent. */
    fun joinFrom(invite: Invite) {
        _invite.value = invite
        session.join(invite, myName())
    }

    fun resume() {
        if (!session.resumeLastRoom(myName())) {
            _error.value = "There is no session to go back to"
        }
    }

    fun leave() {
        session.leave()
        _invite.value = null
    }

    fun send() {
        session.sendChat(chatDraft)
        chatDraft = ""
    }

    fun react(emoji: String) = session.sendReaction(emoji)

    /** Thinking of you. One tap, no typing. */
    fun knock() = session.knock()

    /** Dedicates whatever is playing right now; the note is what makes it a dedication. */
    fun dedicateCurrent(note: String) {
        val track = nowPlaying() ?: return
        session.dedicate(track, note)
        dedicationDraft = ""
    }

    fun sendLyricMoment(line: String, positionMs: Long) = session.sendLyricMoment(line, positionMs)

    fun goodnightIn(minutes: Int) = session.goodnightIn(minutes * 60_000L)

    fun cancelGoodnight() = session.cancelGoodnight()

    fun removeFromQueue(ref: TrackRef) = session.removeFromSharedQueue(ref)

    fun dismissError() {
        _error.value = null
    }

    fun shareText(): String? {
        val invite = _invite.value ?: state.value.room?.let { Invite(it.code, it.secret) } ?: return null
        return TogetherInvite.shareText(invite.code, invite.secret, myName())
    }

    fun inviteLink(): String? {
        val invite = _invite.value ?: state.value.room?.let { Invite(it.code, it.secret) } ?: return null
        return TogetherInvite.link(invite.code, invite.secret)
    }

    fun nowPlaying(): Track? = player.currentTrack

    /** Plays Our Songs as a queue, starting where they tapped. */
    fun playOurSongs(startIndex: Int) {
        val songs = ourSongs.value
        if (songs.isEmpty()) return
        playback.playPlaylist(songs, startIndex.coerceIn(songs.indices))
    }

    private fun myName(): String = nameDraft.trim().ifEmpty { "Listener" }

    companion object {
        val QUICK_REACTIONS = listOf("❤️", "🔥", "🥹", "😂", "🌙", "🎧")

        /** Sleep-timer lengths that fire on both phones at once. */
        val GOODNIGHT_MINUTES = listOf(15, 30, 45, 60)
        const val CODE_LENGTH = RelayEndpoints.CODE_LENGTH
    }
}
