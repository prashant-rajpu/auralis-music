package com.auralis.app.data.source

import android.util.Log
import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.MusicSource
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.network.JamProtocolHelper
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the same song on another catalog when a track cannot be played as-is: imported playlist
 * entries, and tracks shared by a peer whose provider this build does not have.
 */
@Singleton
class SearchFallbackResolver @Inject constructor(
    sources: Set<@JvmSuppressWildcards MusicSource>,
    private val registry: Lazy<StreamResolverRegistry>
) : StreamResolver {
    private val candidates = sources.filter { it.isEnabled }.sortedBy { it.priority }

    override val priority = 100
    override val isFallback = true

    override fun supports(track: Track): Boolean = !JamProtocolHelper.isLocalFileUrl(track.mediaUrl)

    override suspend fun resolve(track: Track, quality: AudioQualitySetting, forceRefresh: Boolean): String? {
        val query = JamProtocolHelper.cleanSearchQuery(track.title, track.artist)
        if (query.isBlank()) return null

        for (source in candidates) {
            if (source.provider == track.provider) continue
            val match = try {
                source.search(query).firstOrNull()
            } catch (e: Exception) {
                Log.w("SearchFallbackResolver", "${source.provider} search failed for $query", e)
                null
            } ?: continue
            val url = registry.get().resolve(match, forceRefresh = forceRefresh, allowFallback = false)
            if (url.isNotBlank()) {
                Log.d("SearchFallbackResolver", "Resolved '${track.title}' via ${source.provider}")
                return url
            }
        }
        return null
    }
}
