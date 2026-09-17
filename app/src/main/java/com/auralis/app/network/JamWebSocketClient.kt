package com.auralis.app.network

import android.util.Log
import com.auralis.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class JamSession(
    val jamId: String,
    val username: String,
    val isHost: Boolean,
    val participants: List<String> = listOf()
)

sealed class JamState {
    object Idle : JamState()
    data class Connected(val session: JamSession) : JamState()
    data class SyncPlayback(
        val track: Track,
        val position: Long,
        val isPlaying: Boolean,
        val sender: String,
        val action: String = "sync"
    ) : JamState()
    data class UserJoined(val username: String) : JamState()
    data class RequestSync(val sender: String) : JamState()
    data class UserLeft(val username: String) : JamState()
    data class QueueTrack(val track: Track, val sender: String) : JamState()
    data class ReactionReceived(val emoji: String, val sender: String) : JamState()
    data class MemoryQuoteReceived(val quote: String, val sender: String) : JamState()
    object Disconnected : JamState()
    data class Error(val message: String) : JamState()
}

@Singleton
class JamWebSocketClient @Inject constructor(
    private val client: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _currentSession = MutableStateFlow<JamSession?>(null)
    val currentSession = _currentSession.asStateFlow()

    private val _jamState = MutableStateFlow<JamState>(JamState.Idle)
    val jamState = _jamState.asStateFlow()

    private fun cleanTopic(jamId: String): String {
        val sanitized = jamId.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return "auralis_jam_${if (sanitized.isNotEmpty()) sanitized else "global"}"
    }

    fun startJam(jamId: String, username: String) {
        connectInternal(jamId, username, isHost = true)
    }

    fun joinJam(jamId: String, username: String) {
        connectInternal(jamId, username, isHost = false)
    }

    fun connect(jamId: String, username: String) {
        connectInternal(jamId, username, isHost = false)
    }

    private fun connectInternal(jamId: String, username: String, isHost: Boolean) {
        disconnect()

        val topic = cleanTopic(jamId)
        val session = JamSession(
            jamId = jamId.uppercase(),
            username = username.ifBlank { if (isHost) "Host" else "Partner" },
            isHost = isHost,
            participants = listOf(username.ifBlank { if (isHost) "Host" else "Partner" })
        )
        _currentSession.value = session

        val wsUrl = "wss://ntfy.sh/$topic/ws"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                _jamState.value = JamState.Connected(session)

                // Announce user joined and request current playing state immediately
                broadcastUserJoined(session.username)
                broadcastRequestSync()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val ntfyMsg = JSONObject(text)
                    if (ntfyMsg.optString("event") == "message") {
                        val messageBody = ntfyMsg.optString("message")
                        if (messageBody.isNotEmpty()) {
                            handleJamPayload(messageBody)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("JamClient", "Error parsing incoming jam msg", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                _jamState.value = JamState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                _jamState.value = JamState.Error(t.message ?: "Connection error")
            }
        })
    }

    private fun handleJamPayload(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val sender = json.optString("sender")
            val currentUsername = _currentSession.value?.username ?: ""

            // Ignore our own published echoes
            if (sender.equals(currentUsername, ignoreCase = true)) {
                return
            }

            when (json.optString("type")) {
                "sync_playback" -> {
                    val position = json.optLong("position", 0L)
                    val isPlaying = json.optBoolean("isPlaying", false)
                    val action = json.optString("action", "sync")
                    val trackObj = json.optJSONObject("track")

                    if (trackObj != null) {
                        val track = Track(
                            id = trackObj.optString("id"),
                            title = trackObj.optString("title"),
                            artist = trackObj.optString("artist"),
                            mediaUrl = trackObj.optString("mediaUrl"),
                            albumArtUrl = trackObj.optString("albumArtUrl").ifEmpty { null },
                            durationMs = trackObj.optLong("durationMs", 0L),
                            qualityBadge = trackObj.optString("qualityBadge", "320 kbps Master"),
                            source = trackObj.optString("source", "Auralis Master")
                        )
                        _jamState.value = JamState.SyncPlayback(track, position, isPlaying, sender, action)
                    }
                }

                "request_sync" -> {
                    _jamState.value = JamState.RequestSync(sender)
                }

                "user_joined" -> {
                    val joinedUser = json.optString("user", sender)
                    _currentSession.value?.let { current ->
                        if (!current.participants.contains(joinedUser)) {
                            val updatedList = current.participants + joinedUser
                            _currentSession.value = current.copy(participants = updatedList)
                        }
                    }
                    _jamState.value = JamState.UserJoined(joinedUser)
                }

                "queue_track" -> {
                    val trackObj = json.optJSONObject("track")
                    if (trackObj != null) {
                        val track = Track(
                            id = trackObj.optString("id"),
                            title = trackObj.optString("title"),
                            artist = trackObj.optString("artist"),
                            mediaUrl = trackObj.optString("mediaUrl"),
                            albumArtUrl = trackObj.optString("albumArtUrl").ifEmpty { null },
                            durationMs = trackObj.optLong("durationMs", 0L),
                            qualityBadge = trackObj.optString("qualityBadge", "320 kbps Master"),
                            source = trackObj.optString("source", "Auralis Master")
                        )
                        _jamState.value = JamState.QueueTrack(track, sender)
                    }
                }

                "user_left" -> {
                    val leftUser = json.optString("user", sender)
                    _currentSession.value?.let { current ->
                        _currentSession.value = current.copy(participants = current.participants - leftUser)
                    }
                    _jamState.value = JamState.UserLeft(leftUser)
                }

                "reaction" -> {
                    val emoji = json.optString("emoji", "❤️")
                    val reactionSender = json.optString("sender", "Partner")
                    _jamState.value = JamState.ReactionReceived(emoji, reactionSender)
                }

                "memory_quote" -> {
                    val quote = json.optString("quote", "I love you jaanaa 💋")
                    val quoteSender = json.optString("sender", "Partner")
                    _jamState.value = JamState.MemoryQuoteReceived(quote, quoteSender)
                }
            }
        } catch (e: Exception) {
            Log.e("JamClient", "Failed to deserialize jam payload", e)
        }
    }

    fun broadcastReaction(emoji: String) {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "reaction")
                    put("sender", session.username)
                    put("emoji", emoji)
                }
                publishToTopic(session.jamId, json.toString())
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast reaction", e)
            }
        }
    }

    fun broadcastMemoryQuote(quote: String = "I love you jaanaa 💋") {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "memory_quote")
                    put("sender", session.username)
                    put("quote", quote)
                }
                publishToTopic(session.jamId, json.toString())
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast memory quote", e)
            }
        }
    }

    fun broadcastPlaybackState(track: Track, position: Long, isPlaying: Boolean, action: String = "sync") {
        val session = _currentSession.value ?: return
        if (!_isConnected.value) return

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "sync_playback")
                    put("sender", session.username)
                    put("position", position)
                    put("isPlaying", isPlaying)
                    put("action", action)
                    put("track", JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("mediaUrl", track.mediaUrl)
                        put("albumArtUrl", track.albumArtUrl ?: "")
                        put("durationMs", track.durationMs)
                        put("qualityBadge", track.qualityBadge)
                        put("source", track.source)
                    })
                }
                publishToTopic(session.jamId, json.toString())
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast playback state", e)
            }
        }
    }

    fun broadcastRequestSync() {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "request_sync")
                    put("sender", session.username)
                }
                publishToTopic(session.jamId, json.toString())
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast request sync", e)
            }
        }
    }

    private fun broadcastUserJoined(username: String) {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "user_joined")
                    put("sender", username)
                    put("user", username)
                }
                publishToTopic(session.jamId, json.toString())
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast user joined", e)
            }
        }
    }

    fun broadcastQueueTrack(track: Track) {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "queue_track")
                    put("sender", session.username)
                    put("track", JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("mediaUrl", track.mediaUrl)
                        put("albumArtUrl", track.albumArtUrl ?: "")
                        put("durationMs", track.durationMs)
                        put("qualityBadge", track.qualityBadge)
                        put("source", track.source)
                    })
                }
                publishToTopic(session.jamId, json.toString())
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast queue track", e)
            }
        }
    }

    private fun publishToTopic(jamId: String, payload: String) {
        val topic = cleanTopic(jamId)
        val url = "https://ntfy.sh/$topic"
        val requestBody = payload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("JamClient", "Failed to publish ntfy message", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun disconnect() {
        val session = _currentSession.value
        if (session != null && _isConnected.value) {
            val json = JSONObject().apply {
                put("type", "user_left")
                put("sender", session.username)
                put("user", session.username)
            }
            publishToTopic(session.jamId, json.toString())
        }

        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        _currentSession.value = null
        _jamState.value = JamState.Idle
    }
}
