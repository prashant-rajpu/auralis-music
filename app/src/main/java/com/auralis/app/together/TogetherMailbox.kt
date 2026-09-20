package com.auralis.app.together

import kotlinx.coroutines.flow.Flow

/**
 * One message, as this phone keeps it.
 *
 * [ciphertext] is present only while the message is still pending. It is the exact bytes that were
 * sealed, so a retry puts the identical frame back on the wire — which means the copy the relay
 * echoes back carries the same [id] and collides with this row instead of doubling it.
 */
data class StoredNote(
    val id: String,
    val roomCode: String,
    val senderId: String,
    val senderName: String,
    val fromMe: Boolean,
    val note: TogetherNote,
    val atMs: Long,
    val pending: Boolean = false,
    val ciphertext: String? = null,
)

/**
 * Where what the two of you said to each other is kept.
 *
 * Separate from [TogetherRecorder] because the lifetimes are different. A recorder writes a
 * session's timeline — what you played, on that evening, in that room. A mailbox is the
 * conversation, which outlives any one session and should be readable without starting one: the
 * whole point of chat for two people six thousand kilometres apart is that it is still there in
 * the morning.
 *
 * An interface for the same reason the rest of these are: the session's job is to file a message
 * once and retry the ones that never left, and proving that should not need a database.
 */
interface TogetherMailbox {

    /** Everything said in this room, oldest first. */
    fun messages(roomCode: String): Flow<List<StoredNote>>

    /** Files a message. A repeat of one already held is ignored rather than duplicated. */
    suspend fun remember(message: StoredNote)

    /** What was written while there was nowhere to send it, oldest first. */
    suspend fun outbox(roomCode: String): List<StoredNote>

    /** The relay took it and stamped it with the room's clock, which is the shared one. */
    suspend fun markDelivered(id: String, atMs: Long)
}

/**
 * What kind of message this is for storage, or null for the ones that are not conversation.
 *
 * A goodnight is a sleep timer and the call notes are a handshake. Neither belongs in a thread the
 * two of you will read back later, and a candidate list in particular would bury it.
 */
val TogetherNote.storedKind: String?
    get() = when (this) {
        is TogetherNote.Text -> "text"
        is TogetherNote.Dedication -> "dedication"
        is TogetherNote.LyricMoment -> "lyric"
        is TogetherNote.Knock -> "knock"
        else -> null
    }

/** One line, for a list row or a notification, without anyone having to parse the payload. */
val TogetherNote.preview: String
    get() = when (this) {
        is TogetherNote.Text -> body
        is TogetherNote.Dedication -> if (note.isBlank()) track.title else "${track.title} — $note"
        is TogetherNote.LyricMoment -> "“$line”"
        is TogetherNote.Knock -> "Thinking of you"
        else -> ""
    }
