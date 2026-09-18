package com.auralis.app.data.remote.youtube

import android.util.Log
import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.StreamResolver
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
import com.auralis.app.network.JamProtocolHelper
import com.auralis.app.data.remote.jiosaavn.JioSaavnApi
import com.auralis.app.data.remote.jiosaavn.JioSaavnDecryptor

private data class CachedStream(
    val url: String,
    val expiresAtMs: Long
)

@Singleton
class YouTubeStreamResolver @Inject constructor(
    private val client: OkHttpClient,
    private val jioSaavnApi: JioSaavnApi
) : StreamResolver {
    private val gson = Gson()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    // Default high-reliability visitor ID & signature timestamp for VisionOS client
    @Volatile private var cachedVisitorId: String = "CgtaXzRFMVhYMktQQSiqgrHVBjIKCgJBRRIEGgAgJg%3D%3D"
    @Volatile private var cachedSignatureTimestamp: Long = 20710L
    @Volatile private var lastTokenRefreshTime: Long = 0L

    override val priority = 0

    override fun supports(track: Track): Boolean =
        !JamProtocolHelper.isLocalFileUrl(track.mediaUrl) && track.isYouTubeTrack()

    override suspend fun resolve(track: Track, quality: AudioQualitySetting, forceRefresh: Boolean): String? =
        withContext(Dispatchers.IO) {
            val videoId = track.getYouTubeVideoId()
            val cacheKey = "${videoId ?: track.id}:${quality.name}"

            // 1. Check in-memory cache if not forcing refresh
            if (!forceRefresh) {
                val cached = streamCache[cacheKey]
                if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
                    Log.d("YouTubeStreamResolver", "Serving cached stream for $cacheKey")
                    return@withContext cached.url
                }
            }

            // 2. Same song on JioSaavn: a stable 320 kbps AAC file beats an expiring googlevideo URL
            val catalogUrl = resolveViaCatalog(track, quality)
            if (!catalogUrl.isNullOrBlank()) {
                Log.d("YouTubeStreamResolver", "Resolved $cacheKey via catalog match")
                streamCache[cacheKey] = CachedStream(
                    url = catalogUrl,
                    expiresAtMs = System.currentTimeMillis() + (12 * 3600 * 1000L)
                )
                return@withContext catalogUrl
            }

            // 3. YouTube Innertube direct audio extraction
            if (!videoId.isNullOrBlank()) {
                ensureVisitorToken()
                val directStreamUrl = extractFromInnertube(videoId, quality)
                if (!directStreamUrl.isNullOrBlank()) {
                    Log.d("YouTubeStreamResolver", "Extracted direct GoogleVideo audio for $videoId")
                    streamCache[cacheKey] = CachedStream(
                        url = directStreamUrl,
                        expiresAtMs = System.currentTimeMillis() + (4 * 3600 * 1000L)
                    )
                    return@withContext directStreamUrl
                }
            }

            // 4. An already-resolved googlevideo URL is fine to reuse
            if (JamProtocolHelper.isPlayableDirectStreamUrl(track.mediaUrl)) {
                return@withContext track.mediaUrl
            }

            Log.w("YouTubeStreamResolver", "Could not resolve stream URL for ${track.title} ($videoId)")
            null
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

    private fun extractFromInnertube(videoId: String, quality: AudioQualitySetting): String? {
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

                val audio = formats.mapNotNull { elem ->
                    val formatObj = elem.asJsonObject
                    val mimeType = formatObj.get("mimeType")?.asString ?: ""
                    val url = formatObj.get("url")?.asString
                    if (mimeType.contains("audio") && !url.isNullOrBlank()) {
                        (formatObj.get("bitrate")?.asLong ?: 0L) to url
                    } else {
                        null
                    }
                }
                return pickFormat(audio, quality)
            }
        } catch (e: Exception) {
            Log.w("YouTubeStreamResolver", "Innertube extraction exception for $videoId", e)
            return null
        }
    }

    private suspend fun resolveViaCatalog(track: Track, quality: AudioQualitySetting): String? {
        try {
            val searchQuery = JamProtocolHelper.cleanSearchQuery(track.title, track.artist)
            val resp = jioSaavnApi.searchSongs(query = searchQuery, count = 5)
            for (candidate in resp.results.orEmpty()) {
                val encrypted = candidate.moreInfo?.encryptedMediaUrl
                if (!encrypted.isNullOrBlank()) {
                    val mediaUrl = JioSaavnDecryptor.decryptMediaUrl(encrypted)
                    if (!mediaUrl.isNullOrBlank()) {
                        return JioSaavnDecryptor.withQuality(mediaUrl, quality)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeStreamResolver", "Catalog resolution failed for ${track.title}", e)
        }
        return null
    }

    companion object {
        /** Highest bitrate at or under the setting's ceiling; the lowest available when nothing fits. */
        fun pickFormat(audioFormats: List<Pair<Long, String>>, quality: AudioQualitySetting): String? {
            if (audioFormats.isEmpty()) return null
            val ceilingBps = when (quality) {
                AudioQualitySetting.HIGH -> Long.MAX_VALUE
                AudioQualitySetting.STANDARD -> 170_000L
                AudioQualitySetting.DATA_SAVER -> 100_000L
            }
            val fitting = audioFormats.filter { it.first <= ceilingBps }
            return (fitting.maxByOrNull { it.first } ?: audioFormats.minByOrNull { it.first })?.second
        }
    }
}
