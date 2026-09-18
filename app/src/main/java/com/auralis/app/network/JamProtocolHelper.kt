package com.auralis.app.network

import com.auralis.app.domain.model.Track
import com.google.gson.Gson
import kotlin.math.abs

data class JamTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val mediaUrl: String = "",
    val albumArtUrl: String? = null,
    val durationMs: Long = 0L,
    val qualityBadge: String = "320 kbps Master",
    val source: String = "Auralis Master"
)

data class JamMessageDto(
    val type: String = "",
    val sender: String = "",
    val user: String? = null,
    val position: Long = 0L,
    val isPlaying: Boolean = false,
    val action: String = "sync",
    val emoji: String? = null,
    val quote: String? = null,
    val track: JamTrackDto? = null
)

/**
 * Pure protocol helper for Spotify Jam & Together Mode sync.
 * Handles topic sanitization, JSON building, payload parsing, echo suppression, and drift calculations.
 */
object JamProtocolHelper {

    private val gson = Gson()

    fun cleanTopic(jamId: String): String {
        val sanitized = jamId.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return "auralis_jam_${if (sanitized.isNotEmpty()) sanitized else "global"}"
    }

    fun shouldSeek(currentPositionMs: Long, targetPositionMs: Long, thresholdMs: Long = 1500L): Boolean {
        return abs(currentPositionMs - targetPositionMs) > thresholdMs
    }

    fun buildSyncPlaybackJson(
        username: String,
        track: Track,
        position: Long,
        isPlaying: Boolean,
        action: String = "sync"
    ): String {
        val dto = JamMessageDto(
            type = "sync_playback",
            sender = username,
            position = position,
            isPlaying = isPlaying,
            action = action,
            track = JamTrackDto(
                id = track.id,
                title = track.title,
                artist = track.artist,
                mediaUrl = track.mediaUrl,
                albumArtUrl = track.albumArtUrl,
                durationMs = track.durationMs,
                qualityBadge = track.qualityBadge,
                source = track.source
            )
        )
        return gson.toJson(dto)
    }

    fun buildRequestSyncJson(username: String): String {
        val dto = JamMessageDto(
            type = "request_sync",
            sender = username
        )
        return gson.toJson(dto)
    }

    fun buildUserJoinedJson(username: String): String {
        val dto = JamMessageDto(
            type = "user_joined",
            sender = username,
            user = username
        )
        return gson.toJson(dto)
    }

    fun buildUserLeftJson(username: String): String {
        val dto = JamMessageDto(
            type = "user_left",
            sender = username,
            user = username
        )
        return gson.toJson(dto)
    }

    fun buildReactionJson(username: String, emoji: String): String {
        val dto = JamMessageDto(
            type = "reaction",
            sender = username,
            emoji = emoji
        )
        return gson.toJson(dto)
    }

    fun buildMemoryQuoteJson(username: String, quote: String): String {
        val dto = JamMessageDto(
            type = "memory_quote",
            sender = username,
            quote = quote
        )
        return gson.toJson(dto)
    }

    fun buildQueueTrackJson(username: String, track: Track): String {
        val dto = JamMessageDto(
            type = "queue_track",
            sender = username,
            track = JamTrackDto(
                id = track.id,
                title = track.title,
                artist = track.artist,
                mediaUrl = track.mediaUrl,
                albumArtUrl = track.albumArtUrl,
                durationMs = track.durationMs,
                qualityBadge = track.qualityBadge,
                source = track.source
            )
        )
        return gson.toJson(dto)
    }

    fun parseJamPayload(jsonString: String, currentUsername: String): JamState? {
        return try {
            val dto = gson.fromJson(jsonString, JamMessageDto::class.java) ?: return null

            // Echo suppression: Ignore own echoes (case-insensitive)
            if (dto.sender.equals(currentUsername, ignoreCase = true)) {
                return null
            }

            when (dto.type) {
                "sync_playback" -> {
                    val trackDto = dto.track ?: return null
                    val track = Track(
                        id = trackDto.id,
                        title = trackDto.title,
                        artist = trackDto.artist,
                        mediaUrl = trackDto.mediaUrl,
                        albumArtUrl = trackDto.albumArtUrl,
                        durationMs = trackDto.durationMs,
                        qualityBadge = trackDto.qualityBadge,
                        source = trackDto.source
                    )
                    JamState.SyncPlayback(track, dto.position, dto.isPlaying, dto.sender, dto.action)
                }

                "request_sync" -> {
                    JamState.RequestSync(dto.sender)
                }

                "user_joined" -> {
                    val joinedUser = dto.user ?: dto.sender
                    JamState.UserJoined(joinedUser)
                }

                "user_left" -> {
                    val leftUser = dto.user ?: dto.sender
                    JamState.UserLeft(leftUser)
                }

                "reaction" -> {
                    val emoji = dto.emoji ?: "❤️"
                    JamState.ReactionReceived(emoji, dto.sender)
                }

                "memory_quote" -> {
                    val quote = dto.quote ?: "I love you jaanaa 💋"
                    JamState.MemoryQuoteReceived(quote, dto.sender)
                }

                "queue_track" -> {
                    val trackDto = dto.track ?: return null
                    val track = Track(
                        id = trackDto.id,
                        title = trackDto.title,
                        artist = trackDto.artist,
                        mediaUrl = trackDto.mediaUrl,
                        albumArtUrl = trackDto.albumArtUrl,
                        durationMs = trackDto.durationMs,
                        qualityBadge = trackDto.qualityBadge,
                        source = trackDto.source
                    )
                    JamState.QueueTrack(track, dto.sender)
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
