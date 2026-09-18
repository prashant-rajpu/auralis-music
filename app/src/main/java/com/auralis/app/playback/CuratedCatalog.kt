package com.auralis.app.playback

import com.auralis.app.domain.model.Track

/**
 * Curated catalog providing immediate starter tracks on app launch or offline fallback.
 * Ensures the app never opens to a blank or error screen on any device.
 */
object CuratedCatalog {

    fun getStarterTracks(): List<Track> {
        return listOf(
            Track(
                id = "yt_b1kbLwvqugk",
                title = "Starboy",
                artist = "The Weeknd ft. Daft Punk",
                albumArtUrl = "https://i.ytimg.com/vi/b1kbLwvqugk/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=b1kbLwvqugk",
                durationMs = 230000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            ),
            Track(
                id = "yt_4NRXx6U8ABQ",
                title = "Blinding Lights",
                artist = "The Weeknd",
                albumArtUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=4NRXx6U8ABQ",
                durationMs = 200000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            ),
            Track(
                id = "yt_JGwWNGJdvx8",
                title = "Shape of You",
                artist = "Ed Sheeran",
                albumArtUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=JGwWNGJdvx8",
                durationMs = 233000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            ),
            Track(
                id = "yt_BddP6PYo2gs",
                title = "Kesariya",
                artist = "Arijit Singh, Pritam",
                albumArtUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=BddP6PYo2gs",
                durationMs = 268000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            ),
            Track(
                id = "yt_GxldQ9GyXfY",
                title = "Until I Found You",
                artist = "Stephen Sanchez",
                albumArtUrl = "https://i.ytimg.com/vi/GxldQ9GyXfY/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=GxldQ9GyXfY",
                durationMs = 177000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            ),
            Track(
                id = "yt_syFZfO_wfMQ",
                title = "Night Changes",
                artist = "One Direction",
                albumArtUrl = "https://i.ytimg.com/vi/syFZfO_wfMQ/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=syFZfO_wfMQ",
                durationMs = 226000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            ),
            Track(
                id = "yt_TUVcZfQe-Kw",
                title = "Levitating",
                artist = "Dua Lipa",
                albumArtUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=TUVcZfQe-Kw",
                durationMs = 203000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            ),
            Track(
                id = "yt_ha_X2bE3oEQ",
                title = "Golden Hour",
                artist = "JVKE",
                albumArtUrl = "https://i.ytimg.com/vi/ha_X2bE3oEQ/hqdefault.jpg",
                mediaUrl = "https://music.youtube.com/watch?v=ha_X2bE3oEQ",
                durationMs = 209000L,
                source = "Auralis Master",
                qualityBadge = "320 kbps Lossless",
                isAutoplayRecommendation = false
            )
        )
    }
}
