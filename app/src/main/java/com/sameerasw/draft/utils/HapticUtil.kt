package com.sameerasw.draft.utils

import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtil {

    fun performUIHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun performVirtualKeyHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
