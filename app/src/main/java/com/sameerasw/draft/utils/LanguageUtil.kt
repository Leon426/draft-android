package com.sameerasw.draft.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

data class LanguageOption(
    val code: String,
    val title: String,
    val subtitle: String
)

object LanguageUtil {

    val supportedLanguages = listOf(
        LanguageOption(code = "", title = "Follow System", subtitle = "跟随系统 / デフォルト"),
        LanguageOption(code = "zh-CN", title = "简体中文", subtitle = "Simplified Chinese"),
        LanguageOption(code = "zh-TW", title = "繁體中文", subtitle = "Traditional Chinese"),
        LanguageOption(code = "en", title = "English", subtitle = "English (United States)"),
        LanguageOption(code = "ja", title = "日本語", subtitle = "Japanese")
    )

    fun setLanguage(languageCode: String) {
        val localeList = if (languageCode.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getCurrentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        val first = locales[0] ?: return ""
        return first.toLanguageTag()
    }

    fun getCurrentLanguageDisplayName(): String {
        val code = getCurrentLanguageCode()
        val match = supportedLanguages.firstOrNull {
            it.code.equals(code, ignoreCase = true) ||
            (it.code.isNotBlank() && code.startsWith(it.code, ignoreCase = true))
        }
        return match?.title ?: if (code.isBlank()) "Follow System" else code
    }

    fun openSystemLocaleSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}