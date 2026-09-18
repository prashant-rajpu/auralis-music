package com.auralis.app.domain

import com.auralis.app.domain.model.Provider
import com.auralis.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderInferenceTest {

    private fun track(id: String, mediaUrl: String = "https://example.com/a.mp3") = Track(
        id = id,
        title = "Title",
        artist = "Artist",
        albumArtUrl = null,
        mediaUrl = mediaUrl,
        durationMs = 1000L
    )

    @Test
    fun infersProviderFromIdPrefix() {
        assertEquals(Provider.YOUTUBE, track("yt_abcdefghijk").provider)
        assertEquals(Provider.JIOSAAVN, track("auralis_12345").provider)
        assertEquals(Provider.AUDIUS, track("auralis_global_xyz").provider)
        assertEquals(Provider.JAMENDO, track("jamendo_998").provider)
        assertEquals(Provider.IMPORTED, track("yt_import_5_9").provider)
        assertEquals(Provider.LOCAL, track("local_42").provider)
    }

    @Test
    fun audiusPrefixWinsOverJioSaavnPrefix() {
        // "auralis_global_" also starts with "auralis_", so ordering matters
        assertEquals(Provider.AUDIUS, track("auralis_global_1").provider)
    }

    @Test
    fun contentUriIsLocal() {
        assertEquals(Provider.LOCAL, track("42", "content://media/external/audio/media/42").provider)
    }

    @Test
    fun providerFollowsCopyInsteadOfGoingStale() {
        val youTube = track("yt_abcdefghijk")
        assertEquals(Provider.YOUTUBE, youTube.provider)

        // A stored provider would still say YOUTUBE here
        val saavn = youTube.copy(id = "auralis_1", mediaUrl = "https://aac.saavncdn.com/1/x_320.mp4")
        assertEquals(Provider.JIOSAAVN, saavn.provider)
    }

    @Test
    fun providerIdStripsThePrefix() {
        assertEquals("abcdefghijk", track("yt_abcdefghijk").providerId)
        assertEquals("xyz", track("auralis_global_xyz").providerId)
        assertEquals("12345", track("auralis_12345").providerId)
        assertEquals("998", track("jamendo_998").providerId)
    }
}
