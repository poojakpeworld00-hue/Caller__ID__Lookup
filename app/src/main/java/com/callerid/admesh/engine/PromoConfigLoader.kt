package com.callerid.admesh.engine

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.callerid.admesh.surface.InHouseRegistry
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.kit.AppPrefs
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_DARK
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_LIGHT
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_SYSTEM
import org.json.JSONObject

object PromoConfigLoader {

    private const val CONFIG_TAG = "AdConfig"

    data class FacebookKeys(val appId: String, val clientToken: String) {
        val usable: Boolean get() = appId.isNotEmpty() && clientToken.isNotEmpty()
    }

    fun absorb(context: Context, root: JSONObject): FacebookKeys {
        val adsPref = PromoVault.getInstance(context)
        adsPref.update {

            listOf(
                "IsAdsON", "IsFail_FB", "isLoaderForFB", "IsCustomADS", "IsBack",
                "NativeBanner", "BannerAds", "In_App_Update_Show", "In_App_Update_Force_Show",
                "Iscountry_Counter", "HD_VBC_Show",
                "HD_VBC_Native", "is_preload_ads",
                "is_splash_inter_show", "is_splash_ads", "InterAds", "AppopenAds",
                "NativeAd", "is_rateus", "is_share", "Perm_Sheet_Show",
                "screen_wise_ad", "screen_wise_default"
            ).forEach { key -> if (root.has(key)) putBoolean(key, root.optBoolean(key, false)) }

            listOf(
                "IsAdType", "In_App_Update_Link", "CountryList_Counter_NShow",
                "PrivacyPolicy", "TermLink",
                "DirectLink", "MarketLink", "HD_VBC_Native_ID", "HD_VBC_Banner_ID",
                "googleS_Inter", "googleBackInter", "googleInter", "googleAppopen",
                "googleNative", "googleBanner", "googleRewarded", "faceB_InterAds",
                "faceB_NativeAds", "faceB_NativeBannerAds", "faceB_BannerAds",
                "NativeTheme", "HD_VBC_Type", "NativeBgColor", "NativebtnColor",
                "NativetxtColor", "NativebtntxtColor", "Perm_Sheet_Mode",

                "api_base_url",

                "intro_display", "ScreenAds", "launcher_ads",
                // The :launcher module's own config, already audience-resolved by this block.
                "launcher_config"
            ).forEach { key -> if (root.has(key)) putString(key, root.optString(key, "")) }

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
                
                "Config_Sync_Hrs"
            ).forEach { key -> if (root.has(key)) putInt(key, root.optInt(key, 0)) }

            applyInlineTheme(context, root)

            val customAdsArray = root.optJSONArray("custom_ads")
            if (customAdsArray != null) {
                putString("CUSTOM_ADS", customAdsArray.toString())
                InHouseRegistry.clearCache()
            }
        }

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

    fun inlineThemeKey(context: Context): String {

        return when (AppPrefs.selectedTheme(context)) {
            THEME_DARK -> {
                "NativeDark"
            }

            THEME_LIGHT -> {
                "NativeLight"
            }

            THEME_SYSTEM -> {
                val isSystemDark =
                    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isSystemDark) "NativeDark" else "NativeLight"
            }

            else -> {
                "NativeLight"
            }
        }
    }

    fun applyInlineTheme(
        context: Context, response: JSONObject
    ) {
        val adsPreference = PromoVault.getInstance(context)

        val nativeThemeRoot = response.optJSONObject("NativeTheme") ?: return

        val modeKey = inlineThemeKey(context)

        nativeThemeRoot?.let {
            val marketingObj = it.optJSONObject("marketing")
            val defaultObj = it.optJSONObject("default")

            val marketingStr = marketingObj?.toString() ?: "{}"
            val defaultStr = defaultObj?.toString() ?: "{}"

            adsPreference.putString("NativeTheme_marketing", marketingStr)
            adsPreference.putString("NativeTheme_default", defaultStr)

            
            val onMarketing = adsPreference.getBoolean("OnMaketing")
            val audienceObj = if (onMarketing) marketingObj ?: defaultObj else defaultObj
            val themeJson = audienceObj?.optJSONObject(modeKey)

            if (BuildConfig.DEBUG) Log.d(
                CONFIG_TAG,
                "native theme ← ${if (onMarketing) "marketing" else "default"}" +
                    (if (onMarketing && marketingObj == null) " (absent, fell back to default)" else "") +
                    " / $modeKey"
            )

            if (themeJson != null) {
                adsPreference.putString("NativebtnColor", themeJson.optString("btnColor"))
                adsPreference.putString("NativebtntxtColor", themeJson.optString("btnText"))
                adsPreference.putString("NativeBgColor", themeJson.optString("bgColor"))
                adsPreference.putString("NativetxtColor", themeJson.optString("textColor"))
            }
        }
    }
}
