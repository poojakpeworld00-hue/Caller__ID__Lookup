package com.callerid.admesh.surface

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import io.lighthouse.push.extended.LightHouseRichPush
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.PromoRevenueGauge
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.surface.interstitial.BackInterstitial
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.BuildConfig

object OpenPromoRegistry {
    private const val LOG_TAG = "OpenPromoRegistry"
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd: Boolean = false
    var callbackshow:Boolean = false
    var isOpenAppDismiss: Boolean = false

    var skipNextAppOpenAd: Boolean = false

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
        get() = appOpenAd != null

    fun loadAd(context: Context?) {
        if (isLoadingAd || isAdAvailable) {
            return
        }
        if (context == null) {
            return
        }
        val adsPreference = PromoVault.getInstance(context)

        if (!adsPreference.getBoolean("AppopenAds")) {
            Log.e(LOG_TAG, "AppopenAds disabled by Firebase flag")
            return
        }
        if (PromoKind.fromString(adsPreference.getString("IsAdType")) == PromoKind.GOOGLE) {
            isLoadingAd = true
            val request = AdRequest.Builder().build()

            if (adsPreference.getString("IsAdType").equals("Google", true)) {
                PromoVault.getInstance(context).getString("googleAppopen")?.let { adUnitId ->
                    AppOpenAd.load(
                        context, adUnitId, request, object : AppOpenAdLoadCallback() {
                            override fun onAdLoaded(ad: AppOpenAd) {

                                try {
                                    context.trackEvent("appopen_ad_loaded")
                                } catch (e: Exception) {
                                }

                                Log.d(LOG_TAG, "Ad was loaded.")
                                appOpenAd = ad
                                isLoadingAd = false
                            }

                            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                Log.e(
                                    LOG_TAG, "Ad failed to load: ${loadAdError.message}"
                                )

                                try {
                                    context.trackEvent("appopen_ad_fail")
                                } catch (e: Exception) {
                                }
                                isLoadingAd = false
                            }
                        })
                }
            }

        } else {
            return
        }
    }

    fun renderAdIfAvailable(
        activity: Activity, onShowAdCompleteListener: OnShowAdCompleteListener
    ) {
        if (!PromoVault.getInstance(activity).getBoolean("IsAdsON")) {
            return
        }

        if (!PromoVault.getInstance(activity).getBoolean("AppopenAds")) {
            Log.e(LOG_TAG, "AppopenAds disabled by Firebase flag")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }
        if (callbackshow) {
            Log.e(
                LOG_TAG, "callbackshow is Show"
            )
            return
        }

        if (LightHouseRichPush.shouldDeferOverlay(activity)) {
            Log.e(LOG_TAG, "Deferring App Open — rich-push overlay active")
            return
        }
        if (FlowInterstitial.Companion.isInterShow) {
            Log.e(
                LOG_TAG, "Inter is Show"
            )
            return
        }
        if (BackInterstitial.Companion.isInterBAckShow) {
            Log.e(
                LOG_TAG, "Inter Back is Show"
            )
            return
        }
        if (isShowingAd) {
            Log.e(
                LOG_TAG, "The app open ad is already showing."
            )
            return
        }
        if (!isAdAvailable) {
            Log.e(
                LOG_TAG, "The app open ad is not ready yet."
            )
            onShowAdCompleteListener.onShowAdComplete()
            loadAd(activity)
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.e(
                    LOG_TAG, "Ad dismissed fullscreen content."
                )

                try {
                    activity.trackEvent("appopen_ad_dismissed")
                } catch (_: Exception) {
                }

                appOpenAd = null
                isShowingAd = false
                isOpenAppDismiss = true
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(
                    activity
                )
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(
                    LOG_TAG, adError.message
                )
                try {
                    activity.trackEvent("appopen_ad_fail")
                } catch (_: Exception) {
                }
                appOpenAd = null
                isShowingAd = false
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(
                    activity
                )
            }

            override fun onAdShowedFullScreenContent() {
                Log.e(
                    LOG_TAG, "Ad showed fullscreen content."
                )
            }
        }

        activity.trackEvent("appopen_ad_shown")

        if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(activity)

        appOpenAd!!.setOnPaidEventListener {
            PromoRevenueGauge.reportPaidEvent(activity, it)
        }

        isShowingAd = true

        if (PromoVault.getInstance(activity).getString("IsAdType").equals("Google", true)) {
            appOpenAd?.show(activity)
        }
    }

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }

}
