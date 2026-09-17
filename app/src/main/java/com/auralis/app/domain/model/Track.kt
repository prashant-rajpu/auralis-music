package com.auralis.app.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val mediaUrl: String,
    val durationMs: Long,
    val source: String = "JioSaavn",
    val qualityBadge: String = "320 kbps",
    val isDownloaded: Boolean = false,
    val lyrics: List<LyricLine>? = null
)
