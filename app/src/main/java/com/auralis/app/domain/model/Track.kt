package com.auralis.app.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val mediaUrl: String,
    val durationMs: Long,
    val source: String = "Auralis Master",
    val qualityBadge: String = "320 kbps Master",
    val isDownloaded: Boolean = false,
    val lyrics: List<LyricLine>? = null
) {
    fun isYouTubeTrack(): Boolean =
        id.startsWith("yt_") ||
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
