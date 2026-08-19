package com.callerid.admesh.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import java.util.Locale
import android.util.Log
import com.google.android.gms.ads.AdValue
import com.google.firebase.analytics.FirebaseAnalytics
import  com.callerid.number.lookup.home.BuildConfig

const val TAG_EVENT = "AdEvents"

/**
 * Universal Ad Revenue Tracker
 * Works for: Interstitial, AppOpen, Banner, Native, Rewarded, Rewarded-Interstitial
 */
object PromoRevenueGauge {

    private const val TAG = "PromoRevenueGauge"

    /** Real revenue handler */
    fun reportPaidEvent(context: Context, adValue: AdValue?) {
        if (adValue == null) {
            Log.e(TAG, "🔥 REAL PAID EVENT → $adValue ")
            return
        }
        val revenue = adValue.valueMicros / 1_000_000.0
        val currency = adValue.currencyCode ?: "USD"

        context.logAdRevenue(revenue, currency)

        Log.e(TAG, "🔥 REAL PAID EVENT → $revenue $currency (${adValue.valueMicros})")
    }

    /** Debug/Test mode revenue simulation */
    fun emitDebugRevenue(context: Context) {
        if (!BuildConfig.DEBUG) return   // 🚫 safety

        val revenue = 1.00
        val currency = "USD"

        context.logAdRevenue(revenue, currency)

        Log.e(TAG, "🧪 DEBUG SIM → $revenue $currency")
    }

}

/* -------------------------------------------------------------
   EXTENSION FUNCTIONS (Correct placement)
--------------------------------------------------------------*/

/** Log general key events */
fun Context.trackEvent(key: String) {
    val bundle = Bundle().apply { putString(key, key) }

    if (isDebuggable()) {
        Log.w(TAG_EVENT, "📌 KeyEvent (Debug): $key")
    } else {
        FirebaseAnalytics.getInstance(this)
            .logEvent(key, bundle)
    }
}

/**
 * Logs a runtime-permission outcome as `perm_<name>_allow` / `_deny`,
 * e.g. `perm_read_call_log_allow`. [permission] is a full
 * `android.permission.*` string; only the short name is used in the event.
 */
fun Context.logPermissionResult(permission: String, granted: Boolean) {
    val shortName = permission.substringAfterLast('.').lowercase(Locale.ROOT)
    trackEvent("perm_${shortName}_${if (granted) "allow" else "deny"}")
}

/**
 * Outcome of a permission the OS grants through a Settings screen rather than a
 * runtime prompt — overlay, full-screen intent, default-home. [gate] is a short
 * snake_case name, so the event reads `gate_overlay_allow`.
 */
fun Context.logGateResult(gate: String, granted: Boolean) {
    trackEvent("gate_${gate}_${if (granted) "allow" else "deny"}")
}

/** Check debug mode */
fun Context.isDebuggable(): Boolean {
    return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

/** Main Firebase revenue logger */
fun Context.logAdRevenue(revenue: Double, currency: String) {
    val params = Bundle().apply {
        putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob") // correct for AdMob
        putString(FirebaseAnalytics.Param.CURRENCY, currency)
        putDouble(FirebaseAnalytics.Param.VALUE, revenue)
    }

    if (BuildConfig.DEBUG) {
        Log.d("AdPaid", "🧪 DEBUG AD_IMPRESSION → $params")
    } else {
        FirebaseAnalytics.getInstance(this)
            .logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params)
    }


}
