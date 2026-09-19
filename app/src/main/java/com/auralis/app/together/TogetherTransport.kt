package com.auralis.app.together

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Credentials for a room this phone just created. The token is what reclaims the host seat. */
data class RoomCredentials(val code: String, val hostToken: String)

sealed interface TogetherConnection {
    data object Idle : TogetherConnection
    data object Connecting : TogetherConnection

    /** In the room. [memberId] is what the relay stamps on our own messages coming back. */
    data class Connected(val memberId: String, val hostId: String) : TogetherConnection

    /** Dropped, and coming back. Worth showing: a session recovering is not a session that ended. */
    data class Reconnecting(val attempt: Int, val retryInMs: Long) : TogetherConnection

    data class Failed(val reason: String) : TogetherConnection
}

/**
 * How this phone talks to the other one.
 *
 * An interface because the relay is new and the ntfy transport it replaces still works. Keeping
 * both behind this lets the migration happen without a flag day, and lets a test drive a session
 * with no network at all.
 */
interface TogetherTransport {

    val connection: StateFlow<TogetherConnection>

    /** Every message the room sends us, in arrival order. */
    val messages: Flow<TogetherServerMessage>

    suspend fun createRoom(): Result<RoomCredentials>

    /** Joins, and keeps rejoining with the same token until [leave]. */
    fun join(code: String, token: String, displayName: String)

    /**
     * Fire and forget. A message sent while the socket is down is dropped rather than queued:
     * playback state is a snapshot, and a stale one arriving after reconnection would be worse
     * than none. Anything that must survive a drop is re-sent from the room snapshot instead.
     */
    fun send(message: TogetherClientMessage)

    fun leave()
}
