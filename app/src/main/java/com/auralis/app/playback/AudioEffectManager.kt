package com.auralis.app.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import com.auralis.app.domain.model.SoundPreset
import com.auralis.app.domain.model.SoundProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEffectManager @Inject constructor(
    private val preferences: SoundProfilePreferences
) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = -1

    var currentProfile: SoundProfile = preferences.getSoundProfile()
        private set

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId

        release()

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Failed to initialize Equalizer", e)
            equalizer = null
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Failed to initialize BassBoost", e)
            bassBoost = null
        }

        applyProfile(currentProfile)
    }

    fun applyProfile(profile: SoundProfile) {
        currentProfile = profile
        preferences.saveSoundProfile(profile)

        // 1. Apply Bass Boost
        try {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    val strength = profile.bassBoostStrength.coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                    bb.enabled = strength > 0
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Error setting bass boost", e)
        }

        // 2. Apply Equalizer preset curve & Treble boost
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands
            if (numBands <= 0) return

            val minBandLevel = eq.bandLevelRange[0]
            val maxBandLevel = eq.bandLevelRange[1]

            // Reset bands to neutral
            for (band in 0 until numBands) {
                eq.setBandLevel(band.toShort(), 0)
            }

            // Apply selected preset
            when (profile.preset) {
                SoundPreset.FLAT -> {
                    // Flat curve
                }
                SoundPreset.BASS_HEAVY -> {
                    if (numBands > 0) eq.setBandLevel(0, 500.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    if (numBands > 1) eq.setBandLevel(1, 400.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                }
                SoundPreset.VOCAL_CLARITY -> {
                    if (numBands > 0) eq.setBandLevel(0, (-200).coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    val midBand = (numBands / 2).toShort()
                    eq.setBandLevel(midBand, 400.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    if (midBand + 1 < numBands) {
                        eq.setBandLevel((midBand + 1).toShort(), 300.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    }
                }
                SoundPreset.EDM_CLUB -> {
                    if (numBands > 0) eq.setBandLevel(0, 500.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    if (numBands > 1) eq.setBandLevel(1, 300.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    val midBand = (numBands / 2).toShort()
                    eq.setBandLevel(midBand, (-150).coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    val topBand = (numBands - 1).toShort()
                    eq.setBandLevel(topBand, 450.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                }
                SoundPreset.ACOUSTIC_WARM -> {
                    if (numBands > 1) eq.setBandLevel(1, 250.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                    val topBand = (numBands - 1).toShort()
                    eq.setBandLevel(topBand, 200.coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort())
                }
            }

            // Apply Treble boost to top band
            if (profile.trebleBoostStrength > 0 && numBands > 0) {
                val topBand = (numBands - 1).toShort()
                val existingLevel = eq.getBandLevel(topBand)
                val boostMb = (profile.trebleBoostStrength * 600 / 1000)
                val targetLevel = (existingLevel + boostMb).coerceIn(minBandLevel.toInt(), maxBandLevel.toInt()).toShort()
                eq.setBandLevel(topBand, targetLevel)
            }
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Error applying equalizer bands", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            equalizer = null
        } catch (e: Exception) {
            // Ignore release error
        }

        try {
            bassBoost?.release()
            bassBoost = null
        } catch (e: Exception) {
            // Ignore release error
        }
    }
}
