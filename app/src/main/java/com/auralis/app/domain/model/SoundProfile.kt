package com.auralis.app.domain.model

enum class SoundPreset(val displayName: String) {
    FLAT("Flat / Reference"),
    BASS_HEAVY("Bass Heavy"),
    VOCAL_CLARITY("Vocal Clarity"),
    EDM_CLUB("EDM / Club"),
    ACOUSTIC_WARM("Acoustic Warm")
}

data class SoundProfile(
    val bassBoostStrength: Int = 0, // 0 to 1000
    val trebleBoostStrength: Int = 0, // 0 to 1000
    val preset: SoundPreset = SoundPreset.FLAT,
    val crossfadeDurationSec: Int = 5 // 1 to 12 seconds
)
