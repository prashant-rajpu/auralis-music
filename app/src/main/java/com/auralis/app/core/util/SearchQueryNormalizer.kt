package com.auralis.app.core.util

/**
 * Turns a title and artist into something a catalog search can actually match.
 *
 * Sources label the same song wildly differently — "Song (Official Video) [4K Remastered]" on one,
 * "Song" on another — so a guest resolving a peer's track against its own catalog needs the noise
 * stripped first. Lives here rather than in a transport class because both the old ntfy path and
 * the relay path need it, and it is pure.
 */
object SearchQueryNormalizer {

    private val TITLE_NOISE_PARENS =
        Regex("(?i)\\(.*?(official|feat|ft|video|audio|lyrics|remix|hd|4k).*?\\)")
    private val TITLE_NOISE_BRACKETS =
        Regex("(?i)\\[.*?(official|feat|ft|video|audio|lyrics|remix|hd|4k).*?\\]")
    private val TITLE_NOISE_WORDS =
        Regex("(?i)(official\\s+video|official\\s+audio|lyric\\s+video|visualizer|remastered|video)")
    private val ARTIST_FEATURES = Regex("(?i)\\b(ft\\.?|feat\\.?|featuring)\\b.*")

    fun normalize(title: String, artist: String): String {
        val cleanTitle = title
            .replace(TITLE_NOISE_PARENS, "")
            .replace(TITLE_NOISE_BRACKETS, "")
            .replace(TITLE_NOISE_WORDS, "")
            .trim()
        val cleanArtist = artist.replace(ARTIST_FEATURES, "").trim()
        return "$cleanTitle $cleanArtist".trim()
    }
}
