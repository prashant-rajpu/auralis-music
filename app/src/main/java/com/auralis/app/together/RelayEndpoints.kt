package com.auralis.app.together

import com.auralis.app.BuildConfig
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

    /**
     * Blank until a build supplies one. There is deliberately no fallback hostname: a plausible
     * but wrong default fails as a mystery socket error, where an empty one can say "no relay
     * configured yet" and point at Settings.
     */
    val DEFAULT_BASE_URL: String get() = BuildConfig.RELAY_URL

    /** Nothing to talk to yet — neither the build nor the user has named a relay. */
    fun isConfigured(baseUrl: String): Boolean = createRoom(baseUrl) != null

    fun createRoom(baseUrl: String): HttpUrl? =
        base(baseUrl)?.newBuilder()?.addPathSegment("rooms")?.build()

    fun socket(
        baseUrl: String,
        code: String,
        token: String,
        displayName: String,
        timeZoneId: String = "",
    ): HttpUrl? {
        if (!isValidCode(code)) return null
        return base(baseUrl)
            ?.newBuilder()
            ?.addPathSegment("rooms")
            ?.addPathSegment(code.uppercase())
            ?.addPathSegment("ws")
            ?.addQueryParameter("token", token)
            ?.addQueryParameter("name", displayName)
            ?.addQueryParameter("tz", timeZoneId)
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
