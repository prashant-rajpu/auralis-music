package com.auralis.app.data.repository

import com.auralis.app.data.local.TogetherMessageDao
import com.auralis.app.data.local.TogetherMessageEntity
import com.auralis.app.together.StoredNote
import com.auralis.app.together.TogetherJson
import com.auralis.app.together.TogetherMailbox
import com.auralis.app.together.TogetherNote
import com.auralis.app.together.preview
import com.auralis.app.together.storedKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The conversation, on disk.
 *
 * Serializes the note into one column rather than spreading it over typed ones: note kinds will
 * keep being added, and a schema migration per new kind is not a trade worth making — the same
 * reasoning `jam_events` uses for its payload.
 *
 * A row whose payload no longer parses is dropped from the read rather than crashing it. That is
 * the shape a downgrade takes — a phone that has rolled back to a build which does not know a
 * newer note kind — and losing one message is a better outcome than an empty screen.
 */
@Singleton
class TogetherMailboxRepository @Inject constructor(
    private val dao: TogetherMessageDao,
) : TogetherMailbox {

    override fun messages(roomCode: String): Flow<List<StoredNote>> =
        dao.messages(roomCode).map { rows -> rows.mapNotNull(::toNote) }

    override suspend fun remember(message: StoredNote) {
        val kind = message.note.storedKind ?: return
        dao.insert(
            TogetherMessageEntity(
                id = message.id,
                roomCode = message.roomCode,
                senderId = message.senderId,
                senderName = message.senderName,
                fromMe = message.fromMe,
                kind = kind,
                payload = TogetherJson.encodeToString(TogetherNote.serializer(), message.note),
                preview = message.note.preview.take(280),
                atMs = message.atMs,
                pending = message.pending,
                ciphertext = message.ciphertext,
            ),
        )
    }

    override suspend fun outbox(roomCode: String): List<StoredNote> =
        dao.outbox(roomCode).mapNotNull(::toNote)

    override suspend fun markDelivered(id: String, atMs: Long) = dao.markDelivered(id, atMs)

    /** How many things they have said that this phone has not shown yet. */
    fun unreadCount(roomCode: String, sinceMs: Long): Flow<Int> = dao.unreadCount(roomCode, sinceMs)

    suspend fun clear(roomCode: String) = dao.clear(roomCode)

    private fun toNote(row: TogetherMessageEntity): StoredNote? {
        val note = runCatching {
            TogetherJson.decodeFromString(TogetherNote.serializer(), row.payload)
        }.getOrNull() ?: return null
        return StoredNote(
            id = row.id,
            roomCode = row.roomCode,
            senderId = row.senderId,
            senderName = row.senderName,
            fromMe = row.fromMe,
            note = note,
            atMs = row.atMs,
            pending = row.pending,
            ciphertext = row.ciphertext,
        )
    }
}
