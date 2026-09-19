package com.auralis.app.together

import com.auralis.app.domain.model.Track

/**
 * Where a session goes to be remembered.
 *
 * An interface for the same reason [TogetherStore] and [TogetherPlayer] are: the session is the
 * part worth testing, and it should not need a Room database to prove that it files a shared play
 * once per track rather than once per tick.
 */
interface TogetherRecorder {

    /** Opens a row for this session and returns the id every play and event is filed under. */
    suspend fun beginSession(roomCode: String, partnerName: String?, wasHost: Boolean): String

    /** A track that played while you were both actually in the room. */
    suspend fun recordSharedPlay(
        sessionId: String,
        track: Track,
        partnerName: String?,
        addedByMe: Boolean,
    )

    suspend fun recordEvent(
        sessionId: String,
        type: String,
        payload: String?,
        fromMe: Boolean,
        trackId: String?,
    )

    suspend fun endSession(sessionId: String)

    companion object {
        const val EVENT_DEDICATION = "dedication"
        const val EVENT_LYRIC = "lyric"
        const val EVENT_KNOCK = "knock"
        const val EVENT_CHAT = "chat"
    }
}
