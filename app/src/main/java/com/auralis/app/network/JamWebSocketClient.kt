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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    private var httpStreamJob: Job? = null
    private var reconnectJob: Job? = null
    private val recentMessageIds = RecentMessageIds()

    // Dedicated clean OkHttpClient with NO logging interceptor to prevent WebSocket handshake corruption
    private val wsClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _currentSession = MutableStateFlow<JamSession?>(null)
    val currentSession = _currentSession.asStateFlow()

    private val _jamState = MutableStateFlow<JamState>(JamState.Idle)
    val jamState = _jamState.asStateFlow()

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
        _isConnected.value = false
        connectInternal(session.jamId, session.username, session.isHost, isAutoReconnect = false)
    }

    private fun connectInternal(jamId: String, username: String, isHost: Boolean, isAutoReconnect: Boolean = false) {
        if (!JamProtocolHelper.isValidJamCode(jamId)) {
            _jamState.value = JamState.Error("Session code needs at least 4 letters or digits")
            return
        }

        if (!isAutoReconnect) {
            disconnect(sendLeaveNotice = false)
        } else {
            webSocket?.cancel()
            webSocket = null
            httpStreamJob?.cancel()
            httpStreamJob = null
        }

        val topic = JamProtocolHelper.cleanTopic(jamId)
        val session = _currentSession.value ?: JamSession(
            jamId = jamId.uppercase(),
            username = username.ifBlank { if (isHost) "Host" else "Partner" },
            isHost = isHost,
            participants = listOf(username.ifBlank { if (isHost) "Host" else "Partner" })
        )
        _currentSession.value = session

        // 1. Initial Fast Catch-Up Poll (retrieves state in <100ms even if sent before connecting)
        pollRecentMessages(topic)

        // 2. Start WebSocket Transport (primary low-latency)
        connectWebSocket(topic, session)

        // 3. Start Resilient HTTP Stream Transport in parallel (immune to carrier WebSocket blocks)
        startHttpStream(topic)
    }

    private fun connectWebSocket(topic: String, session: JamSession) {
        val wsUrl = "wss://ntfy.sh/$topic/ws"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("JamClient", "WebSocket connected to topic: $topic")
                _isConnected.value = true
                _jamState.value = JamState.Connected(session)

                broadcastUserJoined(session.username)
                broadcastRequestSync()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleNtfyEvent(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w("JamClient", "WebSocket closed: $code / $reason")
                // Don't mark disconnected immediately if HTTP stream is keeping connection alive
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("JamClient", "WebSocket transport failed (${t.message}), relying on HTTP stream transport", t)
            }
        })
    }

    private fun startHttpStream(topic: String) {
        httpStreamJob?.cancel()
        httpStreamJob = scope.launch {
            var backoffMs = 1500L
            while (isActive && _currentSession.value != null) {
                try {
                    val streamUrl = "https://ntfy.sh/$topic/json"
                    val request = Request.Builder().url(streamUrl).build()

                    wsClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            _isConnected.value = true
                            backoffMs = 1500L
                            val source = response.body.source()
                            while (!source.exhausted() && isActive && _currentSession.value != null) {
                                val line = source.readUtf8Line() ?: break
                                if (line.isBlank()) continue
                                handleNtfyEvent(line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("JamClient", "HTTP stream reconnecting: ${e.message}")
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(8000L)
            }
        }
    }

    private fun pollRecentMessages(topic: String) {
        scope.launch {
            try {
                val pollUrl = "https://ntfy.sh/$topic/json?poll=1&since=2m"
                val request = Request.Builder().url(pollUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        _isConnected.value = true
                        val body = response.body.string()
                        for (line in body.lines()) {
                            if (line.isNotBlank()) handleNtfyEvent(line)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("JamClient", "Recent message poll failed", e)
            }
        }
    }

    // Single ingress for the poll, the WebSocket and the HTTP stream, so a message that arrives on
    // more than one transport is only acted on once.
    private fun handleNtfyEvent(rawJsonLine: String) {
        val event = try {
            JamProtocolHelper.parseNtfyEvent(rawJsonLine)
        } catch (e: Exception) {
            Log.w("JamClient", "Error parsing ntfy event", e)
            null
        } ?: return

        when (event.event) {
            "message" -> {
                val body = event.message
                if (body.isNullOrEmpty()) return
                val id = event.id
                if (id == null || recentMessageIds.markSeen(id)) {
                    handleJamPayload(body)
                }
            }
            "open", "keepalive" -> _isConnected.value = true
        }
    }

    private fun handleJamPayload(jsonString: String) {
        val currentUsername = _currentSession.value?.username ?: ""
        val state = JamProtocolHelper.parseJamPayload(jsonString, currentUsername) ?: return

        when (state) {
            is JamState.UserJoined -> {
                _currentSession.value?.let { current ->
                    if (!current.participants.contains(state.username)) {
                        val updatedList = current.participants + state.username
                        _currentSession.value = current.copy(participants = updatedList)
                    }
                }
            }
            is JamState.UserLeft -> {
                _currentSession.value?.let { current ->
                    _currentSession.value = current.copy(participants = current.participants - state.username)
                }
            }
            else -> {}
        }
        _jamState.value = state
    }

    fun broadcastReaction(emoji: String) {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JamProtocolHelper.buildReactionJson(session.username, emoji)
                publishToTopic(session.jamId, json)
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast reaction", e)
            }
        }
    }

    fun broadcastMemoryQuote(quote: String = "I love you jaanaa 💋") {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JamProtocolHelper.buildMemoryQuoteJson(session.username, quote)
                publishToTopic(session.jamId, json)
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast memory quote", e)
            }
        }
    }

    fun broadcastPlaybackState(track: Track, position: Long, isPlaying: Boolean, action: String = "sync") {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JamProtocolHelper.buildSyncPlaybackJson(
                    session.username, shareableTrack(track), position, isPlaying, action
                )
                publishToTopic(session.jamId, json)
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast playback state", e)
            }
        }
    }

    fun broadcastRequestSync() {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JamProtocolHelper.buildRequestSyncJson(session.username)
                publishToTopic(session.jamId, json)
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast request sync", e)
            }
        }
    }

    private fun broadcastUserJoined(username: String) {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JamProtocolHelper.buildUserJoinedJson(username)
                publishToTopic(session.jamId, json)
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast user joined", e)
            }
        }
    }

    fun broadcastQueueTrack(track: Track) {
        val session = _currentSession.value ?: return
        scope.launch {
            try {
                val json = JamProtocolHelper.buildQueueTrackJson(session.username, shareableTrack(track))
                publishToTopic(session.jamId, json)
            } catch (e: Exception) {
                Log.e("JamClient", "Failed to broadcast queue track", e)
            }
        }
    }

    // Resolved googlevideo URLs are IP-bound and expire, so peers always get the watch URL instead.
    private fun shareableTrack(track: Track): Track {
        val cleanMediaUrl = if (track.isYouTubeTrack() && track.mediaUrl.contains("googlevideo.com")) {
            val vid = track.getYouTubeVideoId()
            if (vid != null) "https://www.youtube.com/watch?v=$vid" else track.mediaUrl
        } else {
            track.mediaUrl
        }
        return track.copy(mediaUrl = cleanMediaUrl)
    }

    private fun publishToTopic(jamId: String, payload: String) {
        val topic = JamProtocolHelper.cleanTopic(jamId)
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
        httpStreamJob?.cancel()
        httpStreamJob = null

        val session = _currentSession.value
        if (sendLeaveNotice && session != null && _isConnected.value) {
            val json = JamProtocolHelper.buildUserLeftJson(session.username)
            publishToTopic(session.jamId, json)
        }

        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        _currentSession.value = null
        _jamState.value = JamState.Idle
    }
}
