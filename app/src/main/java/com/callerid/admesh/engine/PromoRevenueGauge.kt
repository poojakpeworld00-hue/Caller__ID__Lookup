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

object PromoRevenueGauge {

    private const val TAG = "PromoRevenueGauge"

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

    fun emitDebugRevenue(context: Context) {
        if (!BuildConfig.DEBUG) return

        val revenue = 1.00
        val currency = "USD"

        context.logAdRevenue(revenue, currency)

        Log.e(TAG, "🧪 DEBUG SIM → $revenue $currency")
    }

}

fun Context.trackEvent(key: String) {
    val bundle = Bundle().apply { putString(key, key) }

    if (isDebuggable()) {
        Log.w(TAG_EVENT, "📌 KeyEvent (Debug): $key")
    } else {
        FirebaseAnalytics.getInstance(this)
            .logEvent(key, bundle)
    }
}

fun Context.logPermissionResult(permission: String, granted: Boolean) {
    val shortName = permission.substringAfterLast('.').lowercase(Locale.ROOT)
    trackEvent("perm_${shortName}_${if (granted) "allow" else "deny"}")
}

fun Context.logGateResult(gate: String, granted: Boolean) {
    trackEvent("gate_${gate}_${if (granted) "allow" else "deny"}")
}

fun Context.isDebuggable(): Boolean {
    return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

fun Context.logAdRevenue(revenue: Double, currency: String) {
    val params = Bundle().apply {
        putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
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
