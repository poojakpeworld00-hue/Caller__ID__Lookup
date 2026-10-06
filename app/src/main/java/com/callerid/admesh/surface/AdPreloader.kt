package com.callerid.admesh.surface

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.callerid.admesh.engine.AdsGate
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.interstitial.BackInterstitial
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.BuildConfig

object AdPreloader {

    private const val TAG = "AdPreloader"
    private const val MIN_GAP_MS = 20_000L

    private var lastTopUp = 0L

    fun topUp(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val vault = PromoVault.getInstance(activity)
        if (!vault.getBoolean("IsAdsON")) return
        if (!AdsGate.canRequestAds(activity)) return
        val now = SystemClock.uptimeMillis()
        if (lastTopUp != 0L && now - lastTopUp < MIN_GAP_MS) return
        lastTopUp = now
        if (BuildConfig.DEBUG) Log.d(TAG, "topping up the ad pools from ${activity::class.java.simpleName}")

        runCatching { FlowInterstitial().fetchInterstitial(activity) }
        runCatching { BackInterstitial().fetchBackInterstitial(activity) }
        // The native pool destroys its ad on every load, so only an empty pool asks.
        runCatching { if (!InlinePromo.hasPreloadedNative()) InlinePromo().fetchNativeAds(activity) }
        runCatching { InlinePromoStrip().fetchNativeBannerAds(activity) }
        runCatching { OpenPromoRegistry.loadAd(activity) }
        runCatching { BonusPromo.preload(activity) }
        runCatching { com.callerid.admesh.engine.ShellPromoConfig.preloadOnboardingExitAds(activity) }
    }
}
