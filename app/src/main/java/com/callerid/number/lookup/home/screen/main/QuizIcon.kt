package com.callerid.number.lookup.home.screen.main

import android.app.Activity
import android.content.Context
import android.util.Log
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.DirectLinkOpener
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.kit.Analytics
import org.json.JSONObject

/**
 * Header game-quiz icon: `"quiz_icon": { "enabled", "url", "open_in" }` (webview | custom_tab |
 * browser; blank = the global open type). Hidden unless enabled with a non-blank url.
 */
object QuizIcon {

    private const val TAG = "QuizIcon"
    const val CONFIG_KEY = "quiz_icon"

    private fun config(context: Context): JSONObject? {
        val raw = PromoVault.getInstance(context).getString(CONFIG_KEY)
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun url(context: Context): String? =
        config(context)?.takeIf { it.optBoolean("enabled", false) }
            ?.optString("url").orEmpty().trim().takeIf { it.isNotEmpty() }

    fun isVisible(context: Context): Boolean = url(context) != null

    fun open(activity: Activity) {
        val url = url(activity) ?: return
        val mode = DirectLinkOpener.modeOf(config(activity)?.optString("open_in"))
        Analytics.log("home_quiz_icon_click")
        val opened = DirectLinkOpener.open(activity, url, mode)
        if (BuildConfig.DEBUG) Log.d(TAG, "open '$url' mode=$mode → $opened")
    }
}
