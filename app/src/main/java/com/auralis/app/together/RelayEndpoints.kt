package com.auralis.app.together

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Builds the two relay URLs.
 *
 * Separate from the transport because the relay address is user-editable — the whole point of
 * `docs/RELAY.md` is that anyone can run their own — and a typo in it should fail here, visibly,
 * rather than as a mystery socket error.
 */
object RelayEndpoints {

    const val DEFAULT_BASE_URL = "https://auralis-relay.workers.dev"

    fun createRoom(baseUrl: String): HttpUrl? =
        base(baseUrl)?.newBuilder()?.addPathSegment("rooms")?.build()

    fun socket(baseUrl: String, code: String, token: String, displayName: String): HttpUrl? {
        if (!isValidCode(code)) return null
        return base(baseUrl)
            ?.newBuilder()
            ?.addPathSegment("rooms")
            ?.addPathSegment(code.uppercase())
            ?.addPathSegment("ws")
            ?.addQueryParameter("token", token)
            ?.addQueryParameter("name", displayName)
            ?.build()
    }

    /** Mirrors the relay's alphabet exactly, so a bad code is rejected before a round trip. */
    fun isValidCode(code: String): Boolean =
        code.length == CODE_LENGTH && code.uppercase().all { it in CODE_ALPHABET }

    fun normalizeCode(input: String): String =
        input.uppercase().filter { it in CODE_ALPHABET }.take(CODE_LENGTH)

    /** OkHttp opens a WebSocket from the http(s) URL, so no scheme swap is needed — or wanted. */
    private fun base(baseUrl: String): HttpUrl? {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val url = trimmed.toHttpUrlOrNull() ?: return null
        // An http relay is only ever a local wrangler dev; anything public must be https.
        if (url.scheme != "https" && url.host != "localhost" && url.host != "127.0.0.1") return null
        return url
    }

    const val CODE_LENGTH = 6
    const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
}
