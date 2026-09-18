package com.auralis.app.playback

import android.content.Context
import android.content.SharedPreferences
import com.auralis.app.domain.model.AudioQualitySetting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class LyricsProvider(val displayName: String) {
    AUTO("Automatic (Best Available)"),
    LRCLIB("LRCLIB (Synchronized LRC)"),
    LYRICS_OVH("Lyrics.ovh (Free Uncensored)"),
    NETEASE("NetEase Music")
}

enum class LyricsFontSize(val label: String, val sizeSp: Int) {
    COMPACT("Compact", 14),
    STANDARD("Romantic Standard", 18),
    LARGE("Large Karaoke", 23)
}

enum class HapticIntensity(val label: String) {
    CRISP("Crisp Luxury"),
    SUBTLE("Soft Romantic"),
    OFF("Disabled")
}

@Singleton
class AuralisSettingsPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auralis_settings_preferences", Context.MODE_PRIVATE)

    // Lyrics StateFlows
    private val _lyricsProvider = MutableStateFlow(readLyricsProvider())
    val lyricsProvider: StateFlow<LyricsProvider> = _lyricsProvider.asStateFlow()

    private val _lyricsAutoScroll = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_AUTOSCROLL, true))
    val lyricsAutoScroll: StateFlow<Boolean> = _lyricsAutoScroll.asStateFlow()

    private val _lyricsFontSize = MutableStateFlow(readLyricsFontSize())
    val lyricsFontSize: StateFlow<LyricsFontSize> = _lyricsFontSize.asStateFlow()

    private val _offlineLyricsEnabled = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_LYRICS, true))
    val offlineLyricsEnabled: StateFlow<Boolean> = _offlineLyricsEnabled.asStateFlow()

    // Playback Engine StateFlows
    private val _infiniteRadioAutoplay = MutableStateFlow(prefs.getBoolean(KEY_INFINITE_RADIO, true))
    val infiniteRadioAutoplay: StateFlow<Boolean> = _infiniteRadioAutoplay.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(prefs.getBoolean(KEY_SPONSOR_BLOCK, true))
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _audioQuality = MutableStateFlow(readAudioQuality())
    val audioQuality: StateFlow<AudioQualitySetting> = _audioQuality.asStateFlow()

    private val _hapticIntensity = MutableStateFlow(readHapticIntensity())
    val hapticIntensity: StateFlow<HapticIntensity> = _hapticIntensity.asStateFlow()

    // Lyrics Adjustments
    fun setLyricsProvider(provider: LyricsProvider) {
        prefs.edit().putString(KEY_LYRICS_PROVIDER, provider.name).apply()
        _lyricsProvider.value = provider
    }

    fun setLyricsAutoScroll(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LYRICS_AUTOSCROLL, enabled).apply()
        _lyricsAutoScroll.value = enabled
    }

    fun setLyricsFontSize(size: LyricsFontSize) {
        prefs.edit().putString(KEY_LYRICS_FONT_SIZE, size.name).apply()
        _lyricsFontSize.value = size
    }

    fun setOfflineLyricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_LYRICS, enabled).apply()
        _offlineLyricsEnabled.value = enabled
    }

    // Playback Adjustments
    fun setInfiniteRadioAutoplay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INFINITE_RADIO, enabled).apply()
        _infiniteRadioAutoplay.value = enabled
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSOR_BLOCK, enabled).apply()
        _sponsorBlockEnabled.value = enabled
    }

    fun setAudioQuality(quality: AudioQualitySetting) {
        prefs.edit().putString(KEY_AUDIO_QUALITY, quality.name).apply()
        _audioQuality.value = quality
    }

    fun setHapticIntensity(intensity: HapticIntensity) {
        prefs.edit().putString(KEY_HAPTIC_INTENSITY, intensity.name).apply()
        _hapticIntensity.value = intensity
    }

    // Readers
    private fun readLyricsProvider(): LyricsProvider {
        val name = prefs.getString(KEY_LYRICS_PROVIDER, LyricsProvider.AUTO.name)
        return try { LyricsProvider.valueOf(name ?: LyricsProvider.AUTO.name) } catch (e: Exception) { LyricsProvider.AUTO }
    }

    private fun readLyricsFontSize(): LyricsFontSize {
        val name = prefs.getString(KEY_LYRICS_FONT_SIZE, LyricsFontSize.STANDARD.name)
        return try { LyricsFontSize.valueOf(name ?: LyricsFontSize.STANDARD.name) } catch (e: Exception) { LyricsFontSize.STANDARD }
    }

    private fun readAudioQuality(): AudioQualitySetting {
        val name = prefs.getString(KEY_AUDIO_QUALITY, AudioQualitySetting.HIGH.name)
        return try { AudioQualitySetting.valueOf(name ?: AudioQualitySetting.HIGH.name) } catch (e: Exception) { AudioQualitySetting.HIGH }
    }

    private fun readHapticIntensity(): HapticIntensity {
        val name = prefs.getString(KEY_HAPTIC_INTENSITY, HapticIntensity.CRISP.name)
        return try { HapticIntensity.valueOf(name ?: HapticIntensity.CRISP.name) } catch (e: Exception) { HapticIntensity.CRISP }
    }

    companion object {
        private const val KEY_LYRICS_PROVIDER = "settings_lyrics_provider"
        private const val KEY_LYRICS_AUTOSCROLL = "settings_lyrics_autoscroll"
        private const val KEY_LYRICS_FONT_SIZE = "settings_lyrics_font_size"
        private const val KEY_OFFLINE_LYRICS = "settings_offline_lyrics"

        private const val KEY_INFINITE_RADIO = "settings_infinite_radio"
        private const val KEY_SPONSOR_BLOCK = "settings_sponsor_block"
        private const val KEY_AUDIO_QUALITY = "settings_audio_quality"
        private const val KEY_HAPTIC_INTENSITY = "settings_haptic_intensity"
    }
}
