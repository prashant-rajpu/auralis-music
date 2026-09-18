package com.auralis.app.data.source

import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.network.JamProtocolHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFileResolver @Inject constructor() : StreamResolver {
    override val priority = -100

    override fun supports(track: Track): Boolean = JamProtocolHelper.isLocalFileUrl(track.mediaUrl)

    override suspend fun resolve(track: Track, quality: AudioQualitySetting, forceRefresh: Boolean): String =
        track.mediaUrl
}
