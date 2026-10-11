package com.windowsphonekeyboard.ime.haptics

import android.view.HapticFeedbackConstants
import android.view.View

class HapticFeedback {
    fun play(view: View, enabled: Boolean) {
        if (enabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
