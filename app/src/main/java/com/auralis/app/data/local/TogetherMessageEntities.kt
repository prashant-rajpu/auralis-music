package com.auralis.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing said in a room, kept on this phone.
 *
 * Chat used to live only in the session's UI state, which gave it the lifetime of a socket. For
 * two people whose use of this app is largely talking to each other while a song plays, that is
 * the wrong lifetime: a message should still be there tomorrow, and should be readable without
 * starting a session first.
 *
 * [id] is a digest of the ciphertext rather than a counter. Every message is sealed under a fresh
 * nonce, so the ciphertext is unique to it, and it is the same bytes whether the message arrives
 * as a live broadcast or inside the history a rejoin replays. That makes it exactly the key needed
 * to keep a rejoin from duplicating the last fifty messages.
 *
 * What lands here is plaintext. It is encrypted on the wire because the relay has no business
 * reading it; on the user's own phone, behind the app sandbox, it is a chat log.
 */
@Entity(
    tableName = "together_messages",
    indices = [Index("roomCode", "atMs"), Index("pending")]
)
data class TogetherMessageEntity(
    @PrimaryKey val id: String,
    val roomCode: String,
    val senderId: String,
    val senderName: String,
    val fromMe: Boolean,
    /** "text", "dedication", "lyric". Call signalling rides the same channel and is never stored. */
    val kind: String,
    /** The note, serialized. One column because note kinds will keep being added. */
    val payload: String,
    /** A line to show in a list or a notification without parsing [payload]. */
    val preview: String,
    /**
     * When it happened. This phone's clock until the relay stamps it, the room's clock after —
     * the room's being the one both phones agree on.
     */
    val atMs: Long,
    /** Written but not yet acknowledged by the relay. Pending rows are the outbox. */
    val pending: Boolean,
    /** Kept only while pending, so a retry puts the identical bytes back on the wire. */
    val ciphertext: String?
)
