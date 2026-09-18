package com.auralis.app.domain.model

enum class AudioQualitySetting(val label: String, val bitrateKbps: Int) {
    HIGH("Best available", 320),
    STANDARD("Standard (~160 kbps)", 160),
    DATA_SAVER("Data saver (~96 kbps)", 96)
}
