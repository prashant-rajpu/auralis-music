package com.auralis.app.together

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Serializable
private data class CreateRoomResponse(val code: String = "", val hostToken: String = "")

/**
 * The relay transport: one WebSocket to one Durable Object, kept alive.
 *
 * Reconnection is the interesting part. A phone changing networks, or a screen that went off long
 * enough for the radio to drop, must come back into the *same* room rather than ending the session
 * — so the code and token are held here and replayed, and the relay answers with a fresh snapshot
 * that the sync controller catches up to. To the other person, a ten-second tunnel looks like the
 * music carrying on.
 */
@Singleton
class RelayWebSocketTransport @Inject constructor(
    private val relayUrl: RelayUrlProvider,
) : TogetherTransport {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val backoff = ReconnectBackoff()

    // No logging interceptor: it corrupts the upgrade handshake. Read timeout off, because a quiet
    // room is normal; liveness comes from the protocol-level ping instead.
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _connection = MutableStateFlow<TogetherConnection>(TogetherConnection.Idle)
    override val connection = _connection.asStateFlow()

    // Replay 0: a message is about the moment it arrived. A late collector wants the snapshot the
    // relay sends on connect, not a backlog of positions that were true a minute ago. And if a
    // collector ever falls behind, the newest position is the one worth keeping.
    private val _messages = MutableSharedFlow<TogetherServerMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val messages = _messages.asSharedFlow()

    // Written from OkHttp's callback thread, read from whichever thread the UI calls in on.
    @Volatile private var socket: WebSocket? = null
    @Volatile private var attempt = 0

    /** Set when the user leaves, so a close is not mistaken for a drop worth reconnecting from. */
    @Volatile private var leaving = false

    private var reconnectJob: Job? = null
    private var code: String? = null
    private var token: String? = null
    private var displayName: String = "Listener"

    override suspend fun createRoom(): Result<RoomCredentials> {
        val url = RelayEndpoints.createRoom(relayUrl.baseUrl())
            ?: return Result.failure(IOException("The relay address is not a valid https URL"))

        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            continuation.resume(Result.failure(IOException("Relay said ${it.code}")))
                            return
                        }
                        val parsed = runCatching {
                            TogetherJson.decodeFromString(CreateRoomResponse.serializer(), body)
                        }.getOrNull()
                        if (parsed == null || parsed.code.isBlank() || parsed.hostToken.isBlank()) {
                            continuation.resume(Result.failure(IOException("The relay sent back a room we cannot use")))
                        } else {
                            continuation.resume(
                                Result.success(RoomCredentials(parsed.code, parsed.hostToken)),
                            )
                        }
                    }
                }
            })
        }
    }

    override fun join(code: String, token: String, displayName: String) {
        leave()
        leaving = false
        this.code = code
        this.token = token
        this.displayName = displayName.ifBlank { "Listener" }
        attempt = 0
        open()
    }

    override fun send(message: TogetherClientMessage) {
        val open = socket ?: return
        open.send(TogetherProtocol.encode(message))
    }

    override fun leave() {
        leaving = true
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.let {
            runCatching { it.send(TogetherProtocol.encode(TogetherClientMessage.Bye)) }
            it.close(NORMAL_CLOSURE, "left")
        }
        socket = null
        code = null
        token = null
        _connection.value = TogetherConnection.Idle
    }

    private fun open() {
        val roomCode = code ?: return
        val roomToken = token ?: return
        val url = RelayEndpoints.socket(relayUrl.baseUrl(), roomCode, roomToken, displayName)
        if (url == null) {
            _connection.value = TogetherConnection.Failed("The relay address or room code is not valid")
            return
        }

        _connection.value =
            if (attempt == 0) TogetherConnection.Connecting
            else TogetherConnection.Reconnecting(attempt, 0)

        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    private inner class Listener : WebSocketListener() {

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = TogetherProtocol.decodeServerMessage(text)
            if (message == null) {
                Log.w(TAG, "Dropped a frame the relay sent that this version cannot read")
                return
            }
            if (message is TogetherServerMessage.Welcome) {
                attempt = 0
                _connection.value = TogetherConnection.Connected(message.memberId, message.hostId)
            }
            if (message is TogetherServerMessage.Presence) {
                val current = _connection.value
                if (current is TogetherConnection.Connected) {
                    _connection.value = current.copy(hostId = message.hostId)
                }
            }
            _messages.tryEmit(message)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            if (!leaving) scheduleReconnect("The room closed the connection")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
            // A 404 means the room is gone and no amount of retrying brings it back.
            if (response?.code == 404) {
                _connection.value = TogetherConnection.Failed("That room has expired")
                leaving = true
                return
            }
            if (!leaving) scheduleReconnect(t.message ?: "Lost the connection")
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (reconnectJob?.isActive == true) return
        val waitMs = backoff.delayForMs(attempt)
        _connection.value = TogetherConnection.Reconnecting(attempt + 1, waitMs)
        Log.i(TAG, "Reconnecting to the room in ${waitMs}ms ($reason)")
        reconnectJob = scope.launch {
            delay(waitMs)
            attempt++
            if (!leaving) open()
        }
    }

    private companion object {
        const val TAG = "RelayTransport"
        const val NORMAL_CLOSURE = 1000
    }
}

/** Where the relay lives. Editable, because a self-hosted one is the point. */
fun interface RelayUrlProvider {
    fun baseUrl(): String
}
