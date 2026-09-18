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
    private val jioSaavnApi: JioSaavnApi,
    private val openSourceMusicApi: OpenSourceMusicApi
) {
    private val gson = Gson()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    // Default high-reliability visitor ID & signature timestamp for VisionOS client
    @Volatile private var cachedVisitorId: String = "CgtaXzRFMVhYMktQQSiqgrHVBjIKCgJBRRIEGgAgJg%3D%3D"
    @Volatile private var cachedSignatureTimestamp: Long = 20710L
    @Volatile private var lastTokenRefreshTime: Long = 0L

    suspend fun resolveStreamUrl(track: Track, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val videoId = track.getYouTubeVideoId()

        // If mediaUrl is already a playable direct audio stream and not a web link, return it immediately
        if (videoId.isNullOrBlank() && JamProtocolHelper.isPlayableDirectStreamUrl(track.mediaUrl)) {
            return@withContext track.mediaUrl
        }

        val cacheKey = videoId ?: track.id

        // 1. Check in-memory cache if not forcing refresh
        if (!forceRefresh) {
            val cached = streamCache[cacheKey]
            if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
                Log.d("YouTubeStreamResolver", "Serving cached stream for $cacheKey")
                return@withContext cached.url
            }
        }

        // 2. High-speed Lossless Master Stream via JioSaavn / OpenSource Catalog
        val masterStreamUrl = resolveViaCatalog(track)
        if (!masterStreamUrl.isNullOrBlank()) {
            Log.d("YouTubeStreamResolver", "Resolved $cacheKey via lossless master catalog: $masterStreamUrl")
            streamCache[cacheKey] = CachedStream(
                url = masterStreamUrl,
                expiresAtMs = System.currentTimeMillis() + (12 * 3600 * 1000L)
            )
            return@withContext masterStreamUrl
        }

        // 3. Fallback: YouTube Innertube Direct Audio Extraction
        if (!videoId.isNullOrBlank()) {
            ensureVisitorToken()
            val directStreamUrl = extractFromInnertube(videoId)
            if (!directStreamUrl.isNullOrBlank()) {
                Log.d("YouTubeStreamResolver", "Successfully extracted direct GoogleVideo audio for $videoId")
                streamCache[cacheKey] = CachedStream(
                    url = directStreamUrl,
                    expiresAtMs = System.currentTimeMillis() + (4 * 3600 * 1000L)
                )
                return@withContext directStreamUrl
            }
        }

        // 4. Validate if original mediaUrl is directly playable
        if (JamProtocolHelper.isPlayableDirectStreamUrl(track.mediaUrl)) {
            return@withContext track.mediaUrl
        }

        Log.e("YouTubeStreamResolver", "Could not resolve stream URL for ${track.title} ($videoId)")
        // Never return an unplayable web page URL to ExoPlayer
        ""
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
            val searchQuery = JamProtocolHelper.cleanSearchQuery(track.title, track.artist)
            val resp = jioSaavnApi.searchSongs(query = searchQuery, count = 5)
            val candidates = resp.results.orEmpty()

            for (candidate in candidates) {
                val encrypted = candidate.moreInfo?.encryptedMediaUrl
                if (!encrypted.isNullOrBlank()) {
                    val mediaUrl = JioSaavnDecryptor.decryptMediaUrl(encrypted)
                    if (!mediaUrl.isNullOrBlank()) {
                        return mediaUrl
                    }
                }
            }

            // Fallback: OpenSource / Deezer preview stream
            val deezerResp = openSourceMusicApi.searchTracks(query = searchQuery, limit = 1)
            val firstDeezer = deezerResp.data?.firstOrNull()
            if (!firstDeezer?.preview.isNullOrBlank()) {
                return firstDeezer?.preview
            }
        } catch (e: Exception) {
            Log.w("YouTubeStreamResolver", "Catalog resolution failed for ${track.title}", e)
        }
        return null
    }
}

