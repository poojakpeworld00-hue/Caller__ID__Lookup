package com.callerid.admesh.surface

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import io.lighthouse.push.extended.LightHouseRichPush
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.AdsGate
import com.callerid.admesh.engine.PromoRevenueGauge
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.surface.interstitial.BackInterstitial
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.BuildConfig

object OpenPromoRegistry {
    private const val LOG_TAG = "OpenPromoRegistry"

    /** Google's limit: an App Open ad older than this is no longer served. */
    private const val MAX_AGE_MS = 4 * 60 * 60_000L

    /** The post-call screen's block lapses on its own, in case that screen never opened. */
    private const val CALL_BLOCK_MAX_MS = 15 * 60_000L

    private var appOpenAd: AppOpenAd? = null
    private var loadedAt = 0L
    private var isLoadingAd = false
    var isShowingAd: Boolean = false
    var isOpenAppDismiss: Boolean = false

    private var callbackSince = 0L

    var callbackshow: Boolean
        get() = callbackSince != 0L && SystemClock.uptimeMillis() - callbackSince < CALL_BLOCK_MAX_MS
        set(value) {
            callbackSince = if (value) SystemClock.uptimeMillis() else 0L
        }

    /** Backed by [AdsGate.skipNextAppOpen], which also makes it lapse on its own. */
    var skipNextAppOpenAd: Boolean
        get() = AdsGate.isSkippingAppOpen
        set(value) {
            if (value) AdsGate.skipNextAppOpen() else AdsGate.clearSkipAppOpen()
        }

    /**
     * Set when the launcher opens another app, so the *return* from that app is the moment an ad may
     * show — the reference's `other_app_return`. Consumed once, on the first foreground after it is
     * set, so it never leaks into an unrelated return. This is what lets the app-tap ad fire on the
     * way back rather than alongside the app (e.g. the Play Store) it just launched.
     */
    private var expectReturnAd: Boolean = false

    fun expectReturnAd() {
        expectReturnAd = true
    }

    fun consumeExpectReturnAd(): Boolean {
        val v = expectReturnAd
        expectReturnAd = false
        return v
    }

    val isAdAvailable: Boolean
        get() {
            if (appOpenAd != null && SystemClock.elapsedRealtime() - loadedAt > MAX_AGE_MS) {
                Log.d(LOG_TAG, "loaded ad expired (older than 4h) — dropped")
                appOpenAd = null
            }
            return appOpenAd != null
        }

    fun loadAd(context: Context?) {
        if (context == null) return
        if (isLoadingAd || isAdAvailable) return
        val app = context.applicationContext
        val adsPreference = PromoVault.getInstance(app)
        if (!adsPreference.getBoolean("IsAdsON")) return

        if (!adsPreference.getBoolean("AppopenAds")) {
            Log.e(LOG_TAG, "AppopenAds disabled by Firebase flag")
            return
        }
        if (PromoKind.fromString(adsPreference.getString("IsAdType")) != PromoKind.GOOGLE) return
        if (!AdsGate.canRequestAds(app)) {
            Log.d(LOG_TAG, "no ad consent yet — not loading")
            return
        }
        val adUnitId = adsPreference.getString("googleAppopen").orEmpty()
        if (adUnitId.isBlank()) return

        isLoadingAd = true
        AppOpenAd.load(
            app, adUnitId, AdRequest.Builder().build(), object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    runCatching { app.trackEvent("appopen_ad_loaded") }
                    Log.d(LOG_TAG, "Ad was loaded.")
                    appOpenAd = ad
                    loadedAt = SystemClock.elapsedRealtime()
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(LOG_TAG, "Ad failed to load: ${loadAdError.message}")
                    runCatching { app.trackEvent("appopen_ad_fail") }
                    isLoadingAd = false
                }
            })
    }

    fun renderAdIfAvailable(
        activity: Activity, onShowAdCompleteListener: OnShowAdCompleteListener
    ) {
        // Every path that shows nothing still reports completion.
        val vault = PromoVault.getInstance(activity)
        if (!vault.getBoolean("IsAdsON")) return onShowAdCompleteListener.onShowAdComplete()

        if (!vault.getBoolean("AppopenAds")) {
            Log.e(LOG_TAG, "AppopenAds disabled by Firebase flag")
            return onShowAdCompleteListener.onShowAdComplete()
        }
        if (!vault.getString("IsAdType").equals("Google", true)) {
            return onShowAdCompleteListener.onShowAdComplete()
        }
        val blocked = when {
            callbackshow -> "post-call screen is up"
            LightHouseRichPush.shouldDeferOverlay(activity) -> "rich-push overlay active"
            FlowInterstitial.Companion.isInterShow -> "an interstitial is showing"
            BackInterstitial.Companion.isInterBAckShow -> "a back interstitial is showing"
            AdsGate.isFullScreenShowing -> "another full-screen ad is showing"
            isShowingAd -> "the app open ad is already showing"
            else -> null
        }
        if (blocked != null) {
            Log.e(LOG_TAG, "Not shown: $blocked")
            return onShowAdCompleteListener.onShowAdComplete()
        }
        if (!isAdAvailable) {
            Log.e(LOG_TAG, "The app open ad is not ready yet.")
            onShowAdCompleteListener.onShowAdComplete()
            loadAd(activity)
            return
        }

        val ad = appOpenAd ?: return onShowAdCompleteListener.onShowAdComplete()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.e(LOG_TAG, "Ad dismissed fullscreen content.")
                runCatching { activity.trackEvent("appopen_ad_dismissed") }
                appOpenAd = null
                isShowingAd = false
                AdsGate.fullScreenDismissed()
                isOpenAppDismiss = true
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(LOG_TAG, adError.message)
                runCatching { activity.trackEvent("appopen_ad_fail") }
                appOpenAd = null
                isShowingAd = false
                AdsGate.fullScreenDismissed()
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.e(LOG_TAG, "Ad showed fullscreen content.")
            }
        }

        activity.trackEvent("appopen_ad_shown")
        if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(activity)
        ad.setOnPaidEventListener { PromoRevenueGauge.reportPaidEvent(activity, it) }

        // Set only right before the show, so a skipped show can never leave it stuck true.
        isShowingAd = true
        AdsGate.fullScreenShown()
        ad.show(activity)
    }

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }

}
