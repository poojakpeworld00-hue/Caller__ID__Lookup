package com.callerid.admesh.surface.interstitial

import android.app.Activity
import android.content.Context
import android.util.Log
import com.facebook.ads.Ad
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.PromoTallyRegistry.interBackCounter
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.surface.hasNetwork

class BackInterstitial {

    companion object {
        private var _isInterBAckShow: Boolean = false
        var isInterBAckShow: Boolean
            get() = _isInterBAckShow
            set(value) {
                _isInterBAckShow = value
            }
        private var googleInterBack: InterstitialAd? = null
    }

    fun fetchBackInterstitial(activity: Activity) {
        val pref = PromoVault.getInstance(activity)

        if (!pref.getBoolean("IsAdsON")) {
            activity.safeLog("BackLoad:AdsOFF")
            return
        }

        if (!pref.getBoolean("InterAds")) {
            activity.safeLog("BackLoad:InterAdsDisabled")
            return
        }

        if (!pref.getBoolean("IsBack")) {
            activity.safeLog("BackLoad:BackAdsOFF")
            return
        }

        val id = pref.getString("googleBackInter") ?: return
        val req = AdRequest.Builder().build()

        if (PromoKind.fromString(pref.getString("IsAdType")) == PromoKind.GOOGLE) {
            InterstitialAd.load(
                activity, id, req,
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(ad: InterstitialAd) {
                        googleInterBack = ad
                        Log.d("BackInterstitial", "Back Inter Loaded")
                        activity.safeLog("Back_Inter_Loaded")
                    }

                    override fun onAdFailedToLoad(err: LoadAdError) {
                        googleInterBack = null
                        Log.e("BackInterstitial", "Back Inter Load Fail: ${err.message}")
                        activity.safeLog("Back_Inter_Load_FAILED:${err.message}")
                    }
                })
        }
    }

    fun renderBackInterstitial(activity: Activity?, adsClose: () -> Unit) {
        showBackInternal(activity, adsClose)
    }

    private fun showBackInternal(activity: Activity?, adsClose: () -> Unit) {
        val act = activity ?: return adsClose()
        val pref = PromoVault.getInstance(act)
        var closedOnce = false

        fun safeClose(reason: String) {
            if (closedOnce) return
            closedOnce = true
            act.safeLog("Closed_$reason")
            Log.e("BackInterstitial", "Closed: $reason")
            try {
                adsClose()
            } catch (_: Exception) {
            }
        }

        if (!hasNetwork(act)) return safeClose("no_network")
        if (!pref.getBoolean("IsAdsON")) return safeClose("ads_off")

        if (!pref.getBoolean("InterAds")) return safeClose("inter_ads_disabled")
        if (!pref.getBoolean("IsBack")) return safeClose("back_ads_disabled")

        val target = pref.getInt("InterBackCounter")

        if (interBackCounter != target) {
            interBackCounter++
            return safeClose("counter_skip")
        }
        interBackCounter = 0

        when (PromoKind.fromString(pref.getString("IsAdType"))) {

            PromoKind.GOOGLE -> {
                showGoogleBackInter(act, pref, ::safeClose)
            }

            PromoKind.FACEBOOK -> {
                showFacebookBackInter(
                    act,
                    onDismiss = { safeClose("fb_back_dismiss") },
                    onFail = {
                        showCustomAfterFBFail(act, pref) {
                            safeClose("fb_back_fail")
                        }
                    }
                )
            }

            PromoKind.CUSTOM, PromoKind.UNKNOWN -> {
                if (pref.getBoolean("IsCustomADS"))
                    FlowInterstitial.openDirectLink(act) { safeClose("custom_open") }
                else safeClose("custom_disabled")
            }

            else -> safeClose("invalid_type")
        }
    }

    private fun showGoogleBackInter(
        activity: Activity,
        pref: PromoVault,
        safeClose: (String) -> Unit
    ) {
        val ad = googleInterBack
        if (ad == null) {
            return handleGoogleFail(activity, pref, safeClose)
        }
        activity.safeLog("google_back_inter_show_attempt")

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                isInterBAckShow = true
            }

            override fun onAdDismissedFullScreenContent() {
                googleInterBack = null
                isInterBAckShow = false
                safeClose("Google_Dismiss")
                fetchBackInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                googleInterBack = null
                handleGoogleFail(activity, pref, safeClose)
                fetchBackInterstitial(activity)
            }
        }

        try {
            ad.show(activity)
        } catch (e: Exception) {
            googleInterBack = null
            handleGoogleFail(activity, pref, safeClose)
            fetchBackInterstitial(activity)
        }
    }

    private fun handleGoogleFail(
        activity: Activity,
        pref: PromoVault,
        safeClose: (String) -> Unit
    ) {
        if (pref.getBoolean("IsFail_FB")) {
            showFacebookBackInter(
                activity,
                onDismiss = { safeClose("fb_dismiss") },
                onFail = { showCustomAfterFBFail(activity, pref, safeClose) }
            )

        } else {
            if (pref.getBoolean("IsCustomADS")) {
                FlowInterstitial.openDirectLink(activity) { safeClose("google_fail_custom") }
            } else safeClose("google_fail_no_fb_no_custom")
        }
    }

    private fun showFacebookBackInter(
        context: Context,
        onDismiss: () -> Unit,
        onFail: () -> Unit
    ) {
        val pref = PromoVault.getInstance(context)
        val isLoader = pref.getBoolean("isLoaderForFB")
        val fbId = pref.getString("faceB_InterAds") ?: return onFail()

        val fb = com.facebook.ads.InterstitialAd(context, fbId)

        if (context is Activity) FullScreenWaiter.show(context, isLoader)

        fb.loadAd(
            fb.buildLoadAdConfig()
                .withAdListener(object : InterstitialAdListener {

                    override fun onAdLoaded(ad: Ad?) {
                        if (context is Activity) FullScreenWaiter.hide()
                        try {
                            fb.show()
                        } catch (e: Exception) {
                            onFail()
                        }
                    }

                    override fun onError(ad: Ad?, err: com.facebook.ads.AdError?) {
                        if (context is Activity) FullScreenWaiter.hide()
                        onFail()
                    }

                    override fun onInterstitialDismissed(ad: Ad?) {
                        if (context is Activity) FullScreenWaiter.hide()
                        onDismiss()
                    }

                    override fun onLoggingImpression(ad: Ad?) {}
                    override fun onInterstitialDisplayed(ad: Ad?) {
                        if (context is Activity) FullScreenWaiter.hide()
                    }

                    override fun onAdClicked(ad: Ad?) {}

                }).build()
        )
    }

    private fun showCustomAfterFBFail(
        context: Activity,
        pref: PromoVault,
        safeClose: (String) -> Unit
    ) {
        if (pref.getBoolean("IsCustomADS"))
            FlowInterstitial.openDirectLink(context) { safeClose("fb_fail_custom") }
        else safeClose("fb_fail_no_custom")
    }

    private fun Context.safeLog(event: String) {
        try {
            this.trackEvent(event)
        } catch (_: Exception) {
        }
    }

}
