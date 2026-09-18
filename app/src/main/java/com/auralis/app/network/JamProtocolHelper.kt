package com.auralis.app.network

import com.auralis.app.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.abs

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
        val root = JsonObject().apply {
            addProperty("type", "sync_playback")
            addProperty("sender", username)
            addProperty("position", position)
            addProperty("isPlaying", isPlaying)
            addProperty("action", action)

            val trackObj = JsonObject().apply {
                addProperty("id", track.id)
                addProperty("title", track.title)
                addProperty("artist", track.artist)
                addProperty("mediaUrl", track.mediaUrl)
                addProperty("albumArtUrl", track.albumArtUrl ?: "")
                addProperty("durationMs", track.durationMs)
                addProperty("qualityBadge", track.qualityBadge)
                addProperty("source", track.source)
            }
            add("track", trackObj)
        }
        return gson.toJson(root)
    }

    fun buildRequestSyncJson(username: String): String {
        val root = JsonObject().apply {
            addProperty("type", "request_sync")
            addProperty("sender", username)
        }
        return gson.toJson(root)
    }

    fun buildUserJoinedJson(username: String): String {
        val root = JsonObject().apply {
            addProperty("type", "user_joined")
            addProperty("sender", username)
            addProperty("user", username)
        }
        return gson.toJson(root)
    }

    fun buildUserLeftJson(username: String): String {
        val root = JsonObject().apply {
            addProperty("type", "user_left")
            addProperty("sender", username)
            addProperty("user", username)
        }
        return gson.toJson(root)
    }

    fun buildReactionJson(username: String, emoji: String): String {
        val root = JsonObject().apply {
            addProperty("type", "reaction")
            addProperty("sender", username)
            addProperty("emoji", emoji)
        }
        return gson.toJson(root)
    }

    fun buildMemoryQuoteJson(username: String, quote: String): String {
        val root = JsonObject().apply {
            addProperty("type", "memory_quote")
            addProperty("sender", username)
            addProperty("quote", quote)
        }
        return gson.toJson(root)
    }

    fun buildQueueTrackJson(username: String, track: Track): String {
        val root = JsonObject().apply {
            addProperty("type", "queue_track")
            addProperty("sender", username)

            val trackObj = JsonObject().apply {
                addProperty("id", track.id)
                addProperty("title", track.title)
                addProperty("artist", track.artist)
                addProperty("mediaUrl", track.mediaUrl)
                addProperty("albumArtUrl", track.albumArtUrl ?: "")
                addProperty("durationMs", track.durationMs)
                addProperty("qualityBadge", track.qualityBadge)
                addProperty("source", track.source)
            }
            add("track", trackObj)
        }
        return gson.toJson(root)
    }

    fun parseJamPayload(jsonString: String, currentUsername: String): JamState? {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject
            val sender = json.get("sender")?.asString ?: ""

            // Echo suppression: Ignore own echoes (case-insensitive)
            if (sender.equals(currentUsername, ignoreCase = true)) {
                return null
            }

            val type = json.get("type")?.asString ?: return null

            when (type) {
                "sync_playback" -> {
                    val position = json.get("position")?.asLong ?: 0L
                    val isPlaying = json.get("isPlaying")?.asBoolean ?: false
                    val action = json.get("action")?.asString ?: "sync"
                    val trackObj = json.getAsJsonObject("track") ?: return null

                    val track = Track(
                        id = trackObj.get("id")?.asString ?: "",
                        title = trackObj.get("title")?.asString ?: "Unknown",
                        artist = trackObj.get("artist")?.asString ?: "Unknown",
                        mediaUrl = trackObj.get("mediaUrl")?.asString ?: "",
                        albumArtUrl = trackObj.get("albumArtUrl")?.asString?.ifEmpty { null },
                        durationMs = trackObj.get("durationMs")?.asLong ?: 0L,
                        qualityBadge = trackObj.get("qualityBadge")?.asString ?: "320 kbps Master",
                        source = trackObj.get("source")?.asString ?: "Auralis Master"
                    )
                    JamState.SyncPlayback(track, position, isPlaying, sender, action)
                }

                "request_sync" -> {
                    JamState.RequestSync(sender)
                }

                "user_joined" -> {
                    val joinedUser = json.get("user")?.asString ?: sender
                    JamState.UserJoined(joinedUser)
                }

                "user_left" -> {
                    val leftUser = json.get("user")?.asString ?: sender
                    JamState.UserLeft(leftUser)
                }

                "reaction" -> {
                    val emoji = json.get("emoji")?.asString ?: "❤️"
                    JamState.ReactionReceived(emoji, sender)
                }

                "memory_quote" -> {
                    val quote = json.get("quote")?.asString ?: "I love you jaanaa 💋"
                    JamState.MemoryQuoteReceived(quote, sender)
                }

                "queue_track" -> {
                    val trackObj = json.getAsJsonObject("track") ?: return null
                    val track = Track(
                        id = trackObj.get("id")?.asString ?: "",
                        title = trackObj.get("title")?.asString ?: "Unknown",
                        artist = trackObj.get("artist")?.asString ?: "Unknown",
                        mediaUrl = trackObj.get("mediaUrl")?.asString ?: "",
                        albumArtUrl = trackObj.get("albumArtUrl")?.asString?.ifEmpty { null },
                        durationMs = trackObj.get("durationMs")?.asLong ?: 0L,
                        qualityBadge = trackObj.get("qualityBadge")?.asString ?: "320 kbps Master",
                        source = trackObj.get("source")?.asString ?: "Auralis Master"
                    )
                    JamState.QueueTrack(track, sender)
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
