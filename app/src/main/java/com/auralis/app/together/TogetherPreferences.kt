package com.auralis.app.together

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** The last room this phone was in, so reopening the app can offer to walk back into it. */
data class LastRoom(val code: String, val token: String, val partnerName: String)

/**
 * Where the relay lives, who we are in a room, and how to get back into the last one.
 *
 * The member token is the only credential the relay knows, and it is what reclaims the host seat
 * after a drop, so it outlives the process. The room code is kept beside it for the same reason:
 * being asked to type a code again after a crash is the difference between a session that survived
 * and one that ended.
 */
@Singleton
class TogetherPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : RelayUrlProvider, TogetherStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auralis_together", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _relayUrl = MutableStateFlow(
        prefs.getString(KEY_RELAY_URL, null)?.takeIf { it.isNotBlank() }
            ?: RelayEndpoints.DEFAULT_BASE_URL,
    )
    val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, "").orEmpty())
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _lastRoom = MutableStateFlow(readLastRoom())
    override val lastRoom: StateFlow<LastRoom?> = _lastRoom.asStateFlow()

    override fun baseUrl(): String = _relayUrl.value

    /** True once this phone knows where to reach a relay at all. */
    val isConfigured: StateFlow<Boolean> =
        _relayUrl.map { RelayEndpoints.isConfigured(it) }
            .stateIn(scope, SharingStarted.Eagerly, RelayEndpoints.isConfigured(_relayUrl.value))

    /**
     * Rejects an address the transport could not use anyway, so the failure lands in Settings
     * rather than as a socket error twenty seconds into a session. Clearing it is always allowed:
     * that is how you get back to whatever the build shipped with.
     */
    fun setRelayUrl(url: String): Boolean {
        val candidate = url.trim()
        if (candidate.isEmpty()) {
            prefs.edit().remove(KEY_RELAY_URL).apply()
            _relayUrl.value = RelayEndpoints.DEFAULT_BASE_URL
            return true
        }
        if (RelayEndpoints.createRoom(candidate) == null) return false
        prefs.edit().putString(KEY_RELAY_URL, candidate).apply()
        _relayUrl.value = candidate
        return true
    }

    fun setDisplayName(name: String) {
        val trimmed = name.trim().take(64)
        prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
        _displayName.value = trimmed
    }

    override fun rememberRoom(code: String, token: String, partnerName: String) {
        prefs.edit()
            .putString(KEY_LAST_CODE, code)
            .putString(KEY_LAST_TOKEN, token)
            .putString(KEY_LAST_PARTNER, partnerName)
            .apply()
        _lastRoom.value = LastRoom(code, token, partnerName)
    }

    override fun forgetRoom() {
        prefs.edit()
            .remove(KEY_LAST_CODE)
            .remove(KEY_LAST_TOKEN)
            .remove(KEY_LAST_PARTNER)
            .apply()
        _lastRoom.value = null
    }

    /**
     * This phone's credential for a room it is joining rather than hosting. Generated once and
     * kept, because the relay treats a returning token as the same person.
     */
    override fun memberToken(): String {
        prefs.getString(KEY_MEMBER_TOKEN, null)?.takeIf { it.length >= TOKEN_LENGTH }?.let { return it }
        val minted = newToken()
        prefs.edit().putString(KEY_MEMBER_TOKEN, minted).apply()
        return minted
    }

    private fun readLastRoom(): LastRoom? {
        val code = prefs.getString(KEY_LAST_CODE, null) ?: return null
        val token = prefs.getString(KEY_LAST_TOKEN, null) ?: return null
        if (!RelayEndpoints.isValidCode(code)) return null
        return LastRoom(code, token, prefs.getString(KEY_LAST_PARTNER, "").orEmpty())
    }

    private fun newToken(): String =
        (1..TOKEN_LENGTH).map { HEX[Random.nextInt(HEX.length)] }.joinToString("")

    private companion object {
        const val KEY_RELAY_URL = "relay_url"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_LAST_CODE = "last_room_code"
        const val KEY_LAST_TOKEN = "last_room_token"
        const val KEY_LAST_PARTNER = "last_room_partner"
        const val KEY_MEMBER_TOKEN = "member_token"

        /** The relay refuses anything under 16 characters. */
        const val TOKEN_LENGTH = 32
        const val HEX = "0123456789abcdef"
    }
}
