package com.callerid.number.lookup.home.screen.reveal

import android.content.Context
import com.callerid.number.lookup.home.store.StorageRegistry

object RevealPolicy {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun shouldShow(context: Context, key: String, config: RevealSpec): Boolean {
        if (!config.enabled) return false
        val prefs = StorageRegistry(context)
        return when (config.frequency) {
            RevealCadence.NEVER -> false
            RevealCadence.ALWAYS -> true
            RevealCadence.ONCE -> prefs.introShownCount(key) == 0
            RevealCadence.EVERY_DAYS -> {
                val last = prefs.introLastShownMs(key)
                if (last == 0L) true
                else (System.currentTimeMillis() - last) / DAY_MS >= config.interval
            }

            RevealCadence.APP_LAUNCHES ->
                config.interval > 0 && prefs.appLaunchCount % config.interval == 0
        }
    }

    fun markShown(context: Context, key: String) {
        val prefs = StorageRegistry(context)
        val session = prefs.appLaunchCount
        if (prefs.introLastShownSession(key) == session) return
        prefs.recordIntroShown(key, session)
    }

    fun shouldShowLanguage(context: Context): Boolean =
        shouldShow(context, RevealConfig.LANGUAGE, RevealConfig.language(context))

    fun shouldShowTerms(context: Context): Boolean =
        shouldShow(context, RevealConfig.TERMS, RevealConfig.terms(context))

    fun shouldShowOnboarding(context: Context): Boolean =
        shouldShow(context, RevealConfig.ONBOARDING, RevealConfig.onboarding(context))

    fun shouldShowPermissionSheet(context: Context): Boolean =
        shouldShow(context, RevealConfig.PERMISSION_SHEET, RevealConfig.permissionSheet(context))
}
