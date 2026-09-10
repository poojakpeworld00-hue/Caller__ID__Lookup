package com.callerid.admesh.surface

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.ads.Ad
import com.facebook.ads.InterstitialAdListener
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.FormError
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.lighthouse.push.LightHouse
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.model.OnFeedReady
import com.callerid.admesh.model.fetchGeoFromIp
import com.callerid.admesh.engine.PromoRevenueGauge
import com.callerid.admesh.engine.PromoConfigLoader
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.RemoteConfigRules
import com.callerid.admesh.engine.GmaConsentRegistry
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.surface.interstitial.BackInterstitial
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.permit.PermitSource
import com.callerid.number.lookup.home.permit.ScreenGlob
import com.callerid.number.lookup.home.kit.AppPrefs
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_DARK
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_LIGHT
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_SYSTEM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

open class PromoAnchorActivity : AppCompatActivity() {
    private var customTabsSession: CustomTabsSession? = null
    private var customTabsClient: CustomTabsClient? = null
    var isCustomTabOpened = false
    var isCloseHandled = false
    var onCustomTabClosed: (() -> Unit)? = null
    private var activity: Activity? = null
    private var onGetData: OnFeedReady? = null
    private var isSplash: Boolean? = false
    private val isMobileAdsInitialized = AtomicBoolean(false)
    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private val referrerHandoffDone = AtomicBoolean(false)
    private var isGoogleAdsEnabled = true
    private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor()

    
    companion object {

        const val APPOPEN_TAG = "AppOpenAd"

        const val CONFIG_TAG = "GetDataConfig"

        const val DEBUG_AUDIENCE_MARKETING = true

        /**
         * Applies [DEBUG_AUDIENCE_MARKETING] to **release** builds too, instead of letting real
         * attribution decide.
         *
         * A signed APK is the only way to exercise the release config — R8, the real ad units,
         * the shrunk resources — but a release APK installed by adb or a direct download has no
         * Play install referrer, so LightHouse settles it organic and the `marketing` half of
         * the config can never be reached on a test device. This forces both halves of the
         * decision (the audience read below and the SDK's own install source in `LookupCoreApp`)
         * onto the chosen side so they agree.
         *
         * **Must be `false` in anything that reaches Play.** Left `true`, every real install is
         * pinned to one audience and genuine attribution is never consulted. The splash logs a
         * warning on every launch while it is on, so a build that ships by accident says so in
         * logcat.
         */
        const val FORCE_AUDIENCE_IN_RELEASE = false

        /**
         * True when the audience is being forced rather than resolved — either because this is a
         * debug build, or because [FORCE_AUDIENCE_IN_RELEASE] is still on.
         */
        val isAudienceForced: Boolean
            get() = BuildConfig.DEBUG || FORCE_AUDIENCE_IN_RELEASE

        const val ATTRIBUTION_WAIT_MS = 5_000L

        
        const val COUNTRY_LIST_ALL = "all"
    }

    open fun getData(
        act: Activity, isSplsh: Boolean? = false, onData: OnFeedReady
    ) {
        activity = act
        onGetData = onData
        isSplash = isSplsh

        lifecycleScope.launch(Dispatchers.IO) {
            try {

                if (!hasInternet(this@PromoAnchorActivity)) {
                    withContext(Dispatchers.Main) {
                        onGetData?.onError()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    try {
                        val googleMobileAdsConsentManager =
                            GmaConsentRegistry.getInstance(
                                applicationContext
                            )

                        googleMobileAdsConsentManager.gatherConsent(this@PromoAnchorActivity) { consentError: FormError? ->

                            if (consentError != null) {
                                Log.w("PromoAnchorActivity", "Consent error: ${consentError.message} — proceeding without ads consent")
                            }
                            initializeMobileAdsSdk()
                            if (googleMobileAdsConsentManager.isPrivacyOptionsRequired) {
                                invalidateOptionsMenu()
                            }
                        }

                        if (googleMobileAdsConsentManager.canRequestAds()) {
                            initializeMobileAdsSdk()
                        }
                    } catch (e: Exception) {
                        Log.e("PromoAnchorActivity", "Consent manager error", e)
                        initializeMobileAdsSdk()
                        withContext(Dispatchers.Main) {
                            onGetData?.onError()
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onGetData?.onError()
                }
            }
        }
    }

    fun hasInternet(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {

            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        if (!isMobileAdsInitialized.getAndSet(true)) {
            backgroundExecutor.execute {
                try {
                    activity?.let {
                        isGoogleAdsEnabled = true
                        MobileAds.initialize(it) {}
                        if (BuildConfig.DEBUG) Log.d("PromoAnchorActivity", "MobileAds initialized")
                    }
                } catch (e: Exception) {
                    isGoogleAdsEnabled = false
                    Log.e("PromoAnchorActivity", "Failed to initialize MobileAds", e)
                    isMobileAdsInitialized.set(false)
                }
            }
        }

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        activity?.let { host ->
            
            RemoteConfigRules.withSettings(remoteConfig) {
                remoteConfig.fetchAndActivate().addOnCompleteListener(host) { task ->
                    if (task.isSuccessful) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            setResponceInPref(remoteConfig)
                        }
                    } else {
                        onGetData?.onError()
                    }
                }
            }
        }
    }

    private fun setResponceInPref(remoteConfig: FirebaseRemoteConfig) {

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val blobKey = if (BuildConfig.DEBUG) "DEBUG_GET_DATA_LIST" else "GET_DATA_LIST"
                val configString = remoteConfig.getString(blobKey)

                if (configString.isNullOrEmpty()) {
                    Log.w(CONFIG_TAG, "$blobKey is empty → nothing ingested (using cached/default prefs)")
                    return@launch
                }

                val response = JSONObject(configString)
                val adsPref = PromoVault.getInstance(this@PromoAnchorActivity)

                val isSplit = response.has("marketing") || response.has("organic")
                val onMarketing = adsPref.getBoolean("OnMaketing")
                if (BuildConfig.DEBUG) Log.d(
                    CONFIG_TAG,
                    "fetched $blobKey (${configString.length} chars) → audienceSplit=$isSplit, " +
                        "OnMaketing=$onMarketing "
                )

                adsPref.putString("GET_DATA_RAW", configString)
                adsPref.putBoolean("__cfg_audience_split", isSplit)

                ingestConfig(this@PromoAnchorActivity, audienceBlock(response, onMarketing))

                checkInstallerRefere()

            } catch (e: Exception) {
                Log.e("PromoAnchorActivity", "Failed to update ad preferences: ${e.message}")
            }
        }
    }

    private fun ingestConfig(context: Context, root: JSONObject) {
        val fb = PromoConfigLoader.absorb(context, root)
        if (fb.usable) setApplication(fb.appId, fb.clientToken)
    }

    private fun audienceBlock(response: JSONObject, isMarketing: Boolean): JSONObject =
        PromoConfigLoader.audienceBlock(response, isMarketing)

    fun resolveInlineThemeKey(context: Context): String = PromoConfigLoader.inlineThemeKey(context)

    private fun checkInstallerRefere() {
        if (activity!!.getPreferences(MODE_PRIVATE).getBoolean("isReferrerDone", false)) {
            onReferrerSettled()
            return
        }

        val referrerClient = InstallReferrerClient.newBuilder(activity).build()
        backgroundExecutor.execute(Runnable { readInstallReferrer(referrerClient) })
    }

    private fun onReferrerSettled() {
        if (referrerHandoffDone.compareAndSet(false, true)) {
            funOnAdsLoad()
        }
    }

    private fun funOnAdsLoad() {
        activity?.let { activity ->
            val adsPreference = PromoVault.getInstance(activity)
            lifecycleScope.launch(Dispatchers.IO) {

                val location = fetchGeoFromIp()
                location?.countryCode?.takeIf { it.isNotBlank() }?.let { iso ->
                    val prefs = StorageRegistry(activity)
                    if (prefs.homeCountryIso.isBlank()) {
                        prefs.homeCountryIso = iso
                        if (BuildConfig.DEBUG) Log.d("LocationCheck", "Country code set from IP: $iso")
                    }
                }

                val isMarketingOn = if (isAudienceForced) {
                    
                    if (!BuildConfig.DEBUG) {
                        Log.w(
                            CONFIG_TAG,
                            "FORCE_AUDIENCE_IN_RELEASE is ON — audience pinned to " +
                                "${if (DEBUG_AUDIENCE_MARKETING) "MARKETING" else "ORGANIC"}, " +
                                "real attribution ignored. Turn it off before shipping."
                        )
                    }
                    DEBUG_AUDIENCE_MARKETING
                } else {
                    !LightHouse.isOrganicUser(awaitReferrerMs = ATTRIBUTION_WAIT_MS)
                }
                if (BuildConfig.DEBUG) {
                    Log.d(CONFIG_TAG, "audience → ${if (isMarketingOn) "MARKETING" else "ORGANIC"}")
                }
                adsPreference.putBoolean("OnMaketing", isMarketingOn)

                val isSplitConfig = adsPreference.getBoolean("__cfg_audience_split")
                if (isSplitConfig) {
                    val raw = adsPreference.getString("GET_DATA_RAW", "")
                    if (!raw.isNullOrBlank()) runCatching {
                        ingestConfig(activity, audienceBlock(JSONObject(raw), isMarketingOn))
                    }
                }

                
                val countryEnableKey = "Iscountry_Counter"
                val countryListKey = "CountryList_Counter_NShow"
                if (BuildConfig.DEBUG) Log.d(
                    "LocationCheck",
                    "install=${if (isMarketingOn) "MARKETING" else "ORGANIC"} → using $countryEnableKey / $countryListKey"
                )

                if (adsPreference.getBoolean(countryEnableKey)) {
                    val blockedLocations = (adsPreference.getString(countryListKey, "") ?: "")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    
                    val blocksEveryone =
                        blockedLocations.any { it.equals(COUNTRY_LIST_ALL, ignoreCase = true) }

                    location?.let { loc ->
                        if (BuildConfig.DEBUG) {
                            Log.d("LocationCheck", "=== Location Info ===")
                            Log.d("LocationCheck", "Country: ${loc.country}")
                            Log.d("LocationCheck", "Region: ${loc.regionName}")
                            Log.d("LocationCheck", "City: ${loc.city}")
                        }

                        adsPreference.userCountry = loc.country.orEmpty()
                        adsPreference.userRegion = loc.regionName.orEmpty()
                        adsPreference.userCity = loc.city.orEmpty()
                    }

                    val isAllowed = location?.let { loc ->
                        blockedLocations.any { blocked ->
                            blocked.equals(loc.country, ignoreCase = true) ||
                                blocked.equals(loc.regionName, ignoreCase = true) ||
                                blocked.equals(loc.city, ignoreCase = true)
                        }
                    } ?: false

                    
                    adsPreference.isNShowLocation = blocksEveryone || isAllowed

                    when {
                        blocksEveryone -> {
                            if (BuildConfig.DEBUG) Log.d(
                                "LocationCheck",
                                "\uD83C\uDF0D $countryListKey=\"$COUNTRY_LIST_ALL\" → every location blocked, HD_VBC_Show=false"
                            )
                            adsPreference.putBoolean("HD_VBC_Show", false)
                        }

                        location == null ->
                            if (BuildConfig.DEBUG) Log.w("LocationCheck", "⚠️ Location not available")

                        isAllowed -> {
                            if (BuildConfig.DEBUG) Log.d(
                                "LocationCheck", "✅ Location IN list ($countryListKey) → HD_VBC_Show=false (real ads)"
                            )
                            adsPreference.putBoolean("HD_VBC_Show", false)
                        }

                        else -> if (BuildConfig.DEBUG) Log.d(
                            "LocationCheck", "❌ Location NOT in list ($countryListKey) → HD_VBC_Show unchanged"
                        )
                    }
                } else {
                    
                    adsPreference.isNShowLocation = false
                    if (BuildConfig.DEBUG) Log.d("LocationCheck", "Country check is disabled in preferences")
                }

                if (isMarketingOn && !isSplitConfig) {
                    with(adsPreference) {
                        putInt("InterCounter", getInt("MarketInterCounter"))
                        putInt("InterBackCounter", getInt("MarketBackCounter"))
                        putInt("MidNativeCounter", getInt("MarketNativeCounter"))
                        putInt("NativeCounter", getInt("MarketNativeCounter"))
                        putInt("BannerCounter", getInt("MarketBannerCounter"))
                        putInt("AppopenCounter", getInt("MarketAppopenCounter"))
                        putBoolean("IsBack", true)
                        putString("DirectLink", getString("MarketLink"))
                        putBoolean("is_intro", true)
                    }
                }

                val savedMarketingStr = adsPreference.getString("NativeTheme_marketing", "{}")
                val savedDefaultStr = adsPreference.getString("NativeTheme_default", "{}")

                val marketingObj = JSONObject(savedMarketingStr)
                val defaultObj = JSONObject(savedDefaultStr)

                
                val themeSource = when {
                    !isMarketingOn -> defaultObj
                    marketingObj.length() > 0 -> marketingObj
                    else -> {
                        Log.w(CONFIG_TAG, "no NativeTheme.marketing block — paid ads fall back to default")
                        defaultObj
                    }
                }
                val modeKey = resolveInlineThemeKey(activity)

                val themeJson = themeSource.optJSONObject(modeKey)

                themeJson?.let { theme ->
                    adsPreference.putString("NativebtnColor", theme.optString("btnColor"))
                    adsPreference.putString("NativebtntxtColor", theme.optString("btnText"))
                    adsPreference.putString("NativeBgColor", theme.optString("bgColor"))
                    adsPreference.putString("NativetxtColor", theme.optString("textColor"))

                    if (BuildConfig.DEBUG) Log.d(
                        "NativeTheme",
                        "Applied ${if (isMarketingOn) "MARKETING" else "DEFAULT"} $modeKey theme"
                    )
                }

                launch {

                    val isAdsOn = adsPreference.getBoolean("IsAdsON")

                    withContext(Dispatchers.Main) {

                        if (isAdsOn && isSplash == false) {
                            Log.d(APPOPEN_TAG, "isSplash=false → BS Native path (splash AppOpen is NOT attempted here)")
                            launch(Dispatchers.Main) {
                                SheetInlineAds().sheetFetchNativeAds(activity)
                                onGetData?.onSuccess()
                            }
                            return@withContext
                        }

                        primeSplashPermissions(activity) {
                            if (!isAdsOn) {

                                Log.w(APPOPEN_TAG, "IsAdsON=false → ads disabled, no AppOpen, continuing to app")
                                onGetData?.onSuccess()
                            } else {
                                if (isGoogleAdsEnabled) {
                                    InlinePromo().fetchNativeAds(activity)
                                    InlinePromoStrip().fetchNativeBannerAds(activity)
                                    FlowInterstitial().fetchInterstitial(activity)
                                    BackInterstitial().fetchBackInterstitial(activity)
                                }

                                val isSplashAdsEnabled = adsPreference.getBoolean("is_splash_ads")
                                Log.d(
                                    APPOPEN_TAG,
                                    "gate → IsAdsON=$isAdsOn, isSplash=$isSplash, is_splash_ads=$isSplashAdsEnabled, " +
                                        "IsAdType=${adsPreference.getString("IsAdType")}, appopenId=${adsPreference.getString("googleAppopen")}"
                                )
                                if (!isSplashAdsEnabled) {

                                    Log.w(APPOPEN_TAG, "is_splash_ads=false → skipping splash ad entirely, continuing to app")
                                    onGetData?.onSuccess()
                                } else {

                                    warmAds(adsPreference, activity) {
                                        renderPreloadedAd(activity, adsPreference) {
                                            onGetData?.onSuccess()
                                        }
                                    }
                                }
                            }
                        }

                    }
                }

            }
        }
    }

    private fun requestUserPermissions(
        @Suppress("UNUSED_PARAMETER") hdVbcShow: Boolean, onContinue: () -> Unit
    ) {
        onContinue()
    }

    private fun primeSplashPermissions(activity: Activity, onDone: () -> Unit) {

        runCatching { PermitSource.reload() }
        val screen = activity::class.java.simpleName
        val prime: (String, () -> Unit) -> Unit = { key, next ->
            if (targetsScreen(key, screen)) PermitEngine.request(activity, key) { next() }
            else next()
        }
        prime("notification") {
            prime("phone_state") {
                onDone()
            }
        }
    }

    private fun targetsScreen(key: String, screen: String): Boolean {
        val rule = PermitSource.rules().firstOrNull { it.key == key } ?: return true
        val targets = rule.activities.any { ScreenGlob.refersTo(it, screen) }
        if (!targets) Log.d("PermitEngine", "'$key' not configured for $screen — skipped on splash")
        return targets
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun warmAds(adsPreference: PromoVault, activity: Activity, onComplete: () -> Unit) {
        bindCustomTabs(activity)
        val adType = PromoKind.fromString(adsPreference.getString("IsAdType"))
        Log.d(APPOPEN_TAG, "preload() → IsAdType=$adType, googleAdsEnabled=$isGoogleAdsEnabled")
        when (adType) {
            PromoKind.GOOGLE -> {
                if (isGoogleAdsEnabled) {
                    val showInterstitialOnSplash = adsPreference.getBoolean("is_splash_inter_show")
                    if (showInterstitialOnSplash) {
                        Log.d(APPOPEN_TAG, "preload → is_splash_inter_show=true → loading splash INTERSTITIAL instead of AppOpen")
                        loadGoogleInterstitialWithFallback(activity, adsPreference, onComplete)
                    } else {
                        Log.d(APPOPEN_TAG, "preload → is_splash_inter_show=false → loading APPOPEN")
                        loadAppOpenAdWithFallback(activity, adsPreference, onComplete)
                    }
                } else {
                    Log.w(APPOPEN_TAG, "preload → Google ads disabled (init failed) → Facebook fallback")
                    loadFacebookFallback(activity, adsPreference, onComplete)
                }
            }

            PromoKind.FACEBOOK -> {
                if (adsPreference.getBoolean("IsFail_FB")) {
                    val fbId = adsPreference.getString("faceB_InterAds")!!
                    fetchFbInterstitial(
                        activity,
                        fbId,
                        onLoaded = { onComplete() },
                        onFailed = { onComplete() },
                        onDismissed = {
                            onComplete()
                        }
                    )
                } else {
                    onComplete()
                }
            }

            PromoKind.CUSTOM, PromoKind.UNKNOWN -> onComplete()
        }
    }

    fun renderPreloadedAd(activity: Activity, adsPreference: PromoVault, onDismissed: () -> Unit) {
        when (PromoKind.fromString(adsPreference.getString("IsAdType"))) {
            PromoKind.GOOGLE -> {
                Log.d(APPOPEN_TAG, "showPreloaded() → appOpenReady=${appOpenAd != null}, interstitialReady=${interstitialAd != null}")
                if (isGoogleAdsEnabled && (appOpenAd != null || interstitialAd != null)) {
                    if (appOpenAd != null) {
                        renderAppOpenAd(activity) { onDismissed() }
                    } else if (interstitialAd != null) {
                        renderGoogleInterstitial(activity) {
                            onDismissed()
                        }
                    }
                } else {
                    if (adsPreference.getBoolean("IsFail_FB")) {
                        val fbId = adsPreference.getString("faceB_InterAds")
                        if (!fbId.isNullOrEmpty() && fbInterstitial != null) {
                            renderFbInterstitial { onDismissed() }
                        } else if (adsPreference.getBoolean("IsCustomADS")) {

                            launchCustomAdLink(
                                activity, adsPreference.getString("DirectLink")!!
                            ) {
                                onDismissed()
                            }
                        } else {
                            onDismissed()
                        }
                    } else if (adsPreference.getBoolean("IsCustomADS")) {

                        launchCustomAdLink(
                            activity, adsPreference.getString("DirectLink")!!
                        ) {
                            onDismissed()
                        }
                    } else {
                        onDismissed()
                    }
                }
            }

            PromoKind.FACEBOOK -> {
                if (fbInterstitial != null) renderFbInterstitial { onDismissed() }
                else onDismissed()
            }

            PromoKind.CUSTOM, PromoKind.UNKNOWN -> {
                if (adsPreference.getBoolean("IsCustomADS")) {
                    launchCustomAdLink(
                        activity, adsPreference.getString("DirectLink")!!
                    ) {
                        onDismissed()
                    }
                } else {
                    onDismissed()
                }
            }
        }
    }

    private var appOpenAd: AppOpenAd? = null
    private fun loadGoogleInterstitialWithFallback(
        activity: Activity, adsPreference: PromoVault, onComplete: () -> Unit
    ) {
        val googleId = adsPreference.getString("googleS_Inter") ?: run { onComplete(); return }
        fetchGoogleInterstitial(activity, googleId, onLoaded = { onComplete() }, onFailed = {
            loadFacebookFallback(activity, adsPreference, onComplete)
        })
    }

    private fun loadAppOpenAdWithFallback(
        activity: Activity, adsPreference: PromoVault, onComplete: () -> Unit
    ) {
        val appOpenId = adsPreference.getString("googleAppopen")
        if (appOpenId.isNullOrBlank()) {
            Log.w(APPOPEN_TAG, "no/blank 'googleAppopen' unit id in Remote Config → skipping AppOpen, continuing")
            onComplete(); return
        }
        fetchAppOpenAd(activity, appOpenId, onLoaded = { onComplete() }, onFailed = {
            Log.w(APPOPEN_TAG, "AppOpen load failed → falling back to Google interstitial")
            loadGoogleInterstitialWithFallback(activity, adsPreference, onComplete)
        })
    }

    private fun loadFacebookFallback(
        activity: Activity, adsPreference: PromoVault, onComplete: () -> Unit
    ) {
        if (adsPreference.getBoolean("IsFail_FB")) {
            val fbId = adsPreference.getString("faceB_InterAds")
            if (!fbId.isNullOrEmpty()) {
                fetchFbInterstitial(
                    activity,
                    fbId,
                    onLoaded = { onComplete() },
                    onFailed = { onComplete() },
                    onDismissed = { onComplete() })
            } else onComplete()
        } else onComplete()
    }

    fun fetchAppOpenAd(
        activity: Activity,
        adUnitId: String,
        onLoaded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        Log.d(APPOPEN_TAG, "load() → requesting AppOpen, id=$adUnitId")
        AppOpenAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {

                    Log.e(
                        APPOPEN_TAG,
                        "load FAILED → code=${loadAdError.code}, domain=${loadAdError.domain}, " +
                            "message=${loadAdError.message}"
                    )
                    appOpenAd = null
                    OpenPromoRegistry.isShowingAd = false
                    onFailed?.invoke()
                }

                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(APPOPEN_TAG, "load SUCCESS → AppOpen ad is ready to show")
                    appOpenAd = ad
                    onLoaded?.invoke()
                }
            })
    }

    fun renderAppOpenAd(activity: Activity, onDismissed: (() -> Unit)? = null) {
        appOpenAd?.let { ad ->
            Log.d(APPOPEN_TAG, "show() → displaying AppOpen ad")
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    onDismissed?.invoke()
                    OpenPromoRegistry.isShowingAd = false
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    onDismissed?.invoke()
                }

                override fun onAdShowedFullScreenContent() {
                    OpenPromoRegistry.isShowingAd = true
                }
            }

            activity.trackEvent("app_open_loaded")

            if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(activity)

            appOpenAd?.setOnPaidEventListener {
                PromoRevenueGauge.reportPaidEvent(activity, it)
            }
            ad.show(activity)
        } ?: run {

            Log.w(APPOPEN_TAG, "show() → AppOpen ad is null (not loaded in time) → skipping show")
            onDismissed?.invoke()
        }
    }

    private var interstitialAd: InterstitialAd? = null

    fun fetchGoogleInterstitial(
        activity: Activity,
        adUnitId: String,
        onLoaded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        InterstitialAd.load(
            activity, adUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    onFailed?.invoke()
                }
            })
    }

    fun renderGoogleInterstitial(activity: Activity, onDismissed: (() -> Unit)? = null) {
        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    onDismissed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    onDismissed?.invoke()
                }

                override fun onAdShowedFullScreenContent() {
                }
            }

            activity.trackEvent("interstitial_splash_loaded")

            if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(activity)

            interstitialAd?.setOnPaidEventListener {
                PromoRevenueGauge.reportPaidEvent(activity, it)
            }
            ad.show(activity)
        } ?: run { onDismissed?.invoke() }
    }

    private var fbInterstitial: com.facebook.ads.InterstitialAd? = null

    fun fetchFbInterstitial(
        activity: Activity,
        adUnitId: String,
        onLoaded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
        onDismissed: (() -> Unit)? = null
    ) {
        fbInterstitial = com.facebook.ads.InterstitialAd(activity, adUnitId)
        fbInterstitial?.loadAd(
            fbInterstitial!!.buildLoadAdConfig().withAdListener(object : InterstitialAdListener {
                override fun onInterstitialDisplayed(ad: Ad?) {}
                override fun onInterstitialDismissed(ad: Ad?) {
                    onDismissed?.invoke()
                }

                override fun onError(ad: Ad?, adError: com.facebook.ads.AdError?) {
                    onFailed?.invoke()
                }

                override fun onAdLoaded(ad: Ad?) {
                    onLoaded?.invoke()
                }

                override fun onAdClicked(ad: Ad?) {}
                override fun onLoggingImpression(ad: Ad?) {}
            }).build()
        )
    }

    fun renderFbInterstitial(onDismissed: (() -> Unit)? = null) {
        val ad = fbInterstitial

        if (ad != null && ad.isAdLoaded) {
            ad.show()
        } else {
            onDismissed?.invoke()
        }
    }

    private fun bindCustomTabs(activity: Activity) {
        if (customTabsClient != null) return

        CustomTabsClient.bindCustomTabsService(
            activity, "com.android.chrome", object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(
                    name: ComponentName, client: CustomTabsClient
                ) {
                    customTabsClient = client
                    customTabsSession = client.newSession(object : CustomTabsCallback() {
                        override fun onNavigationEvent(event: Int, extras: Bundle?) {
                            if ((event == CustomTabsCallback.TAB_HIDDEN || event == CustomTabsCallback.NAVIGATION_ABORTED) && isCustomTabOpened) {
                                handleCustomTabClose()
                            }
                        }
                    })
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    customTabsClient = null
                    customTabsSession = null
                }
            })
    }

    private fun handleCustomTabClose() {
        if (isFinishing || isDestroyed) return
        if (!isCloseHandled) {
            isCloseHandled = true
            isCustomTabOpened = false
            onCustomTabClosed?.invoke()
        }
    }

    override fun onDestroy() {
        try {
            customTabsClient = null
            customTabsSession = null
            onCustomTabClosed = null
            activity = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun launchCustomAdLink(activity: Activity, url: String, onClosed: () -> Unit) {
        bindCustomTabs(activity)

        isCustomTabOpened = true
        isCloseHandled = false
        onCustomTabClosed = { onClosed() }

        val customTabsIntent = CustomTabsIntent.Builder(customTabsSession).setShowTitle(true)
            .setToolbarColor(ContextCompat.getColor(activity, R.color.black)).build()

        try {
            customTabsIntent.launchUrl(activity, Uri.parse(url))
        } catch (e: Exception) {

            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            handleCustomTabClose()
        }
    }

    override fun onResume() {
        super.onResume()
        handleCustomTabClose()
    }

    fun readInstallReferrer(referrerClient: InstallReferrerClient) {
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        var response: ReferrerDetails? = null
                        try {
                            response = referrerClient.installReferrer

                            val ref = response.installReferrer
                            activity?.let {
                                PromoVault.getInstance(it).putString("FinalString", ref)
                            }

                            onReferrerSettled()

                            activity?.getPreferences(MODE_PRIVATE)?.edit()?.apply {
                                putBoolean("isReferrerDone", true)
                                apply()
                            }
                        } catch (e: RemoteException) {
                            onReferrerSettled()
                        } finally {
                            referrerClient.endConnection()
                        }

                    }

                    else -> onReferrerSettled()
                }
            }

            override fun onInstallReferrerServiceDisconnected() {

                onReferrerSettled()
            }
        })
    }

    private fun setApplication(fbAppId: String, fbClientToken: String) {
        FacebookSdk.setApplicationId(fbAppId)
        FacebookSdk.setClientToken(fbClientToken)
        activity?.let { FacebookSdk.sdkInitialize(it) }

        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.fullyInitialize()
        FacebookSdk.setAutoLogAppEventsEnabled(true)
        FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
        val logger = activity?.let { AppEventsLogger.newLogger(it) }
        logger?.applicationId
    }
}
