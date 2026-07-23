package com.sameerasw.draft.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager

object DeviceUtils {

    fun isBlurProblematicDevice(): Boolean {
        // Samsung devices on One UI 7 (Android 15) or below have a broken blur implementation
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Build.VERSION.SDK_INT <= 35
    }

    fun isPowerSaveMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }
}
