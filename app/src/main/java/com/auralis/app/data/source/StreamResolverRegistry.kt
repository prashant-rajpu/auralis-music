package com.auralis.app.data.source

import android.util.Log
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.network.JamProtocolHelper
import com.auralis.app.playback.AuralisSettingsPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolverRegistry @Inject constructor(
    resolvers: Set<@JvmSuppressWildcards StreamResolver>,
    private val settings: AuralisSettingsPreferences,
    private val cache: StreamUrlCache
) {
    private val ordered = resolvers.sortedBy { it.priority }

    /** A URL ExoPlayer can open, or "" when no resolver in this build can play the track. */
    suspend fun resolve(track: Track, forceRefresh: Boolean = false, allowFallback: Boolean = true): String {
        val quality = settings.audioQuality.value

        // A local file is its own URL; caching one would just be a second copy of the path.
        if (JamProtocolHelper.isLocalFileUrl(track.mediaUrl)) return track.mediaUrl

        if (forceRefresh) {
            cache.invalidate(track.id)
        } else {
            cache.get(track.id, quality)?.let { cached ->
                Log.d("StreamResolverRegistry", "Cache hit for ${track.title}")
                return cached
            }
        }

        for (resolver in ordered) {
            if (resolver.isFallback && !allowFallback) continue
            if (!resolver.supports(track)) continue
            val url = try {
                resolver.resolve(track, quality, forceRefresh)
            } catch (e: Exception) {
                Log.w("StreamResolverRegistry", "${resolver::class.simpleName} failed for ${track.title}", e)
                null
            }
            if (!url.isNullOrBlank()) {
                // Only worth storing when resolution did real work; a URL the track already
                // carried can be produced again for free.
                if (url != track.mediaUrl) cache.put(track.id, quality, url)
                return url
            }
        }
        Log.e("StreamResolverRegistry", "No resolver could play ${track.title} (${track.provider})")
        return ""
    }
}
