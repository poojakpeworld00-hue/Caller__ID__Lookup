package com.callerid.number.lookup.home.screen.reveal

import android.content.Context
import com.callerid.number.lookup.home.store.StorageRegistry

/**
 * Decides whether an intro screen (Language / Onboarding) shows this launch, from
 * its [RevealSpec] Remote Config policy plus the persisted ledger in
 * [StorageRegistry]. A screen calls [markShown] when it actually appears.
 *
 * Session = one cold start: [StorageRegistry.appLaunchCount] is bumped once per launch
 * (in Splash), so `app_launches` counts launches and [markShown] can de-dupe within
 * a single launch even if the gate is evaluated at several points in the funnel.
 */
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
            // Show on every Nth launch (interval must be positive to be meaningful).
            RevealCadence.APP_LAUNCHES ->
                config.interval > 0 && prefs.appLaunchCount % config.interval == 0
        }
    }

    /** Record that [key] was shown this launch (bumps count + stamps time, once per launch). */
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

    /** Frequency gate only — callers still AND this with their own "is anything pending?" check. */
    fun shouldShowPermissionSheet(context: Context): Boolean =
        shouldShow(context, RevealConfig.PERMISSION_SHEET, RevealConfig.permissionSheet(context))
}
