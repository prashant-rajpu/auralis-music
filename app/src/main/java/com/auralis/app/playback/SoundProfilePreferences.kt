package com.auralis.app.playback

import android.content.Context
import android.content.SharedPreferences
import com.auralis.app.domain.model.SoundPreset
import com.auralis.app.domain.model.SoundProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundProfilePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auralis_audio_fx_prefs", Context.MODE_PRIVATE)

    fun getSoundProfile(): SoundProfile {
        val bass = prefs.getInt(KEY_BASS, 0)
        val treble = prefs.getInt(KEY_TREBLE, 0)
        val presetName = prefs.getString(KEY_PRESET, SoundPreset.FLAT.name) ?: SoundPreset.FLAT.name
        val crossfade = prefs.getInt(KEY_CROSSFADE, 5)

        val preset = try {
            SoundPreset.valueOf(presetName)
        } catch (e: Exception) {
            SoundPreset.FLAT
        }

        return SoundProfile(
            bassBoostStrength = bass,
            trebleBoostStrength = treble,
            preset = preset,
            crossfadeDurationSec = crossfade
        )
    }

    fun saveSoundProfile(profile: SoundProfile) {
        prefs.edit()
            .putInt(KEY_BASS, profile.bassBoostStrength)
            .putInt(KEY_TREBLE, profile.trebleBoostStrength)
            .putString(KEY_PRESET, profile.preset.name)
            .putInt(KEY_CROSSFADE, profile.crossfadeDurationSec)
            .apply()
    }

    companion object {
        private const val KEY_BASS = "bass_boost"
        private const val KEY_TREBLE = "treble_boost"
        private const val KEY_PRESET = "sound_preset"
        private const val KEY_CROSSFADE = "crossfade_sec"
    }
}
