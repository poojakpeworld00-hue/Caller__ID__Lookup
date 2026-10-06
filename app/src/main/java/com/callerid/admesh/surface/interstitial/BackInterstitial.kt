package com.callerid.admesh.surface.interstitial

import android.app.Activity
import android.content.Context
import android.util.Log
import com.facebook.ads.Ad
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.callerid.admesh.engine.AdsGate
import com.callerid.admesh.engine.LauncherPlacementAds
import com.callerid.admesh.engine.PromoRevenueGauge
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
        private var googleInterBackLoadedAt = 0L
        private var isLoadingBack = false
        private const val MAX_AGE_MS = 60 * 60_000L

        /** The loaded back interstitial, or null when there is none or it is older than an hour. */
        private fun freshBackInter(): InterstitialAd? {
            if (googleInterBack != null && android.os.SystemClock.elapsedRealtime() - googleInterBackLoadedAt > MAX_AGE_MS) {
                googleInterBack = null
            }
            return googleInterBack
        }
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

        if (!AdsGate.canRequestAds(activity)) return
        // Called on every foreground: never replace a fresh ad or stack a second request.
        if (freshBackInter() != null || isLoadingBack) return

        val id = pref.getString("googleBackInter").orEmpty()
        if (id.isBlank()) return
        val req = AdRequest.Builder().build()

        if (PromoKind.fromString(pref.getString("IsAdType")) == PromoKind.GOOGLE) {
            isLoadingBack = true
            val app = activity.applicationContext
            InterstitialAd.load(
                app, id, req,
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoadingBack = false
                        googleInterBack = ad
                        googleInterBackLoadedAt = android.os.SystemClock.elapsedRealtime()
                        Log.d("BackInterstitial", "Back Inter Loaded")
                        app.safeLog("Back_Inter_Loaded")
                    }

                    override fun onAdFailedToLoad(err: LoadAdError) {
                        isLoadingBack = false
                        googleInterBack = null
                        Log.e("BackInterstitial", "Back Inter Load Fail: ${err.message}")
                        // Analytics event names cannot carry free text; the code is enough.
                        app.safeLog("Back_Inter_Load_FAILED_${err.code}")
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
        if (!AdsGate.canRequestAds(act)) return safeClose("no_consent")

        // `<`, not `!=`: a missing key reads -1, and a lowered remote value can sit below the count.
        val target = pref.getInt("InterBackCounter")

        if (interBackCounter < target) {
            interBackCounter++
            return safeClose("counter_skip")
        }
        interBackCounter = 0

        // `back_ad_flow` / a `back_` link chain: the dynamic flow instead of the fixed back ad.
        if (LauncherPlacementAds.hasOwnFlow(act, "back")) {
            if (!LauncherPlacementAds.placementEnabled(act, "back")) return safeClose("back_ads_on_false")
            LauncherPlacementAds.showInterstitial(act, "back") { safeClose("back_flow") }
            return
        }

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
        val ad = freshBackInter()
        if (ad == null) {
            // Refilled for the next Back whatever this one ends up showing.
            fetchBackInterstitial(activity)
            return handleGoogleFail(activity, pref, safeClose)
        }
        activity.safeLog("google_back_inter_show_attempt")

        if (com.callerid.number.lookup.home.BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(activity)
        ad.setOnPaidEventListener { PromoRevenueGauge.reportPaidEvent(activity, it) }

        // The failure path runs once: a show() that throws and then also reports onAdFailedToShow.
        var failHandled = false
        fun failOnce() {
            if (failHandled) return
            failHandled = true
            handleGoogleFail(activity, pref, safeClose)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                isInterBAckShow = true
                AdsGate.fullScreenShown()
            }

            override fun onAdDismissedFullScreenContent() {
                googleInterBack = null
                isInterBAckShow = false
                AdsGate.fullScreenDismissed()
                safeClose("Google_Dismiss")
                fetchBackInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                googleInterBack = null
                isInterBAckShow = false
                AdsGate.fullScreenDismissed()
                failOnce()
                fetchBackInterstitial(activity)
            }
        }

        try {
            ad.show(activity)
        } catch (e: Exception) {
            googleInterBack = null
            isInterBAckShow = false
            AdsGate.fullScreenDismissed()
            failOnce()
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
        val isLoader = InterLoader.enabled(pref)
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
                        AdsGate.fullScreenDismissed()
                        if (context is Activity) FullScreenWaiter.hide()
                        onDismiss()
                    }

                    override fun onLoggingImpression(ad: Ad?) {}
                    override fun onInterstitialDisplayed(ad: Ad?) {
                        AdsGate.fullScreenShown()
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
