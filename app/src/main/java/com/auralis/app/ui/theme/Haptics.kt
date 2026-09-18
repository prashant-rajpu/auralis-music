package com.auralis.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.auralis.app.playback.HapticIntensity

val LocalHapticIntensity = compositionLocalOf { HapticIntensity.CRISP }

val HapticIntensity.feedbackType: HapticFeedbackType?
    get() = when (this) {
        HapticIntensity.CRISP -> HapticFeedbackType.VirtualKey
        HapticIntensity.SUBTLE -> HapticFeedbackType.TextHandleMove
        HapticIntensity.OFF -> null
    }
