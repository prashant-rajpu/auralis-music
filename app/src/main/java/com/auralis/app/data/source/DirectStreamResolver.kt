package com.auralis.app.data.source

import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.network.JamProtocolHelper
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks whose mediaUrl already is a stream on a known CDN (Audius, Jamendo) need no resolution. */
@Singleton
class DirectStreamResolver @Inject constructor() : StreamResolver {
    override val priority = 50

    override fun supports(track: Track): Boolean =
        !track.isYouTubeTrack() && JamProtocolHelper.isPlayableDirectStreamUrl(track.mediaUrl)

    override suspend fun resolve(track: Track, quality: AudioQualitySetting, forceRefresh: Boolean): String =
        track.mediaUrl
}
