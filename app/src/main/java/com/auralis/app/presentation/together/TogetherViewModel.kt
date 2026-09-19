package com.auralis.app.presentation.together

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.together.EncryptionStrength
import com.auralis.app.together.Invite
import com.auralis.app.together.RelayEndpoints
import com.auralis.app.together.TogetherInvite
import com.auralis.app.together.TogetherPreferences
import com.auralis.app.together.TogetherSession
import com.auralis.app.together.TrackRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TogetherViewModel @Inject constructor(
    private val session: TogetherSession,
    private val preferences: TogetherPreferences,
) : ViewModel() {

    val state = session.state
    val reactions = session.reactions
    val displayName = preferences.displayName
    val lastRoom = preferences.lastRoom
    val relayUrl = preferences.relayUrl

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

    private fun myName(): String = nameDraft.trim().ifEmpty { "Listener" }

    companion object {
        val QUICK_REACTIONS = listOf("❤️", "🔥", "🥹", "😂", "🌙", "🎧")
        const val CODE_LENGTH = RelayEndpoints.CODE_LENGTH
    }
}
