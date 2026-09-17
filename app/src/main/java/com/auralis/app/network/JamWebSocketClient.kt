package com.auralis.app.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamWebSocketClient @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()
    
    private val _jamState = MutableStateFlow<JamState>(JamState.Idle)
    val jamState = _jamState.asStateFlow()

    fun connect(jamId: String, username: String) {
        val request = Request.Builder()
            .url("wss://api.auralis.opensource/jam/$jamId?user=$username")
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                _jamState.value = JamState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.getString("type")) {
                        "sync_playback" -> {
                            val trackId = json.getString("trackId")
                            val position = json.getLong("position")
                            val isPlaying = json.getBoolean("isPlaying")
                            _jamState.value = JamState.SyncPlayback(trackId, position, isPlaying)
                        }
                        "user_joined" -> {
                            val user = json.getString("user")
                            _jamState.value = JamState.UserJoined(user)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                _jamState.value = JamState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                _jamState.value = JamState.Error(t.message ?: "Unknown error")
            }
        })
    }

    fun broadcastPlaybackState(trackId: String, position: Long, isPlaying: Boolean) {
        if (_isConnected.value) {
            val json = JSONObject().apply {
                put("type", "sync_playback")
                put("trackId", trackId)
                put("position", position)
                put("isPlaying", isPlaying)
            }
            webSocket?.send(json.toString())
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        _jamState.value = JamState.Idle
    }
}

sealed class JamState {
    object Idle : JamState()
    object Connected : JamState()
    data class SyncPlayback(val trackId: String, val position: Long, val isPlaying: Boolean) : JamState()
    data class UserJoined(val username: String) : JamState()
    object Disconnected : JamState()
    data class Error(val message: String) : JamState()
}
