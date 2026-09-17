package com.auralis.app.network

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

private data class RawYtTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?
)

@Singleton
class YouTubeMusicApi @Inject constructor(
    private val client: OkHttpClient,
    private val jioSaavnApi: JioSaavnApi
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
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", "1.20231204.01.00")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val rawTracks = parseSearchResponse(responseBody)

            val playableTracks = mutableListOf<Track>()
            for (raw in rawTracks.take(15)) {
                val resolved = resolvePlayableTrack(raw)
                if (resolved != null) {
                    playableTracks.add(resolved)
                }
            }
            playableTracks
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun resolvePlayableTrack(raw: RawYtTrack): Track? {
        try {
            val cleanTitle = raw.title.replace(Regex("(?i)\\(.*\\)|\\[.*\\]|official|video|audio|lyrics"), "").trim()
            val searchQuery = "$cleanTitle ${raw.artist}".trim()
            val resp = jioSaavnApi.searchSongs(query = searchQuery, count = 1)
            val first = resp.results?.firstOrNull()

            if (first != null) {
                val mediaUrl = JioSaavnDecryptor.decryptMediaUrl(first.moreInfo?.encryptedMediaUrl)
                if (!mediaUrl.isNullOrBlank()) {
                    val durationSec = first.moreInfo?.duration?.toLongOrNull() ?: 210L
                    val art = raw.albumArtUrl ?: first.image?.replace("150x150", "500x500")

                    return Track(
                        id = "yt_${raw.videoId}",
                        title = raw.title,
                        artist = raw.artist,
                        albumArtUrl = art,
                        mediaUrl = mediaUrl,
                        durationMs = durationSec * 1000L,
                        source = "Auralis Master",
                        qualityBadge = "320 kbps Master",
                        isDownloaded = false
                    )
                }
            }
        } catch (e: Exception) {
            // Resolution fallback failed
        }
        return null
    }

    private fun parseSearchResponse(jsonString: String): List<RawYtTrack> {
        val tracks = mutableListOf<RawYtTrack>()
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            extractMusicItems(root, tracks)
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return tracks
    }

    private fun extractMusicItems(element: JsonElement, result: MutableList<RawYtTrack>) {
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

    private fun parseItem(item: JsonObject): RawYtTrack? {
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
            if (flexColumns.size() > 1) {
                val col1Runs = flexColumns[1].asJsonObject
                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                    ?.getAsJsonArray("runs")

                if (col1Runs != null && col1Runs.size() > 0) {
                    val firstArtist = col1Runs.get(0).asJsonObject.get("text")?.asString
                    if (!firstArtist.isNullOrBlank()) {
                        artist = firstArtist
                    }
                }
            }

            var thumbUrl: String? = null
            val thumbnails = item.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")

            if (thumbnails != null && thumbnails.size() > 0) {
                thumbUrl = thumbnails.get(thumbnails.size() - 1).asJsonObject.get("url")?.asString
            }

            return RawYtTrack(
                videoId = videoId,
                title = title,
                artist = artist,
                albumArtUrl = thumbUrl
            )
        } catch (e: Exception) {
            return null
        }
    }
}
