package com.auralis.app.network

import android.util.Log
import com.auralis.app.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicApi @Inject constructor(
    private val client: OkHttpClient
) {
    private val gson = Gson()

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20231204.01.00",
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "query": ${gson.toJson(query)}
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", "1.20231204.01.00")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w("YouTubeMusicApi", "Search request failed with HTTP ${response.code}")
                return@withContext emptyList()
            }
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            parseSearchResponse(responseBody)
        } catch (e: Exception) {
            Log.e("YouTubeMusicApi", "Search failed for query: $query", e)
            emptyList()
        }
    }

    private fun parseSearchResponse(jsonString: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            extractMusicItems(root, tracks)
        } catch (e: Exception) {
            Log.w("YouTubeMusicApi", "JSON parsing error", e)
        }
        return tracks
    }

    private fun extractMusicItems(element: JsonElement, result: MutableList<Track>) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("musicResponsiveListItemRenderer")) {
                val item = obj.getAsJsonObject("musicResponsiveListItemRenderer")
                parseItem(item)?.let { result.add(it) }
                return
            }
            for (entry in obj.entrySet()) {
                extractMusicItems(entry.value, result)
            }
        } else if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                extractMusicItems(item, result)
            }
        }
    }

    private fun parseItem(item: JsonObject): Track? {
        try {
            val videoId = item.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
                ?: return null

            val flexColumns = item.getAsJsonArray("flexColumns") ?: return null
            if (flexColumns.size() < 1) return null

            val col0Runs = flexColumns[0].asJsonObject
                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.getAsJsonArray("runs")

            val title = col0Runs?.get(0)?.asJsonObject?.get("text")?.asString ?: "Unknown Title"

            var artist = "Artist"
            var durationMs = 210000L

            if (flexColumns.size() > 1) {
                val col1Runs = flexColumns[1].asJsonObject
                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                    ?.getAsJsonArray("runs")

                if (col1Runs != null) {
                    for (i in 0 until col1Runs.size()) {
                        val txt = col1Runs.get(i).asJsonObject.get("text")?.asString?.trim() ?: continue
                        if (txt.matches(Regex("""\d+:\d+(?::\d+)?"""))) {
                            val parts = txt.split(":").mapNotNull { it.toLongOrNull() }
                            if (parts.size == 2) {
                                durationMs = (parts[0] * 60 + parts[1]) * 1000L
                            } else if (parts.size == 3) {
                                durationMs = (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
                            }
                        } else if (artist == "Artist" &&
                            !txt.equals("Song", true) &&
                            !txt.equals("Video", true) &&
                            !txt.equals("Album", true) &&
                            !txt.equals("Single", true) &&
                            !txt.equals("EP", true) &&
                            !txt.contains("•") &&
                            txt.isNotEmpty()
                        ) {
                            artist = txt
                        }
                    }
                }
            }

            var thumbUrl: String? = null
            val thumbnails = item.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")

            if (thumbnails != null && thumbnails.size() > 0) {
                val rawUrl = thumbnails.get(thumbnails.size() - 1).asJsonObject.get("url")?.asString
                thumbUrl = rawUrl?.replace(Regex("""=w\d+-h\d+.*"""), "=w500-h500-l90-rj")
            }

            return Track(
                id = "yt_$videoId",
                title = title,
                artist = artist,
                albumArtUrl = thumbUrl,
                mediaUrl = "https://www.youtube.com/watch?v=$videoId",
                durationMs = durationMs,
                source = "Auralis Master",
                qualityBadge = "320 kbps Master",
                isDownloaded = false
            )
        } catch (e: Exception) {
            return null
        }
    }
}
