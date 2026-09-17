package com.auralis.app.network

import android.util.Log
import com.auralis.app.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private data class CachedStream(
    val url: String,
    val expiresAtMs: Long
)

@Singleton
class YouTubeStreamResolver @Inject constructor(
    private val client: OkHttpClient,
    private val jioSaavnApi: JioSaavnApi
) {
    private val gson = Gson()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    // Default high-reliability visitor ID & signature timestamp for VisionOS client
    @Volatile private var cachedVisitorId: String = "CgtaXzRFMVhYMktQQSiqgrHVBjIKCgJBRRIEGgAgJg%3D%3D"
    @Volatile private var cachedSignatureTimestamp: Long = 20710L
    @Volatile private var lastTokenRefreshTime: Long = 0L

    suspend fun resolveStreamUrl(track: Track, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val videoId = track.getYouTubeVideoId()
        if (videoId.isNullOrBlank()) {
            return@withContext track.mediaUrl
        }

        // 1. Check in-memory cache if not forcing refresh
        if (!forceRefresh) {
            val cached = streamCache[videoId]
            if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
                Log.d("YouTubeStreamResolver", "Serving cached stream for $videoId")
                return@withContext cached.url
            }
        }

        // 2. Refresh visitor token if older than 12 hours
        ensureVisitorToken()

        // 3. Extract direct audio stream from YouTube Innertube VisionOS
        val directStreamUrl = extractFromInnertube(videoId)
        if (!directStreamUrl.isNullOrBlank()) {
            Log.d("YouTubeStreamResolver", "Successfully extracted direct GoogleVideo audio for $videoId")
            // Cache for 4 hours (GoogleVideo CDN links typically expire in 6 hours)
            streamCache[videoId] = CachedStream(
                url = directStreamUrl,
                expiresAtMs = System.currentTimeMillis() + (4 * 3600 * 1000L)
            )
            return@withContext directStreamUrl
        }

        // 4. Fallback: Search 320 kbps lossless catalog by track metadata
        Log.w("YouTubeStreamResolver", "Direct extraction failed for $videoId, trying master audio fallback")
        val fallbackUrl = resolveViaCatalog(track)
        if (!fallbackUrl.isNullOrBlank()) {
            Log.d("YouTubeStreamResolver", "Resolved $videoId via master audio fallback")
            streamCache[videoId] = CachedStream(
                url = fallbackUrl,
                expiresAtMs = System.currentTimeMillis() + (24 * 3600 * 1000L)
            )
            return@withContext fallbackUrl
        }

        // 5. If all fails, return original mediaUrl
        Log.e("YouTubeStreamResolver", "Could not resolve stream URL for ${track.title} ($videoId)")
        track.mediaUrl
    }

    private fun ensureVisitorToken() {
        val now = System.currentTimeMillis()
        if (now - lastTokenRefreshTime < 12 * 3600 * 1000L) return

        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val visMatch = Regex(""""visitorData":"([^"]+)"""").find(body)
                if (visMatch != null) {
                    cachedVisitorId = visMatch.groupValues[1]
                }
                val sigMatch = Regex(""""signatureTimestamp":(\d+)""").find(body)
                if (sigMatch != null) {
                    cachedSignatureTimestamp = sigMatch.groupValues[1].toLongOrNull() ?: 20710L
                }
            }
            lastTokenRefreshTime = now
        } catch (e: Exception) {
            Log.w("YouTubeStreamResolver", "Failed to refresh visitor token, using fallback", e)
        }
    }

    private fun extractFromInnertube(videoId: String): String? {
        try {
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "VISIONOS",
                            "clientVersion": "1.02",
                            "deviceMake": "Apple",
                            "deviceModel": "RealityDevice17,1",
                            "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                            "osName": "visionOS",
                            "osVersion": "26.5.23O471",
                            "hl": "en",
                            "timeZone": "UTC",
                            "utcOffsetMinutes": 0
                        }
                    },
                    "videoId": "$videoId",
                    "playbackContext": {
                        "contentPlaybackContext": {
                            "html5Preference": "HTML5_PREF_WANTS",
                            "signatureTimestamp": $cachedSignatureTimestamp
                        }
                    },
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                .header("X-YouTube-Client-Name", "101")
                .header("X-YouTube-Client-Version", "1.02")
                .header("Origin", "https://www.youtube.com")
                .header("X-Goog-Visitor-Id", cachedVisitorId)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = gson.fromJson(body, JsonObject::class.java)

                val playabilityStatus = root.getAsJsonObject("playabilityStatus")
                val status = playabilityStatus?.get("status")?.asString
                if (status != "OK") {
                    Log.w("YouTubeStreamResolver", "Innertube playability status: $status for $videoId")
                    return null
                }

                val streamingData = root.getAsJsonObject("streamingData") ?: return null
                val formats = streamingData.getAsJsonArray("adaptiveFormats") ?: return null

                var bestUrl: String? = null
                var maxBitrate = 0L

                for (elem in formats) {
                    val formatObj = elem.asJsonObject
                    val mimeType = formatObj.get("mimeType")?.asString ?: ""
                    val url = formatObj.get("url")?.asString

                    if (mimeType.contains("audio") && !url.isNullOrBlank()) {
                        val bitrate = formatObj.get("bitrate")?.asLong ?: 0L
                        if (bitrate > maxBitrate) {
                            maxBitrate = bitrate
                            bestUrl = url
                        }
                    }
                }
                return bestUrl
            }
        } catch (e: Exception) {
            Log.w("YouTubeStreamResolver", "Innertube extraction exception for $videoId", e)
            return null
        }
    }

    private suspend fun resolveViaCatalog(track: Track): String? {
        try {
            val cleanTitle = track.title.replace(Regex("(?i)\\(.*\\)|\\[.*\\]|official|video|audio|lyrics|hd|4k"), "").trim()
            val searchQuery = "$cleanTitle ${track.artist}".trim()
            val resp = jioSaavnApi.searchSongs(query = searchQuery, count = 1)
            val first = resp.results?.firstOrNull()

            if (first != null) {
                val mediaUrl = JioSaavnDecryptor.decryptMediaUrl(first.moreInfo?.encryptedMediaUrl)
                if (!mediaUrl.isNullOrBlank()) {
                    return mediaUrl
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeStreamResolver", "Catalog resolution failed for ${track.title}", e)
        }
        return null
    }
}
