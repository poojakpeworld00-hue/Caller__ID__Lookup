package com.callerid.admesh.surface

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.PromoTallyRegistry.nativeBannerCounter
import com.callerid.admesh.engine.PromoRevenueGauge
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.TAG_EVENT
import com.callerid.admesh.engine.trackEvent
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.databinding.FacebookNativeBannerBinding
import com.callerid.number.lookup.home.databinding.GooglesmallnativeBinding
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdOptionsView
import com.facebook.ads.NativeAdListener
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

class InlinePromoStrip {
    companion object {
        private var nativeAdBanner: NativeAd? = null
    }

    fun fetchNativeBannerAds(activity: Activity) {
        val adsPref = PromoVault.getInstance(activity)
        if (!adsPref.getBoolean("IsAdsON")) return

        if (!adsPref.getBoolean("NativeBanner")) return

        when (PromoKind.fromString(adsPref.getString("IsAdType"))) {
            PromoKind.GOOGLE -> {
                val adUnitId = adsPref.getString("googleNative") ?: return

                val adLoader = AdLoader.Builder(activity, adUnitId).forNativeAd { ad ->
                    nativeAdBanner?.destroy()
                    nativeAdBanner = ad
                    try {
                        activity.trackEvent("native_banner_load")
                    } catch (e: Exception) {
                    }

                    Log.d("InlinePromoStrip", "Ad loaded successfully")
                }.withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("InlinePromoStrip", "Ad failed to load: ${error.message}")
                        nativeAdBanner = null

                        try {
                            activity.trackEvent("native_banner_fail")
                        } catch (e: Exception) {
                        }
                    }
                }).withNativeAdOptions(NativeAdOptions.Builder().build()).build()

                adLoader.loadAd(AdRequest.Builder().build())
            }

            PromoKind.FACEBOOK -> {

                Log.e(TAG_EVENT, "AdType FaceBook NOt Pre load Google Native")
                return
            }

            PromoKind.UNKNOWN, PromoKind.CUSTOM -> {

                Log.e(TAG_EVENT, "AdType Custom NOt Pre load Google Native")
                return
            }
        }

    }

    fun renderNativeBanner(
        context: Activity, layout: FrameLayout, shimmer: ShimmerFrameLayout? = null
    ) {
        Log.e("NativeAds", "Google Show: nativeAd")
        val adsPref = PromoVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context)
            || !adsPref.getBoolean("IsAdsON")
            || !adsPref.getBoolean("NativeBanner")
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            return
        }

        if (nativeBannerCounter < adsPref.getInt("MidNativeCounter")) {
            nativeBannerCounter += 1
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            return
        }

        nativeBannerCounter = 0

        layout.visible()
        shimmer?.startShimmer()
        shimmer?.isVisible = true

        when (PromoKind.fromString(adsPref.getString("IsAdType"))) {
            PromoKind.GOOGLE -> {

                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (nativeAdBanner != null) {
                            val binding = GooglesmallnativeBinding.inflate(context.layoutInflater)
                            bindGoogleNativeAd(nativeAdBanner!!, binding, context)

                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false

                            layout.addView(binding.root)
                            nativeAdBanner = null
                            fetchNativeBannerAds(context)
                            return@post
                        } else {

                            if (adsPref.getBoolean("IsFail_FB")) {
                                showFBNativeBannerFallback(context, layout)
                            } else {
                                layout.removeAllViews()
                                shimmer?.stopShimmer()
                                shimmer?.isVisible = false
                                InHouseRegistry().fetchHouseAd(
                                    context, layout, InHouseRegistry.CustomAdType.BANNER
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("InlinePromoStrip", "Google NativeBanner failed: ${e.message}")
                    }
                }
            }

            PromoKind.FACEBOOK -> {
                showFBNativeBannerFallback(context, layout)
            }

            PromoKind.UNKNOWN, PromoKind.CUSTOM -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                InHouseRegistry().fetchHouseAd(
                    context, layout, InHouseRegistry.CustomAdType.BANNER
                )
            }
        }
    }

    private fun bindGoogleNativeAd(
        nativeAd: NativeAd, binding: GooglesmallnativeBinding, context: Activity
    ) {

        context.trackEvent("native_banner_show_google")

        if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(context)

        nativeAd.setOnPaidEventListener {
            PromoRevenueGauge.reportPaidEvent(context, it)
        }

        binding.apply {
            mainNativeadView.headlineView = adHeadline
            mainNativeadView.bodyView = adBody
            mainNativeadView.callToActionView = adCallToAction
            mainNativeadView.iconView = adAppIcon

            (adHeadline as TextView).text = nativeAd.headline

            val bgColor = PromoVault.getInstance(context).getString("NativeBgColor")
            val btnColor = PromoVault.getInstance(context).getString("NativebtnColor")

            val txtColor =
                PromoVault.getInstance(context).getString("NativetxtColor") ?: "#000000"

            (binding.mainNativeadView.headlineView as TextView).apply {
                setTextColor(Color.parseColor(txtColor))
            }

            (binding.mainNativeadView.bodyView as TextView).apply {
                setTextColor(Color.parseColor(txtColor))
            }

            val btntxtColor =
                PromoVault.getInstance(context).getString("NativebtntxtColor") ?: "#000000"

            (adCallToAction as TextView).apply {
                setTextColor(Color.parseColor(btntxtColor))
            }

            mainNativeadView.backgroundTintList =
                ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))

            adCallToAction.backgroundTintList =
                ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))

            if (nativeAd.body != null) {
                adBody.visibility = View.VISIBLE
                (adBody as TextView).text = nativeAd.body
            } else {
                adBody.visibility = View.GONE
            }

            if (nativeAd.icon != null) {
                adAppIcon.visibility = View.VISIBLE
                (adAppIcon as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            } else {
                adAppIcon.visibility = View.GONE
            }

            if (nativeAd.callToAction != null) {
                adCallToAction.visibility = View.VISIBLE
                (adCallToAction as TextView).text = nativeAd.callToAction

            } else {
                adCallToAction.visibility = View.GONE
            }

            mainNativeadView.setNativeAd(nativeAd)
        }
    }

    fun parseColorOrNull(colorString: String?, defaultColor: String): Int {
        return try {
            if (!colorString.isNullOrBlank()) {
                Color.parseColor(colorString)
            } else {
                Color.parseColor(defaultColor)
            }
        } catch (e: IllegalArgumentException) {
            Color.parseColor(defaultColor)
        }
    }

    private fun showFBNativeBannerFallback(
        context: Activity, layout: FrameLayout, shimmer: ShimmerFrameLayout? = null
    ) {
        val adsPref = PromoVault.getInstance(context)
        val fbId = adsPref.getString("faceB_NativeBannerAds")

        if (fbId.isNullOrEmpty()) {
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            InHouseRegistry().fetchHouseAd(
                context, layout, InHouseRegistry.CustomAdType.BANNER
            )
            return
        }

        val fbNative = com.facebook.ads.NativeAd(context, fbId)
        fbNative.loadAd(
            fbNative.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                override fun onMediaDownloaded(ad: Ad?) {
                    shimmer?.stopShimmer()
                    shimmer?.isVisible = false
                    layout.removeAllViews()
                    bindFbNativeBanner(fbNative, layout, context)
                    context.trackEvent("native_banner_fb")
                }

                override fun onError(ad: Ad?, adError: AdError?) {
                    shimmer?.stopShimmer()
                    shimmer?.isVisible = false
                    Log.e("NativeAds", "FB MidNative failed: ${adError?.errorMessage}")
                    InHouseRegistry().fetchHouseAd(
                        context, layout, InHouseRegistry.CustomAdType.BANNER
                    )
                }

                override fun onAdLoaded(ad: Ad?) {
                    if (fbNative !== ad) return
                    fbNative.downloadMedia()
                }

                override fun onAdClicked(ad: Ad?) {}
                override fun onLoggingImpression(ad: Ad?) {}
            }).build()
        )
    }

    fun bindFbNativeBanner(
        nativeAd: com.facebook.ads.NativeAd,
        viewGroup: ViewGroup,
        activity: Activity,
        adSize: String? = null
    ) {

        viewGroup.isVisible = true

        nativeAd.unregisterView()

        val binding =
            FacebookNativeBannerBinding.inflate(LayoutInflater.from(activity), viewGroup, false)

        viewGroup.removeAllViews()
        viewGroup.addView(binding.root)

        val adOptionsView = AdOptionsView(activity, nativeAd, binding.nativview)
        binding.adChoicesContainer.removeAllViews()
        binding.adChoicesContainer.addView(adOptionsView, 0)

        binding.nativeAdTitle.text = nativeAd.advertiserName
        binding.nativeAdSocialContext.text = nativeAd.adSocialContext
        binding.nativeAdSponsoredLabel.text = nativeAd.sponsoredTranslation

        val bgColor = PromoVault.getInstance(activity).getString("NativeBgColor")
        val btnColor = PromoVault.getInstance(activity).getString("NativebtnColor")
        val txtColor = PromoVault.getInstance(activity).getString("NativetxtColor") ?: "#000000"
        val btntxtColor =
            PromoVault.getInstance(activity).getString("NativebtntxtColor") ?: "#000000"

        binding.nativeAdTitle.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdSocialContext.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdSponsoredLabel.setTextColor(Color.parseColor(txtColor))

        binding.nativview.backgroundTintList =
            ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))

        binding.nativeAdCallToAction.backgroundTintList =
            ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))
        (binding.nativeAdCallToAction as TextView).apply {
            setTextColor(Color.parseColor(btntxtColor))
        }

        if (nativeAd.hasCallToAction()) {
            binding.nativeAdCallToAction.text = nativeAd.adCallToAction
            binding.nativeAdCallToAction.isVisible = true
        } else {
            binding.nativeAdCallToAction.isVisible = false
        }

        val clickableViews = listOf(binding.nativeAdTitle, binding.nativeAdCallToAction)

        nativeAd.registerViewForInteraction(
            binding.root, binding.nativeIconView, clickableViews
        )
    }

}
