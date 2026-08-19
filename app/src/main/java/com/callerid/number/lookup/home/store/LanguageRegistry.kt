package com.callerid.number.lookup.home.store

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.kit.AppPrefs

object LanguageRegistry {

    private const val TAG = "LanguageRegistry"

    fun apply(languageTag: String) {
        try {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageTag)
            )
        } catch (e: IllegalStateException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "locale '$languageTag' refused: ${e.message}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "locale '$languageTag' failed: ${e.message}")
        }
    }

    fun applySaved(context: Context) {
        val savedLang = AppPrefs.selectedLanguage(context)
        if (savedLang.isEmpty()) return

        val normalizedLang = if (savedLang == "in") "id" else savedLang
        val current = context.resources.configuration.locales[0].language
        if (normalizedLang == current) return

        apply(normalizedLang)
    }

}
