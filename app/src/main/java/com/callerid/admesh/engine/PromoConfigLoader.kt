package com.callerid.admesh.engine

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.callerid.admesh.surface.InHouseRegistry
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.kit.NATIVE_THEME_KEY
import com.callerid.number.lookup.home.kit.applyNativeAdTheme
import com.callerid.number.lookup.home.kit.nativeThemeMode
import org.json.JSONObject

object PromoConfigLoader {

    private const val CONFIG_TAG = "AdConfig"

    /** Run on the main thread after every successful live ingest (re-applies the launcher config). */
    @Volatile
    var onApplied: (() -> Unit)? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun notifyApplied() {
        onApplied?.let { listener -> mainHandler.post { runCatching(listener) } }
    }

    data class FacebookKeys(val appId: String, val clientToken: String) {
        val usable: Boolean get() = appId.isNotEmpty() && clientToken.isNotEmpty()
    }

    fun absorb(context: Context, root: JSONObject): FacebookKeys {
        val adsPref = PromoVault.getInstance(context)
        adsPref.update {

            listOf(
                "IsAdsON", "IsFail_FB", "isLoaderForFB", "inter_loader", "IsCustomADS", "IsBack",
                "NativeBanner", "BannerAds", "In_App_Update_Show", "In_App_Update_Force_Show",
                "Iscountry_Counter", "HD_VBC_Show",
                "HD_VBC_Native", "is_preload_ads",
                "is_splash_inter_show", "is_splash_ads", "InterAds", "AppopenAds",
                "NativeAd", "is_rateus", "is_share", "Perm_Sheet_Show",
                "screen_wise_ad", "screen_wise_default",
                // First-session Recents → Play Store home (RecentAdWatcher).
                "recent_playstore"
            ).forEach { key -> if (root.has(key)) putBoolean(key, root.optBoolean(key, false)) }

            // QRScanner's names for the same two switches, so its config pastes across unchanged.
            // The CallerID name wins when both are present.
            mapOf("BannerAdPresenter" to "BannerAds", "NativeBannerPresenter" to "NativeBanner")
                .forEach { (alias, key) ->
                    if (root.has(alias) && !root.has(key)) putBoolean(key, root.optBoolean(alias, false))
                }

            listOf(
                "IsAdType", "In_App_Update_Link", "CountryList_Counter_NShow",
                "PrivacyPolicy", "TermLink",
                "DirectLink", "MarketLink", "HD_VBC_Native_ID", "HD_VBC_Banner_ID",
                "googleS_Inter", "googleBackInter", "googleInter", "googleAppopen",
                "googleNative", "googleBanner", "googleRewarded", "faceB_InterAds",
                "faceB_NativeAds", "faceB_NativeBannerAds", "faceB_BannerAds",
                // NativeTheme is resolved by storeInlineTheme; the Native*Color keys derive from it.
                "HD_VBC_Type", "Perm_Sheet_Mode",

                "contacts_base_url",

                "intro_display", "ScreenAds", "launcher_ads",
                // App Home's game-quiz icon (QuizIcon).
                "quiz_icon",
                // The :launcher module's own config, already audience-resolved by this block.
                "launcher_config",
                // "app" | "launcher": where onboarding hands off (OnboardRouter.homeActivity).
                "onboarding_home",
                // Every placement's own ad settings, nested (LauncherPlacementAds).
                LauncherPlacementAds.PLACEMENTS_KEY
            ).forEach { key -> if (root.has(key)) putString(key, root.optString(key, "")) }

            // `link_open_in` is the readable name of `DirectLinkType`; DirectLinkOpener reads the flat pref.
            if (root.has("DirectLinkType")) putString("DirectLinkType", root.optString("DirectLinkType", ""))
            if (root.has("link_open_in")) putString("DirectLinkType", root.optString("link_open_in", ""))

            // The launcher's per-placement ad keys (`leftPanel_googleInter`, `drawer_link_first_then`,
            // …) and the link-first switches. Always stored as strings, whatever their JSON type, so
            // a key that is a boolean in one config and a string in the next never clashes in prefs.
            root.keys().forEach { key ->
                if (LauncherPlacementAds.isPlacementKey(key)) putString(key, root.optString(key, ""))
            }

            listOf(
                "InterCounter", "InterBackCounter", "MarketInterCounter", "MarketBackCounter",
                "NativeCounter", "MarketNativeCounter", "MidNativeCounter", "BannerCounter",
                "MarketBannerCounter", "MarketAppopenCounter", "AppopenCounter",
                "Perm_Sheet_Interval_Days", "HD_VBC_Hrs",

                "Config_Sync_Hrs",
                "recent_playstore_window_sec"
            ).forEach { key -> if (root.has(key)) putInt(key, root.optInt(key, 0)) }

            storeInlineTheme(this, adsPref, root)

            val customAdsArray = root.optJSONArray("custom_ads")
            if (customAdsArray != null) {
                putString("CUSTOM_ADS", customAdsArray.toString())
                InHouseRegistry.clearCache()
            }
        }
        // After the batch above is applied, so it reads the palette just stored.
        context.applyNativeAdTheme()

        val fbAppId = root.optString("FbAppId", "")
        val fbClientToken = root.optString("FbClientToken", "")

        if (BuildConfig.DEBUG) Log.d(
            CONFIG_TAG,
            "ingested → IsAdsON=${adsPref.getBoolean("IsAdsON")}, IsAdType=${adsPref.getString("IsAdType")}, " +
                "InterAds=${adsPref.getBoolean("InterAds")}, AppopenAds=${adsPref.getBoolean("AppopenAds")}, " +
                "NativeAd=${adsPref.getBoolean("NativeAd")}, BannerAds=${adsPref.getBoolean("StripPromo")}, " +
                "HD_VBC_Show=${adsPref.getBoolean("HD_VBC_Show")}, HD_VBC_Hrs=${adsPref.getInt("HD_VBC_Hrs")}, " +
                "screen_wise_ad=${adsPref.getBoolean("screen_wise_ad")}, " +
                "customAds=${root.optJSONArray("custom_ads")?.length() ?: 0}, " +
                "fbInit=${fbAppId.isNotEmpty() && fbClientToken.isNotEmpty()}, " +
                "appOpenId=${adsPref.getString("googleAppopen")}"
        )

        return FacebookKeys(fbAppId, fbClientToken)
    }

    fun audienceBlock(response: JSONObject, isMarketing: Boolean): JSONObject {
        val preferred = if (isMarketing) "marketing" else "organic"
        val fallback = if (isMarketing) "organic" else "marketing"
        response.optJSONObject(preferred)?.let {
            if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "audienceBlock → using '$preferred' segment")
            return it
        }
        response.optJSONObject(fallback)?.let {
            if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "audienceBlock → '$preferred' missing, fell back to '$fallback' segment")
            return it
        }
        if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "audienceBlock → no marketing/organic wrapper, using flat config")
        return response
    }

    fun inlineThemeKey(context: Context): String = context.nativeThemeMode()

    /**
     * Stores the audience block's `NativeTheme` ({ "NativeLight": {…}, "NativeDark": {…} }) under
     * [NATIVE_THEME_KEY]. The old `{ "marketing": {…}, "default": {…} }` wrapper is reduced to
     * the same shape: marketing users take `marketing` (else `default`), organic users `default`.
     */
    private fun storeInlineTheme(editor: SharedPreferences.Editor, adsPref: PromoVault, root: JSONObject) {
        val theme = root.optJSONObject(NATIVE_THEME_KEY) ?: return
        val onMarketing = adsPref.getBoolean("OnMaketing")
        val palette = if (theme.has("NativeLight") || theme.has("NativeDark")) {
            theme
        } else {
            val legacy = if (onMarketing) theme.optJSONObject("marketing") ?: theme.optJSONObject("default")
            else theme.optJSONObject("default")
            if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "native theme: legacy marketing/default wrapper")
            legacy ?: return
        }
        editor.putString(NATIVE_THEME_KEY, palette.toString())
        editor.remove("NativeTheme_marketing")
        editor.remove("NativeTheme_default")
        if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "native theme ← ${if (onMarketing) "marketing" else "organic"} palette")
    }
}
