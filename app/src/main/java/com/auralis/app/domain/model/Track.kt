package com.auralis.app.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val mediaUrl: String,
    val durationMs: Long,
    val source: String = "Auralis",
    val qualityBadge: String = "HQ Audio",
    val isDownloaded: Boolean = false,
    val lyrics: List<LyricLine>? = null,
    val isAutoplayRecommendation: Boolean = false
) {
    /**
     * Derived rather than stored: a data class `copy()` would keep a stale value, and the id
     * prefix is what every source already encodes. Phase 3 replaces the prefixes with real ids.
     */
    val provider: Provider
        get() = Provider.infer(id, mediaUrl)

    /** The id inside the provider's own catalog (YouTube video id, JioSaavn song id, ...). */
    val providerId: String
        get() = when (provider) {
            Provider.YOUTUBE -> getYouTubeVideoId() ?: id.removePrefix("yt_")
            Provider.AUDIUS -> id.removePrefix("auralis_global_")
            Provider.JIOSAAVN -> id.removePrefix("auralis_")
            Provider.JAMENDO -> id.removePrefix("jamendo_")
            Provider.LOCAL -> id.removePrefix("local_")
            Provider.IMPORTED -> id
        }

    fun isYouTubeTrack(): Boolean =
        provider == Provider.YOUTUBE ||
        mediaUrl.contains("youtube.com") ||
        mediaUrl.contains("youtu.be") ||
        mediaUrl.contains("googlevideo.com") ||
        mediaUrl.contains("piped")

    fun getYouTubeVideoId(): String? {
        if (id.startsWith("yt_")) {
            val vid = id.removePrefix("yt_")
            if (vid.length == 11) return vid
        }
        val match = Regex("""(?:v=|/|vi/|youtu\.be/)([0-9A-Za-z_-]{11})""").find(mediaUrl)
        return match?.groupValues?.get(1)
    }
}
