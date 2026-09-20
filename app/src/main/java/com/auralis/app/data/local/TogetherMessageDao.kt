package com.auralis.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TogetherMessageDao {

    /** Oldest first, which is the order a conversation is read in. */
    @Query("SELECT * FROM together_messages WHERE roomCode = :roomCode ORDER BY atMs ASC")
    fun messages(roomCode: String): Flow<List<TogetherMessageEntity>>

    /**
     * IGNORE rather than REPLACE: the same message arrives twice by design — once live, once in
     * the history a rejoin replays — and the first copy is the one that may still be pending.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: TogetherMessageEntity)

    /** Everything written while there was nowhere to send it, oldest first. */
    @Query("SELECT * FROM together_messages WHERE roomCode = :roomCode AND pending = 1 ORDER BY atMs ASC")
    suspend fun outbox(roomCode: String): List<TogetherMessageEntity>

    /**
     * The relay took it and stamped it. Drops the ciphertext at the same time: it existed only so
     * a retry could resend the identical bytes, and there is no reason to keep a second copy.
     */
    @Query("UPDATE together_messages SET pending = 0, atMs = :serverMs, ciphertext = NULL WHERE id = :id")
    suspend fun markDelivered(id: String, serverMs: Long)

    @Query("SELECT COUNT(*) FROM together_messages WHERE roomCode = :roomCode AND fromMe = 0 AND atMs > :sinceMs")
    fun unreadCount(roomCode: String, sinceMs: Long): Flow<Int>

    @Query("SELECT * FROM together_messages WHERE fromMe = 0 ORDER BY atMs DESC LIMIT :limit")
    fun latestFromThem(limit: Int): Flow<List<TogetherMessageEntity>>

    @Query("DELETE FROM together_messages WHERE roomCode = :roomCode")
    suspend fun clear(roomCode: String)
}
