package com.callerid.number.lookup.home.kit

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.onboard.OnboardRouter
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Single entry point for Firebase Analytics events (closed list: `docs/analytics-events.md`).
 * Until the first run completes every name gets a `first_` prefix, applied here only.
 */
object Analytics {

    private const val TAG = "Analytics"
    private const val KEY_FIRST_DONE = "analytics_first_session_done"
    private const val MAX_NAME = 40

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    @Volatile
    private var isFirstSession = false

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val p = app.getSharedPreferences("analytics_prefs", Context.MODE_PRIVATE)
        prefs = p
        isFirstSession = !p.getBoolean(KEY_FIRST_DONE, false) &&
            !runCatching { OnboardRouter.wasOnboardingCompleted(app) }.getOrDefault(false)
        if (!isFirstSession) p.edit().putBoolean(KEY_FIRST_DONE, true).apply()
    }

    fun endFirstSession() {
        if (!isFirstSession) return
        prefs?.edit()?.putBoolean(KEY_FIRST_DONE, true)?.apply()
        isFirstSession = false
    }

    fun log(event: String, vararg params: Pair<String, String>) {
        val ctx = appContext ?: return
        val name = (if (isFirstSession) "first_$event" else event).take(MAX_NAME)
        runCatching {
            val bundle = if (params.isEmpty()) null else Bundle().apply {
                params.forEach { (k, v) -> putString(k.take(40), v.take(100)) }
            }
            FirebaseAnalytics.getInstance(ctx).logEvent(name, bundle)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "$name ${params.joinToString { "${it.first}=${it.second}" }}")
    }

    fun screen(token: String) = log("${token}_open")

    /** `ad_{type}_{phase}` (`show` / `close` / `failed`) with the placement in `screen`. */
    fun adEvent(type: String, phase: String, place: String) =
        log("ad_${type}_$phase", "screen" to place)
}
