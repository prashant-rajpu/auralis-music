package com.auralis.app.data.source

import android.util.Log
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.MusicSource
import javax.inject.Inject
import javax.inject.Singleton
import com.auralis.app.data.remote.jiosaavn.JioSaavnApi
import com.auralis.app.data.remote.jiosaavn.JioSaavnDecryptor
import com.auralis.app.data.remote.jiosaavn.JioSaavnSongDto

@Singleton
class JioSaavnSource @Inject constructor(
    private val api: JioSaavnApi
) : MusicSource {
    override val provider = Provider.JIOSAAVN
    override val priority = 10

    override suspend fun search(query: String): List<Track> =
        api.searchSongs(query).results.orEmpty().mapNotNull { it.toTrack() }

    override suspend fun trending(): List<Track> {
        val list = mutableListOf<Track>()
        try {
            api.getTrendingPlaylist().list?.mapNotNull { it.toTrack() }?.let { list.addAll(it) }
        } catch (e: Exception) {
            Log.w("JioSaavnSource", "Trending playlist failed", e)
        }
        if (list.size < 10) {
            try {
                val topHits = api.searchSongs("Top Hits", count = 25).results.orEmpty().mapNotNull { it.toTrack() }
                for (song in topHits) {
                    if (list.none { it.id == song.id }) list.add(song)
                }
            } catch (e: Exception) {
                Log.w("JioSaavnSource", "Top Hits fallback failed", e)
            }
        }
        return list
    }

    override suspend fun related(seed: Track): List<Track> =
        search(seed.artist).filter { it.id != seed.id }

    private fun JioSaavnSongDto.toTrack(): Track? {
        val mediaUrl = JioSaavnDecryptor.decryptMediaUrl(moreInfo?.encryptedMediaUrl)
        if (mediaUrl.isNullOrBlank()) return null

        val durationSec = moreInfo?.duration?.toLongOrNull() ?: 180L
        val artistName = moreInfo?.artistMap?.primaryArtists?.firstOrNull()?.name
            ?: subtitle
            ?: "Artist"

        return Track(
            id = "auralis_${id ?: title.hashCode()}",
            title = title?.unescape() ?: "Unknown",
            artist = artistName.unescape(),
            albumArtUrl = image?.replace("150x150", "500x500") ?: image,
            mediaUrl = mediaUrl,
            durationMs = durationSec * 1000L,
            source = provider.displayName,
            qualityBadge = "320 kbps AAC"
        )
    }

    private fun String.unescape(): String = replace("&quot;", "\"").replace("&#039;", "'")
}
