package com.callerid.admesh.surface.interstitial

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.content.ContextCompat
import com.facebook.ads.Ad
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.AdsGate
import com.callerid.admesh.engine.LauncherPlacementAds
import com.callerid.admesh.engine.PromoTallyRegistry.interCounter
import com.callerid.admesh.engine.PromoRevenueGauge
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.surface.DirectLinkOpener
import com.callerid.admesh.surface.DrawerAdRunner
import com.callerid.admesh.surface.hasNetwork
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.R
class FlowInterstitial {

    companion object {

        /** The app-wide interstitial has no placement keys of its own: only the global ones apply. */
        private const val APP_PLACEMENT = "app"

        private var _isInterShow: Boolean = false
        var isInterShow: Boolean
            get() = _isInterShow
            set(value) {
                _isInterShow = value
            }
        private var googleInterAd: InterstitialAd? = null
        private var googleInterLoadedAt = 0L
        private var isGoogleInterLoading = false

        /** An interstitial kept longer than this is no longer served by AdMob; it is dropped. */
        private const val INTER_MAX_AGE_MS = 60 * 60_000L

        private fun freshGoogleInter(): InterstitialAd? {
            if (googleInterAd != null && android.os.SystemClock.elapsedRealtime() - googleInterLoadedAt > INTER_MAX_AGE_MS) {
                Log.d("FlowInterstitial", "preloaded inter expired (older than 1h) — dropped")
                googleInterAd = null
            }
            return googleInterAd
        }

        /** Uptime at which an interstitial request started showing; 0 when none is in progress. */
        private var showInFlightSince = 0L
        private const val SHOW_IN_FLIGHT_MAX_MS = 60_000L

        /** A show that has not closed (or put an ad on screen) after this long is given up on. */
        private const val WATCHDOG_MS = 20_000L
        private const val WATCHDOG_MAX_CHECKS = 6

        private var preloadedFbAd: com.facebook.ads.InterstitialAd? = null
        private var isFbPreloading = false

        private var fbOnDismissed: (() -> Unit)? = null
        private var fbOnFail: (() -> Unit)? = null
        var isOpened = false
        var onTabClosed: (() -> Unit)? = null
        fun handleTabReturn(context: Context) {
            if (!isOpened) return

            isOpened = false
            onTabClosed?.invoke()
            onTabClosed = null

            releaseSession(context)
        }
        private var customTabsClient: CustomTabsClient? = null
        private var customTabsSession: CustomTabsSession? = null
        private var serviceConnection: CustomTabsServiceConnection? = null

        fun releaseSession(context: Context) {
            serviceConnection?.let {
                try {
                    context.unbindService(it)
                } catch (_: Exception) {}
            }
            serviceConnection = null
            customTabsClient = null
            customTabsSession = null
        }

        fun preloadFbAd(context: Context) {
            val pref = PromoVault.getInstance(context)
            if (!pref.getBoolean("IsAdsON")) return
            if (!AdsGate.canRequestAds(context)) return
            if (isFbPreloading || preloadedFbAd != null) return
            val fbId = pref.getString("faceB_InterAds") ?: return
            isFbPreloading = true
            val inter = com.facebook.ads.InterstitialAd(context, fbId)
            inter.loadAd(
                inter.buildLoadAdConfig()
                    .withAdListener(object : com.facebook.ads.InterstitialAdListener {
                        override fun onAdLoaded(ad: com.facebook.ads.Ad?) {
                            preloadedFbAd = inter
                            isFbPreloading = false
                        }
                        override fun onError(ad: com.facebook.ads.Ad?, e: com.facebook.ads.AdError?) {
                            isFbPreloading = false
                            fbOnFail?.invoke()
                            fbOnFail = null
                            fbOnDismissed = null
                        }
                        override fun onInterstitialDismissed(ad: com.facebook.ads.Ad?) {
                            AdsGate.fullScreenDismissed()
                            preloadFbAd(context)
                            fbOnDismissed?.invoke()
                            fbOnDismissed = null
                            fbOnFail = null
                        }
                        override fun onInterstitialDisplayed(ad: com.facebook.ads.Ad?) {
                            AdsGate.fullScreenShown()
                        }
                        override fun onAdClicked(ad: com.facebook.ads.Ad?) {}
                        override fun onLoggingImpression(ad: com.facebook.ads.Ad?) {}
                    }).build()
            )
        }

        fun openDirectLink(context: Activity, onClosed: () -> Unit) {
            val url = PromoVault.getInstance(context).getString("DirectLink")

            if (url.isNullOrEmpty()) {
                onClosed()
                return
            }

            // App-wide open-type switch: only Custom Tab uses the session-tracked path below (so
            // onClosed fires when the tab closes). WebView / browser open fire-and-forget and the
            // flow continues right away.
            if (DirectLinkOpener.mode(context) != DirectLinkOpener.Mode.CUSTOM_TAB) {
                // Carry on once the user is back from the page, not the moment it opens.
                if (DirectLinkOpener.open(context, url)) DrawerAdRunner.onReturnTo(context, onClosed)
                else onClosed()
                return
            }

            val uri = Uri.parse(url)
            isOpened = true
            onTabClosed = onClosed
            // Screens that are not FrameActivities never called handleTabReturn; their resume does it now.
            DrawerAdRunner.onReturnTo(context) { handleTabReturn(context) }
            AdsGate.skipNextAppOpen()

            getSession(context) { session ->

                val customTab = CustomTabsIntent.Builder(session)
                    .setShowTitle(true)
                    .setToolbarColor(ContextCompat.getColor(context, R.color.black))
                    .build()

                try {
                    customTab.launchUrl(context, uri)
                } catch (e: Exception) {
                    openInBrowser(context, uri, onClosed)
                }
            }
        }

        private fun getSession(
            context: Context,
            onReady: (CustomTabsSession?) -> Unit
        ) {
            if (customTabsSession != null) {
                onReady(customTabsSession)
                return
            }

            serviceConnection = object : CustomTabsServiceConnection() {

                override fun onCustomTabsServiceConnected(
                    name: ComponentName,
                    client: CustomTabsClient
                ) {
                    customTabsClient = client
                    customTabsSession = client.newSession(null)
                    onReady(customTabsSession)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    customTabsClient = null
                    customTabsSession = null
                    onReady(null)
                }
            }

            // Without Chrome the bind fails and onReady never ran; a null session still opens a
            // Custom Tab in any supporting browser, or falls back to the browser.
            val bound = runCatching {
                CustomTabsClient.bindCustomTabsService(
                    context,
                    "com.android.chrome",
                    serviceConnection as CustomTabsServiceConnection
                )
            }.getOrDefault(false)
            if (!bound) {
                serviceConnection = null
                onReady(null)
            }
        }

        private fun openInBrowser(
            context: Activity,
            uri: Uri,
            onClosed: () -> Unit
        ) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                context.startActivity(intent)

            } catch (e: Exception) {

            } finally {

                isOpened = false
                onTabClosed?.invoke()
                onTabClosed = null
                onClosed()
            }
        }

    }

    fun fetchInterstitial(activity: Activity) {
        val pref = PromoVault.getInstance(activity)
        if (!pref.getBoolean("IsAdsON")) return

        if (!pref.getBoolean("InterAds")) return

        if (!pref.getBoolean("is_preload_ads")) return
        if (!AdsGate.canRequestAds(activity)) return

        val adType = PromoKind.fromString(pref.getString("IsAdType"))

        // Called on every foreground: a second request would only replace a good ad.
        if (adType == PromoKind.GOOGLE && freshGoogleInter() == null && !isGoogleInterLoading) {
            val id = pref.getString("googleInter").orEmpty()
            if (id.isNotBlank()) {
                activity.safeLog("google_inter_load_start")
                isGoogleInterLoading = true
                val app = activity.applicationContext
                InterstitialAd.load(
                    app, id, AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            isGoogleInterLoading = false
                            googleInterAd = null
                            app.safeLog("google_inter_load_failed_${error.code}")
                            Log.e("FlowInterstitial", "Load Failed: ${error.message}")
                        }
                        override fun onAdLoaded(ad: InterstitialAd) {
                            isGoogleInterLoading = false
                            googleInterAd = ad
                            googleInterLoadedAt = android.os.SystemClock.elapsedRealtime()
                            app.safeLog("google_inter_loaded")
                            Log.d("FlowInterstitial", "Google inter loaded")
                        }
                    })
            }
        }

        if (adType == PromoKind.FACEBOOK || pref.getBoolean("IsFail_FB")) {
            preloadFbAd(activity)
        }
    }

    fun renderInterstitial(activity: Activity?, adsClose: () -> Unit) {
        showAdInternal(activity, adsClose)
    }

    private fun showAdInternal(activity: Activity?, adsClose: () -> Unit) {
        val act = activity ?: return adsClose()
        act.safeLog("inter_request_start")

        val pref = PromoVault.getInstance(act)
        if (act.isFinishing || act.isDestroyed) return adsClose()

        // A second tap while the first one's ad is still loading or on screen is ignored.
        val now = android.os.SystemClock.uptimeMillis()
        if (showInFlightSince != 0L && now - showInFlightSince < SHOW_IN_FLIGHT_MAX_MS) {
            act.safeLog("inter_request_ignored_in_flight")
            return
        }
        showInFlightSince = now
        var hasClosed = false

        fun safeClose(reason: String) {
            if (hasClosed) return
            hasClosed = true
            showInFlightSince = 0L
            act.safeLog("inter_closed_$reason")
            adsClose()
        }

        // A load or callback that never comes back must not leave the caller waiting for ever.
        val watchdog = android.os.Handler(android.os.Looper.getMainLooper())
        var checks = 0
        lateinit var check: Runnable
        check = Runnable {
            if (hasClosed) return@Runnable
            if (isInterShow || AdsGate.isFullScreenShowing) {
                if (++checks < WATCHDOG_MAX_CHECKS) watchdog.postDelayed(check, WATCHDOG_MS)
                return@Runnable
            }
            act.safeLog("inter_watchdog_close")
            safeClose("watchdog")
        }
        watchdog.postDelayed(check, WATCHDOG_MS)

        if (!AdsGate.canRequestAds(act)) return safeClose("no_consent")

        if (!hasNetwork(act)) return safeClose("no_network")
        if (!pref.getBoolean("IsAdsON")) return safeClose("ads_off")

        if (!pref.getBoolean("InterAds")) return safeClose("inter_ads_disabled")

        // `<`, not `!=`: a missing key reads -1 and a lowered remote counter can sit below the count.
        val target = pref.getInt("InterCounter")
        if (interCounter < target) {
            interCounter++
            act.safeLog("inter_counter_skip")
            return safeClose("counter_skip")
        }
        interCounter = 0
        act.safeLog("inter_counter_triggered")

        // QRScanner's link-first, on every in-app interstitial: `link_first_then` + `DirectLink`
        // (+ `IsCustomADS`) open the links first, then the listed follow-ups, instead of this ad.
        if (LauncherPlacementAds.showLinkFirst(act, APP_PLACEMENT) { safeClose("link_first") }) {
            act.safeLog("inter_link_first")
            return
        }
        // A global `ad_flow` ("reward,inter", "app_open,inter", …) replaces the plain interstitial.
        if (LauncherPlacementAds.showAdFlow(act, APP_PLACEMENT) { safeClose("ad_flow") }) {
            act.safeLog("inter_ad_flow")
            return
        }

        val isPreload = pref.getBoolean("is_preload_ads")

        when (PromoKind.fromString(pref.getString("IsAdType"))) {

            PromoKind.GOOGLE -> {
                act.safeLog("inter_type_google")
                if (isPreload) {
                    renderGoogleInterstitial(act, pref, ::safeClose)
                } else {
                    loadAndShowGoogleOnDemand(act, pref, ::safeClose)
                }
            }

            PromoKind.FACEBOOK -> {
                act.safeLog("inter_type_facebook")
                if (isPreload && preloadedFbAd != null) {
                    showPreloadedFbAd(
                        act,
                        onDismissed = { safeClose("fb_dismiss") },
                        onFail = {
                            act.safeLog("fb_preload_fail_fallback")
                            loadAndRenderFbInterstitial(
                                act,
                                onDismissed = { safeClose("fb_dismiss") },
                                onFail = { showCustomAfterFacebookFail(act, pref) { safeClose("fb_fail_custom") } }
                            )
                        }
                    )
                } else {
                    loadAndRenderFbInterstitial(
                        act,
                        onDismissed = { safeClose("fb_dismiss") },
                        onFail = {
                            act.safeLog("fb_load_fail")
                            showCustomAfterFacebookFail(act, pref) { safeClose("fb_fail_custom") }
                        }
                    )
                }
            }

            PromoKind.CUSTOM, PromoKind.UNKNOWN -> {
                act.safeLog("inter_type_custom")
                if (pref.getBoolean("IsCustomADS"))
                    openDirectLink(act) { safeClose("custom_opened") }
                else safeClose("custom_disabled")
            }
        }
    }

    private fun renderGoogleInterstitial(
        activity: Activity,
        pref: PromoVault,
        safeClose: (String) -> Unit
    ) {

        val inter = freshGoogleInter()
        if (inter == null) {
            activity.safeLog("google_inter_null")
            // Refilled for the next request whatever this one ends up showing.
            fetchInterstitial(activity)
            return handleGoogleFail(activity, pref, safeClose)
        }

        activity.trackEvent("google_inter_show_attempt")

        if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(activity)

        inter.setOnPaidEventListener {
            PromoRevenueGauge.reportPaidEvent(activity, it)
        }

        // The failure path runs once, even when show() throws and also reports onAdFailedToShow.
        var failHandled = false
        fun failOnce() {
            if (failHandled) return
            failHandled = true
            handleGoogleFail(activity, pref, safeClose)
        }

        inter.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                isInterShow = true
                AdsGate.fullScreenShown()
            }

            override fun onAdDismissedFullScreenContent() {
                isInterShow = false
                AdsGate.fullScreenDismissed()
                googleInterAd = null
                activity.safeLog("google_inter_dismiss")
                safeClose("google_dismiss")
                if (pref.getBoolean("is_preload_ads")) fetchInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isInterShow = false
                AdsGate.fullScreenDismissed()
                googleInterAd = null
                activity.safeLog("google_inter_failed_show_${error.code}")
                failOnce()
                if (pref.getBoolean("is_preload_ads")) fetchInterstitial(activity)
            }
        }

        try {
            inter.show(activity)
        } catch (e: Exception) {
            isInterShow = false
            AdsGate.fullScreenDismissed()
            googleInterAd = null
            activity.safeLog("google_inter_exception")
            failOnce()
            fetchInterstitial(activity)
        }
    }

    private fun loadAndShowGoogleOnDemand(
        activity: Activity,
        pref: PromoVault,
        safeClose: (String) -> Unit
    ) {
        val id = pref.getString("googleInter") ?: return handleGoogleFail(activity, pref, safeClose)
        val isLoader = InterLoader.enabled(pref)

        activity.safeLog("google_inter_ondemand_load_start")
        FullScreenWaiter.show(activity, isLoader)

        InterstitialAd.load(
            activity, id, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    FullScreenWaiter.hide()
                    googleInterAd = ad
                    googleInterLoadedAt = android.os.SystemClock.elapsedRealtime()
                    activity.safeLog("google_inter_ondemand_loaded")
                    renderGoogleInterstitial(activity, pref, safeClose)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    FullScreenWaiter.hide()
                    googleInterAd = null
                    activity.safeLog("google_inter_ondemand_fail_${error.code}")
                    handleGoogleFail(activity, pref, safeClose)
                }
            }
        )
    }

    private fun showPreloadedFbAd(
        activity: Activity,
        onDismissed: () -> Unit,
        onFail: () -> Unit
    ) {
        val ad = preloadedFbAd
        if (ad == null || !ad.isAdLoaded) {
            preloadedFbAd = null
            onFail()
            return
        }
        preloadedFbAd = null

        fbOnDismissed = onDismissed
        fbOnFail = onFail
        try {
            ad.show()
            activity.safeLog("fb_preloaded_show")
        } catch (e: Exception) {
            activity.safeLog("fb_preloaded_show_exception")
            fbOnDismissed = null
            fbOnFail = null
            onFail()
        }
    }

    private fun handleGoogleFail(
        activity: Activity,
        pref: PromoVault,
        safeClose: (String) -> Unit
    ) {
        activity.safeLog("google_fail_start")

        val fbEnabled = pref.getBoolean("IsFail_FB")
        val customEnabled = pref.getBoolean("IsCustomADS")

        if (fbEnabled) {

            activity.safeLog("google_fail_try_facebook")

            loadAndRenderFbInterstitial(
                activity,
                onDismissed = { safeClose("fb_dismiss") },
                onFail = {
                    activity.safeLog("fb_fail_after_google_fail")
                    showCustomAfterFacebookFail(activity, pref, safeClose)
                }
            )
        } else if (LauncherPlacementAds.hasFallback(activity, APP_PLACEMENT)) {
            // QRScanner's InterFallbackAds: the `inter_fallback` chain (rewarded / full_native /
            // custom / directlink, in the configured order), then the flow continues.
            activity.safeLog("google_fail_fallback_chain")
            LauncherPlacementAds.runFallback(activity, APP_PLACEMENT) { safeClose("google_fail_fallback") }
        } else {
            if (customEnabled) {
                activity.safeLog("google_fail_open_custom")
                openDirectLink(activity) { safeClose("google_fail_custom") }
            } else safeClose("google_fail_no_fb_no_custom")
        }
    }

    fun loadAndRenderFbInterstitial(
        context: Context,
        onDismissed: () -> Unit,
        onFail: () -> Unit
    ) {
        val pref = PromoVault.getInstance(context)
        val isLoader = InterLoader.enabled(pref)
        val fbId = pref.getString("faceB_InterAds") ?: return onFail()

        context.safeLog("facebook_inter_load_start")

        val inter = com.facebook.ads.InterstitialAd(context, fbId)

        if (context is Activity) FullScreenWaiter.show(context, isLoader)

        inter.loadAd(
            inter.buildLoadAdConfig()
                .withAdListener(object : InterstitialAdListener {

                    override fun onAdLoaded(ad: Ad?) {
                        context.safeLog("facebook_inter_loaded")
                        if (context is Activity) FullScreenWaiter.hide()
                        try {
                            inter.show()
                            context.safeLog("facebook_inter_show")
                        } catch (e: Exception) {
                            context.safeLog("facebook_inter_show_exception")
                            onFail()
                        }
                    }

                    override fun onError(ad: Ad?, error: com.facebook.ads.AdError?) {
                        context.safeLog("facebook_inter_error_${error?.errorCode}")
                        if (context is Activity) FullScreenWaiter.hide()
                        onFail()
                    }

                    override fun onInterstitialDismissed(ad: Ad?) {
                        AdsGate.fullScreenDismissed()
                        context.safeLog("facebook_inter_dismiss")
                        if (context is Activity) FullScreenWaiter.hide()
                        onDismissed()
                    }

                    override fun onInterstitialDisplayed(ad: Ad?) {
                        AdsGate.fullScreenShown()
                        context.safeLog("facebook_inter_displayed")

                        if (context is Activity) FullScreenWaiter.hide()
                    }

                    override fun onAdClicked(ad: Ad?) {
                        context.safeLog("facebook_inter_clicked")
                    }

                    override fun onLoggingImpression(ad: Ad?) {
                        context.safeLog("facebook_inter_impression")
                    }

                }).build()
        )
    }

    private fun showCustomAfterFacebookFail(
        activity: Activity,
        pref: PromoVault,
        safeClose: (String) -> Unit
    ) {
        activity.safeLog("custom_after_fb_fail")

        if (pref.getBoolean("IsCustomADS"))
            openDirectLink(activity) { safeClose("fb_fail_custom") }
        else safeClose("fb_fail_no_custom")
    }

    private fun Context.safeLog(event: String) {
        try {
            trackEvent(event)
            Log.d("InterADsLog", event)
        } catch (_: Exception) {
        }
    }
}
