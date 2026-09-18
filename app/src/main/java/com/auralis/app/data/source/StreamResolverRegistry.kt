package com.auralis.app.data.source

import android.util.Log
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.playback.AuralisSettingsPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolverRegistry @Inject constructor(
    resolvers: Set<@JvmSuppressWildcards StreamResolver>,
    private val settings: AuralisSettingsPreferences
) {
    private val ordered = resolvers.sortedBy { it.priority }

    /** A URL ExoPlayer can open, or "" when no resolver in this build can play the track. */
    suspend fun resolve(track: Track, forceRefresh: Boolean = false, allowFallback: Boolean = true): String {
        val quality = settings.audioQuality.value
        for (resolver in ordered) {
            if (resolver.isFallback && !allowFallback) continue
            if (!resolver.supports(track)) continue
            val url = try {
                resolver.resolve(track, quality, forceRefresh)
            } catch (e: Exception) {
                Log.w("StreamResolverRegistry", "${resolver::class.simpleName} failed for ${track.title}", e)
                null
            }
            if (!url.isNullOrBlank()) return url
        }
        Log.e("StreamResolverRegistry", "No resolver could play ${track.title} (${track.provider})")
        return ""
    }
}
