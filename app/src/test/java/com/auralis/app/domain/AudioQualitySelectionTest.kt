package com.auralis.app.domain

import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.network.JioSaavnDecryptor
import com.auralis.app.network.YouTubeStreamResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualitySelectionTest {

    private val formats = listOf(
        50_000L to "opus-low",
        130_000L to "opus-medium",
        256_000L to "aac-high"
    )

    @Test
    fun highPicksTheBestAvailable() {
        assertEquals("aac-high", YouTubeStreamResolver.pickFormat(formats, AudioQualitySetting.HIGH))
    }

    @Test
    fun standardStaysUnderItsCeiling() {
        assertEquals("opus-medium", YouTubeStreamResolver.pickFormat(formats, AudioQualitySetting.STANDARD))
    }

    @Test
    fun dataSaverStaysUnderItsCeiling() {
        assertEquals("opus-low", YouTubeStreamResolver.pickFormat(formats, AudioQualitySetting.DATA_SAVER))
    }

    @Test
    fun fallsBackToTheLowestWhenNothingFitsTheCeiling() {
        val onlyHigh = listOf(320_000L to "only-high")
        assertEquals("only-high", YouTubeStreamResolver.pickFormat(onlyHigh, AudioQualitySetting.DATA_SAVER))
    }

    @Test
    fun noFormatsYieldsNull() {
        assertNull(YouTubeStreamResolver.pickFormat(emptyList(), AudioQualitySetting.HIGH))
    }

    @Test
    fun jioSaavnUrlSwitchesBitrateVariant() {
        val url = "https://aac.saavncdn.com/077/abc_320.mp4"
        assertEquals(url, JioSaavnDecryptor.withQuality(url, AudioQualitySetting.HIGH))
        assertEquals(
            "https://aac.saavncdn.com/077/abc_160.mp4",
            JioSaavnDecryptor.withQuality(url, AudioQualitySetting.STANDARD)
        )
        assertEquals(
            "https://aac.saavncdn.com/077/abc_96.mp4",
            JioSaavnDecryptor.withQuality(url, AudioQualitySetting.DATA_SAVER)
        )
    }

    @Test
    fun jioSaavnQualitySwitchLeavesOtherUrlsAlone() {
        val url = "https://aac.saavncdn.com/077/abc.mp4"
        assertEquals(url, JioSaavnDecryptor.withQuality(url, AudioQualitySetting.DATA_SAVER))
    }
}
