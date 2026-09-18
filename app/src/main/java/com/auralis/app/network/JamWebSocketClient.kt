package com.auralis.app.network

import android.util.Log
import com.auralis.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
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
    private var reconnectJob: Job? = null

    // Dedicated WebSocket client with RFC-compliant 10s pingInterval, zero read timeout,
    // and automatic retry on connection failure
    private val wsClient = client.newBuilder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

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

    fun reconnect() {
        val session = _currentSession.value ?: return
        connectInternal(session.jamId, session.username, session.isHost, isAutoReconnect = false)
    }

    private fun connectInternal(jamId: String, username: String, isHost: Boolean, isAutoReconnect: Boolean = false) {
        if (!isAutoReconnect) {
            disconnect(sendLeaveNotice = false)
        } else {
            webSocket?.cancel()
            webSocket = null
        }

        val topic = cleanTopic(jamId)
        val session = _currentSession.value ?: JamSession(
            jamId = jamId.uppercase(),
            username = username.ifBlank { if (isHost) "Host" else "Partner" },
            isHost = isHost,
            participants = listOf(username.ifBlank { if (isHost) "Host" else "Partner" })
        )
        _currentSession.value = session

        val wsUrl = "wss://ntfy.sh/$topic/ws"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("JamClient", "WebSocket connected successfully to topic: $topic")
                _isConnected.value = true
                _jamState.value = JamState.Connected(session)

                // Announce user joined and request current playing state immediately
                broadcastUserJoined(session.username)
                broadcastRequestSync()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val ntfyMsg = JSONObject(text)
                    val event = ntfyMsg.optString("event")
                    if (event == "message") {
                        val messageBody = ntfyMsg.optString("message")
                        if (messageBody.isNotEmpty()) {
                            handleJamPayload(messageBody)
                        }
                    } else if (event == "open" || event == "keepalive") {
                        _isConnected.value = true
                    }
                } catch (e: Exception) {
                    Log.e("JamClient", "Error parsing incoming jam msg", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w("JamClient", "WebSocket closed: $code / $reason")
                _isConnected.value = false
                _jamState.value = JamState.Disconnected
                if (_currentSession.value != null && code != 1000) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("JamClient", "WebSocket failure: ${t.message}", t)
                _isConnected.value = false
                _jamState.value = JamState.Error(t.message ?: "Connection error")
                if (_currentSession.value != null) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        val session = _currentSession.value ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2500L)
            if (_currentSession.value != null && !_isConnected.value) {
                Log.d("JamClient", "Attempting automatic Jam reconnect for session ${session.jamId}...")
                connectInternal(session.jamId, session.username, session.isHost, isAutoReconnect = true)
            }
        }
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

        scope.launch {
            try {
                // If it's a YouTube track, send the original YouTube URL or videoId so the partner can resolve cleanly
                val cleanMediaUrl = if (track.isYouTubeTrack() && track.mediaUrl.contains("googlevideo.com")) {
                    val vid = track.getYouTubeVideoId()
                    if (vid != null) "https://www.youtube.com/watch?v=$vid" else track.mediaUrl
                } else {
                    track.mediaUrl
                }

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
                        put("mediaUrl", cleanMediaUrl)
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
                val cleanMediaUrl = if (track.isYouTubeTrack() && track.mediaUrl.contains("googlevideo.com")) {
                    val vid = track.getYouTubeVideoId()
                    if (vid != null) "https://www.youtube.com/watch?v=$vid" else track.mediaUrl
                } else {
                    track.mediaUrl
                }

                val json = JSONObject().apply {
                    put("type", "queue_track")
                    put("sender", session.username)
                    put("track", JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("mediaUrl", cleanMediaUrl)
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

    fun disconnect(sendLeaveNotice: Boolean = true) {
        reconnectJob?.cancel()
        reconnectJob = null

        val session = _currentSession.value
        if (sendLeaveNotice && session != null && _isConnected.value) {
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
