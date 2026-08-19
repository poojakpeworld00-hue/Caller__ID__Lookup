package com.callerid.admesh.engine

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.callerid.admesh.surface.StripWatcher
import com.callerid.admesh.surface.StripPromo
import com.callerid.admesh.surface.StripScale
import com.callerid.admesh.surface.StripKind
import com.callerid.admesh.surface.InlinePromoStrip
import com.callerid.number.lookup.home.BuildConfig
import org.json.JSONObject
import com.callerid.number.lookup.home.permit.ScreenGlob

object PerScreenPromo {

    private const val TAG = "PerScreenPromo"

    data class ScreenAd(
        val show: Boolean,
        val bannerId: String,
        val bannerType: String,
        val nativeId: String,
        val nativeType: String
    )

    fun resolve(context: Context, screenName: String): ScreenAd {
        val pref = PromoVault.getInstance(context)
        val globalBanner = pref.getString("googleBanner").orEmpty()
        val globalNative = pref.getString("googleNative").orEmpty()
        val screenWise = pref.getBoolean("screen_wise_ad")
        val useDefault = pref.getBoolean("screen_wise_default")

        val root = runCatching { JSONObject(pref.getString("ScreenAds", "{}") ?: "{}") }.getOrNull()

        val entry = when {
            !screenWise -> null
            root == null -> null
            useDefault -> root.optJSONObject("default")
            else -> root.screenEntry(screenName) ?: root.optJSONObject("default")
        }

        val showEntry = root?.let { it.screenEntry(screenName) ?: it.optJSONObject("default") }
        val globalShow = showEntry?.optBoolean("show", true) ?: true

        val result: ScreenAd
        val source: String
        var bannerFromEntry = false
        var nativeFromEntry = false

        if (entry == null) {
            result = ScreenAd(globalShow, globalBanner, "adaptive", globalNative, "mid")
            source = when {
                !screenWise -> "GLOBAL ids (screen_wise_ad=false), show=$globalShow from ScreenAds"
                root == null -> "GLOBAL (ScreenAds JSON missing/invalid)"
                else -> "GLOBAL (no 'default' entry)"
            }
        } else {
            val bannerRaw = entry.optString("banner")
            val nativeRaw = entry.optString("native")
            bannerFromEntry = bannerRaw.isNotBlank()
            nativeFromEntry = nativeRaw.isNotBlank()
            result = ScreenAd(
                show = entry.optBoolean("show", true),
                bannerId = bannerRaw.ifBlank { globalBanner },
                bannerType = entry.optString("bannerType").ifBlank { "adaptive" },
                nativeId = nativeRaw.ifBlank { globalNative },
                nativeType = entry.optString("nativeType").ifBlank { "mid" }
            )
            source = when {
                useDefault -> "ScreenAds.default"
                root!!.has(screenName) -> "ScreenAds[$screenName]"
                else -> "ScreenAds.default (no entry for $screenName)"
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "resolve($screenName)  screen_wise_ad=$screenWise  screen_wise_default=$useDefault")
            Log.d(TAG, "   source = $source   |   show = ${result.show}")
            Log.d(
                TAG,
                "   banner = ${result.bannerId}  type=${result.bannerType}  " +
                        if (bannerFromEntry) "(from entry)" else "(inherited googleBanner)"
            )
            Log.d(
                TAG,
                "   native = ${result.nativeId}  type=${result.nativeType}  " +
                        if (nativeFromEntry) "(from entry)" else "(inherited googleNative)"
            )
        }
        return result
    }

    fun inlineAdUnitId(context: Context): String {
        val pref = PromoVault.getInstance(context)
        val globalNative = pref.getString("googleNative").orEmpty()
        val screenWise = pref.getBoolean("screen_wise_ad")

        val result: String
        val source: String
        if (!screenWise) {
            result = globalNative
            source = "googleNative (screen_wise_ad=false)"
        } else {
            val root = runCatching {
                JSONObject(pref.getString("ScreenAds", "{}") ?: "{}")
            }.getOrNull()
            val entryNative = root?.optJSONObject("default")?.optString("native").orEmpty()
            if (entryNative.isNotBlank()) {
                result = entryNative
                source = "ScreenAds.default.native"
            } else {
                result = globalNative
                source = "googleNative (default.native blank/missing)"
            }
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "inlineAdUnitId() = $result   <- $source")
        return result
    }

    fun renderAd(
        screenName: String,
        activity: Activity,
        container: FrameLayout,
        shimmer: ShimmerFrameLayout? = null
    ) {
        val pref = PromoVault.getInstance(activity)
        val resolved = resolve(activity, screenName)
        val adsOn = pref.getBoolean("IsAdsON")

        if (!adsOn || !resolved.show) {
            container.removeAllViews()
            container.visibility = View.GONE
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "renderAd($screenName) -> HIDDEN  IsAdsON=$adsOn  show=${resolved.show}")
            }
            return
        }

        val (size, collapsible) = when (resolved.bannerType.lowercase()) {
            "inline" -> StripScale.INLINE to false
            "normal" -> StripScale.NORMAL to false
            "collapsible" -> StripScale.ADAPTIVE to true
            else -> StripScale.ADAPTIVE to false
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "renderAd($screenName) -> LOAD banner  id=${resolved.bannerId}  " +
                        "bannerType=${resolved.bannerType}  size=$size  collapsible=$collapsible"
            )
        }

        StripPromo().renderBanner(
            activity = activity,
            container = container,
            type = StripKind.AUTO,
            size = size,
            isCollapsable = collapsible,
            shimmer = shimmer,
            customAdUnitId = resolved.bannerId.takeIf { it.isNotBlank() },
            disableInternalFallback = true,
            observer = object : StripWatcher {
                override fun onAdFailed() {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "renderAd($screenName) -> banner failed, fallback to native banner")
                    }
                    InlinePromoStrip().renderNativeBanner(activity, container, shimmer)
                }
            }
        )
    }
}

private fun JSONObject.screenEntry(screenName: String): JSONObject? =
    ScreenGlob.keyFor(keys(), screenName)?.let { optJSONObject(it) }
