package com.auralis.app.domain.source

import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.domain.model.Track

/** Turns a track into a URL ExoPlayer can open. Implementations are contributed per build flavor. */
interface StreamResolver {
    /** Lower is consulted first. */
    val priority: Int

    /** A last-resort resolver that finds the song on another provider. */
    val isFallback: Boolean get() = false

    fun supports(track: Track): Boolean

    /** A playable URL, or null when this resolver cannot produce one. */
    suspend fun resolve(track: Track, quality: AudioQualitySetting, forceRefresh: Boolean): String?
}
