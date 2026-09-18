package com.auralis.app.network

import android.util.Log
import com.auralis.app.domain.model.ArtistProfile
import com.auralis.app.domain.model.ArtistRelease
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonArray
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

    companion object {
        private const val SONGS_SEARCH_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
        private const val ARTISTS_SEARCH_PARAMS = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
    }

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20240101.01.00",
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "query": ${gson.toJson(query)},
                "params": "$SONGS_SEARCH_PARAMS"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", "1.20240101.01.00")
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

    /**
     * Infinite Radio Autoplay:
     * Fetches related recommended tracks from YouTube Music's 'next' watch endpoint.
     */
    suspend fun getRelatedTracks(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_").trim()
        if (cleanId.isEmpty()) return@withContext emptyList()

        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20240101.01.00",
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "videoId": "$cleanId"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/next")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", "1.20240101.01.00")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            parseRelatedTracks(responseBody)
        } catch (e: Exception) {
            Log.e("YouTubeMusicApi", "getRelatedTracks failed for $cleanId", e)
            emptyList()
        }
    }

    private fun parseRelatedTracks(jsonString: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            extractPlaylistPanelVideos(root, tracks)
        } catch (e: Exception) {
            Log.w("YouTubeMusicApi", "Failed to parse related tracks", e)
        }
        return tracks
    }

    private fun extractPlaylistPanelVideos(element: JsonElement, result: MutableList<Track>) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("playlistPanelVideoRenderer")) {
                val item = obj.getAsJsonObject("playlistPanelVideoRenderer")
                parsePlaylistPanelVideo(item)?.let { result.add(it) }
                return
            }
            for (entry in obj.entrySet()) {
                extractPlaylistPanelVideos(entry.value, result)
            }
        } else if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                extractPlaylistPanelVideos(item, result)
            }
        }
    }

    private fun parsePlaylistPanelVideo(item: JsonObject): Track? {
        try {
            val videoId = item.get("videoId")?.asString ?: return null

            val titleRuns = item.getAsJsonObject("title")?.getAsJsonArray("runs")
            val title = titleRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: "Unknown Title"

            var artist = "Artist"
            val bylineRuns = item.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                ?: item.getAsJsonObject("shortBylineText")?.getAsJsonArray("runs")
            if (bylineRuns != null && bylineRuns.size() > 0) {
                artist = bylineRuns.get(0).asJsonObject.get("text")?.asString ?: "Artist"
            }

            var durationMs = 210000L
            val lenText = item.getAsJsonObject("lengthText")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
            if (lenText != null && lenText.matches(Regex("""\d+:\d+(?::\d+)?"""))) {
                val parts = lenText.split(":").mapNotNull { it.toLongOrNull() }
                if (parts.size == 2) {
                    durationMs = (parts[0] * 60 + parts[1]) * 1000L
                } else if (parts.size == 3) {
                    durationMs = (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
                }
            }

            var thumbUrl: String? = null
            val thumbnails = item.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
            if (thumbnails != null && thumbnails.size() > 0) {
                val rawUrl = thumbnails.get(thumbnails.size() - 1).asJsonObject.get("url")?.asString
                thumbUrl = rawUrl?.replace(Regex("""=w\d+-h\d+.*"""), "=w500-h500-l90-rj")
            }

            return Track(
                id = "yt_$videoId",
                title = title,
                artist = artist,
                albumArtUrl = thumbUrl ?: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
                mediaUrl = "https://www.youtube.com/watch?v=$videoId",
                durationMs = durationMs,
                source = Provider.YOUTUBE.displayName,
                qualityBadge = "Opus / AAC"
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Searches for Artist profile, bio, top songs and discography.
     */
    suspend fun getArtistProfile(artistName: String): ArtistProfile? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isEmpty()) return@withContext null

        try {
            // 1. Search for artist channel / browse ID
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240101.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": ${gson.toJson(cleanName)},
                    "params": "$ARTISTS_SEARCH_PARAMS"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-YouTube-Client-Name", "67")
                .addHeader("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val searchResp = client.newCall(request).execute()
            if (!searchResp.isSuccessful) return@withContext fallbackArtistProfile(cleanName)
            val searchJson = searchResp.body?.string() ?: return@withContext fallbackArtistProfile(cleanName)

            // Extract browse ID
            val browseId = extractArtistBrowseId(searchJson) ?: return@withContext fallbackArtistProfile(cleanName)

            // 2. Fetch full Artist Browse page
            val browseBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240101.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "browseId": "$browseId"
                }
            """.trimIndent()

            val browseReq = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-YouTube-Client-Name", "67")
                .addHeader("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(browseBody.toRequestBody("application/json".toMediaType()))
                .build()

            val browseResp = client.newCall(browseReq).execute()
            if (!browseResp.isSuccessful) return@withContext fallbackArtistProfile(cleanName)
            val browseJson = browseResp.body?.string() ?: return@withContext fallbackArtistProfile(cleanName)

            parseArtistBrowsePage(browseId, cleanName, browseJson)
        } catch (e: Exception) {
            Log.e("YouTubeMusicApi", "getArtistProfile failed for $cleanName", e)
            fallbackArtistProfile(cleanName)
        }
    }

    private fun extractArtistBrowseId(jsonString: String): String? {
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            val jsonStr = root.toString()
            val match = Regex(""""browseId":"(UC[a-zA-Z0-9_-]{22})"""").find(jsonStr)
            return match?.groupValues?.get(1)
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun fallbackArtistProfile(artistName: String): ArtistProfile {
        val tracks = searchTracks(artistName)
        val banner = tracks.firstOrNull()?.albumArtUrl
        return ArtistProfile(
            id = "artist_${artistName.hashCode()}",
            name = artistName,
            bannerUrl = banner,
            monthlyListeners = null,
            bio = "$artistName on Auralis Premium Music.",
            topSongs = tracks.take(15),
            releases = listOf(
                ArtistRelease(
                    browseId = "rel_1",
                    title = "Popular Releases",
                    year = "2024",
                    coverUrl = banner,
                    type = "Album"
                )
            )
        )
    }

    private fun parseArtistBrowsePage(id: String, name: String, jsonString: String): ArtistProfile {
        var bannerUrl: String? = null
        var monthlyListeners: String? = null
        var bio: String? = null
        val topSongs = mutableListOf<Track>()
        val releases = mutableListOf<ArtistRelease>()

        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)

            // Header Banner & Subscribers
            val header = root.getAsJsonObject("header")
                ?.getAsJsonObject("musicImmersiveHeaderRenderer")
                ?: root.getAsJsonObject("header")?.getAsJsonObject("musicVisualHeaderRenderer")

            if (header != null) {
                val thumbs = header.getAsJsonObject("thumbnail")
                    ?.getAsJsonObject("musicThumbnailRenderer")
                    ?.getAsJsonObject("thumbnail")
                    ?.getAsJsonArray("thumbnails")
                if (thumbs != null && thumbs.size() > 0) {
                    bannerUrl = thumbs.get(thumbs.size() - 1).asJsonObject.get("url")?.asString
                }

                val subs = header.getAsJsonObject("subscriptionButton")
                    ?.getAsJsonObject("subscribeButtonRenderer")
                    ?.getAsJsonObject("subscriberCountText")
                    ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                if (subs != null) {
                    monthlyListeners = "$subs listeners"
                }

                val desc = header.getAsJsonObject("description")?.getAsJsonArray("runs")
                    ?.get(0)?.asJsonObject?.get("text")?.asString
                if (desc != null) {
                    bio = desc
                }
            }

            // Extract Songs & Releases from Shelves
            extractMusicItems(root, topSongs)

            // Look for albums / singles
            val shelfMatches = Regex(""""title":\{"runs":\[\{"text":"([^"]+)"\}\]\}.*?"browseId":"(MPREb_[a-zA-Z0-9_-]+)"""")
                .findAll(jsonString)
            for (m in shelfMatches.take(10)) {
                val title = m.groupValues[1]
                val bId = m.groupValues[2]
                releases.add(
                    ArtistRelease(
                        browseId = bId,
                        title = title,
                        year = "2024",
                        coverUrl = bannerUrl,
                        type = "Album / Single"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicApi", "Error parsing artist browse page", e)
        }

        return ArtistProfile(
            id = id,
            name = name,
            bannerUrl = bannerUrl ?: topSongs.firstOrNull()?.albumArtUrl,
            monthlyListeners = monthlyListeners,
            bio = bio,
            topSongs = topSongs.distinctBy { it.id }.take(20),
            releases = releases.distinctBy { it.browseId }
        )
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
                ?: item.getAsJsonObject("doubleTapToLikeRenderer")?.getAsJsonObject("track")?.get("videoId")?.asString
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
                source = Provider.YOUTUBE.displayName,
                qualityBadge = "Opus / AAC"
            )
        } catch (e: Exception) {
            return null
        }
    }
}
