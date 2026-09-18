package com.auralis.app.data.source

import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import com.auralis.app.domain.source.StreamResolver
import com.auralis.app.network.JamProtocolHelper
import javax.inject.Inject
import javax.inject.Singleton
import com.auralis.app.data.remote.jiosaavn.JioSaavnDecryptor

@Singleton
class JioSaavnStreamResolver @Inject constructor() : StreamResolver {
    override val priority = 10

    override fun supports(track: Track): Boolean =
        track.provider == Provider.JIOSAAVN && JamProtocolHelper.isPlayableDirectStreamUrl(track.mediaUrl)

    override suspend fun resolve(track: Track, quality: AudioQualitySetting, forceRefresh: Boolean): String =
        JioSaavnDecryptor.withQuality(track.mediaUrl, quality)
}
