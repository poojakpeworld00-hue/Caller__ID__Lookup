package com.callerid.admesh.surface

import android.R.attr.visibility
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.model.PromoKind.*
import com.callerid.admesh.engine.PromoTallyRegistry.nativeCounter
import com.callerid.admesh.engine.PromoRevenueGauge
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.PerScreenPromo
import com.callerid.admesh.engine.trackEvent
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.databinding.FbMidNativeBinding
import com.callerid.number.lookup.home.databinding.FbNativeBinding
import com.callerid.number.lookup.home.databinding.GooglebignativeBinding
import com.callerid.number.lookup.home.databinding.GooglebignativetopBinding
import com.callerid.number.lookup.home.databinding.Googlemidnative2Binding
import com.callerid.number.lookup.home.databinding.GooglemidnativeBinding
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
import kotlin.collections.plusAssign
import kotlin.text.compareTo

class InlinePromo() {
    interface NativeAdObserver {
        fun onNativeAdLoaded()
        fun onNativeAdFailed()
    }

    companion object {
        private var nativeAd: NativeAd? = null

        /**
         * A load is in flight. The pool is one static slot and every `show*` kicks a refill,
         * so without this a surface that opens twice in a row (the app drawer, the left panel)
         * stacks concurrent AdLoader requests that each overwrite — and destroy — the last
         * one's result.
         */
        @Volatile
        private var loadingSince = 0L

        /**
         * How long a request may be considered in flight. AdLoader always answers one of its
         * two callbacks, but a latched flag here would kill native ads process-wide, so the
         * guard expires rather than trusting that.
         */
        private const val LOAD_TIMEOUT_MS = 60_000L

        private val isLoading: Boolean
            get() = loadingSince != 0L &&
                    System.currentTimeMillis() - loadingSince < LOAD_TIMEOUT_MS

        /**
         * Whether a native is in hand right now.
         *
         * Callers that re-render an already-filled frame need this: rendering consumes the
         * pooled ad, so asking again before the refill lands would drop through to the
         * fallback path and replace a good ad with a custom one. See
         * [com.callerid.admesh.engine.ShellPromoConfig.refreshSlot].
         */
        fun hasPreloadedNative(): Boolean = nativeAd != null
    }

    /**
     * Takes the shimmer down and clears whatever half-built view is in the frame.
     *
     * Every render path below builds the ad view FIRST and stops the shimmer only once that
     * has succeeded — so anything thrown while binding a template (a null asset, a recycled
     * NativeAd, an inflate failure) used to land in a catch that only logged, leaving the
     * shimmer running over an empty frame for as long as the screen lived. The launcher's
     * side panels are where that shows up worst: they are never recreated, so the placeholder
     * stays until the process dies.
     */
    private fun clearToEmpty(layout: FrameLayout, shimmer: ShimmerFrameLayout?) {
        runCatching {
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            layout.removeAllViews()
        }
    }

    private fun Activity.isActivityDestroyedCompat(): Boolean {
        if (isFinishing) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed
    }

    fun fetchNativeAds(context: Activity, observer: NativeAdObserver? = null) {
        val adsPreference = PromoVault.getInstance(context)

        if (!adsPreference.getBoolean("IsAdsON") || PromoKind.fromString(adsPreference.getString("IsAdType")) != PromoKind.GOOGLE) {
            observer?.onNativeAdFailed()
            return
        }

        if (!adsPreference.getBoolean("NativeAd")) {
            observer?.onNativeAdFailed()
            return
        }

        val adUnit = PerScreenPromo.inlineAdUnitId(context)
        if (adUnit.isEmpty()) {
            return
        }
        
        if (isLoading) {
            Log.d("NativeAds", "load already in flight — skipped")
            return
        }
        loadingSince = System.currentTimeMillis()
        val adLoader =
            AdLoader.Builder(context, adUnit)
                .forNativeAd { nativeAds ->

                    nativeAd?.destroy()
                    nativeAd = nativeAds
                    loadingSince = 0L

                    if (context.isActivityDestroyedCompat()) {

                        return@forNativeAd
                    }

                    observer?.onNativeAdLoaded()
                    try {
                        context.trackEvent("native_ads_load")
                    } catch (e: Exception) {
                    }

                    Log.e("NativeAds", "Google Load: nativeAd")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        loadingSince = 0L
                        if (context.isActivityDestroyedCompat()) return
                        observer?.onNativeAdFailed()
                        Log.e(
                            "NativeAds",
                            "Google onAdFailedToLoad:nativeAd ${loadAdError.message}"
                        )
                        try {
                            context.trackEvent("native_ads_fail")
                        } catch (e: Exception) {
                        }
                        nativeAd = null
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun renderBigNative(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        isButtonTop: Boolean? = false,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {

        val adsPreference = PromoVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context)
            || !adsPreference.getBoolean("IsAdsON")
            || !adsPreference.getBoolean("NativeAd")
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }

        if (nativeCounter < adsPreference.getInt("NativeCounter")) {
            nativeCounter++
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }
        nativeCounter = 0

        layout.visible()
        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()

        when (PromoKind.fromString(adsPreference.getString("IsAdType"))) {
            PromoKind.GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post

                        if (nativeAd != null) {

                            val rootView: View = if (isButtonTop == true) {
                                val binding =
                                    GooglebignativetopBinding.inflate(context.layoutInflater)

                                bigNativeTemplateTop(nativeAd!!, binding, context)
                                binding.root
                            } else {
                                val binding =
                                    GooglebignativeBinding.inflate(context.layoutInflater)

                                bigNativeTemplate(nativeAd!!, binding, context)
                                binding.root
                            }

                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(rootView)

                            context.trackEvent("native_ads_show_big_native_google")

                            if (BuildConfig.DEBUG) {
                                PromoRevenueGauge.emitDebugRevenue(context)
                            }

                            nativeAd?.setOnPaidEventListener {
                                PromoRevenueGauge.reportPaidEvent(context, it)
                            }

                            nativeAd = null
                            fetchNativeAds(context)

                            return@post
                        }

                        if (adsPreference.getBoolean("IsFail_FB")) {
                            renderFbFallback(context, layout, imageView, shimmer)
                        } else {
                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false

                            InHouseRegistry().fetchHouseAd(
                                context,
                                layout,
                                InHouseRegistry.CustomAdType.BIG_NATIVE,
                                imageView
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("NativeAds", "Google BigNative crash", e)
                        clearToEmpty(layout, shimmer)
                    }
                }
            }

            PromoKind.FACEBOOK -> {
                renderFbFallback(context, layout, imageView)
            }

            PromoKind.UNKNOWN, PromoKind.CUSTOM -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                InHouseRegistry().fetchHouseAd(
                    context,
                    layout,
                    InHouseRegistry.CustomAdType.BIG_NATIVE,
                    imageView
                )
            }
        }
    }

    private fun bigNativeTemplate(
        nativeAd: NativeAd,
        binding: GooglebignativeBinding,
        context: Activity
    ) {
        binding.apply {

            binding.mainNativeadView.mediaView = adMedia
            binding.mainNativeadView.headlineView = adHeadline
            binding.mainNativeadView.bodyView = adBody
            binding.mainNativeadView.callToActionView = adCallToAction
            binding.mainNativeadView.iconView = adAppIcon

            (binding.mainNativeadView.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadView.mediaView?.mediaContent = nativeAd.mediaContent

            val bgColor = PromoVault.getInstance(context).getString("NativeBgColor")
            val btnColor = PromoVault.getInstance(context).getString("NativebtnColor")
            val txtColor = PromoVault.getInstance(context).getString("NativetxtColor") ?: "#000000"
            val btntxtColor = PromoVault.getInstance(context).getString("NativebtntxtColor") ?: "#FFFFFF"

            binding.mainNativeadView.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))
            binding.mainNativeadView.callToActionView?.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))

            (binding.mainNativeadView.headlineView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (binding.mainNativeadView.bodyView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (adCallToAction as TextView).setTextColor(parseColorOrNull(btntxtColor, "#FFFFFF"))

            binding.mainNativeadView.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadView.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadView.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.callToActionView as TextView).text = nativeAd.callToAction
            }

            binding.mainNativeadView.setNativeAd(nativeAd)
        }
    }

    private fun bigNativeTemplateTop(
        nativeAd: NativeAd,
        binding: GooglebignativetopBinding,
        context: Activity
    ) {
        binding.apply {

            binding.mainNativeadView.mediaView = adMedia
            binding.mainNativeadView.headlineView = adHeadline
            binding.mainNativeadView.bodyView = adBody
            binding.mainNativeadView.callToActionView = adCallToAction
            binding.mainNativeadView.iconView = adAppIcon

            (binding.mainNativeadView.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadView.mediaView?.mediaContent = nativeAd.mediaContent

            val bgColor = PromoVault.getInstance(context).getString("NativeBgColor")
            val btnColor = PromoVault.getInstance(context).getString("NativebtnColor")
            val txtColor = PromoVault.getInstance(context).getString("NativetxtColor") ?: "#000000"
            val btntxtColor = PromoVault.getInstance(context).getString("NativebtntxtColor") ?: "#FFFFFF"

            binding.mainNativeadView.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))
            binding.mainNativeadView.callToActionView?.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))

            (binding.mainNativeadView.headlineView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (binding.mainNativeadView.bodyView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (adCallToAction as TextView).setTextColor(parseColorOrNull(btntxtColor, "#FFFFFF"))

            binding.mainNativeadView.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadView.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadView.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.callToActionView as TextView).text = nativeAd.callToAction
            }

            binding.mainNativeadView.setNativeAd(nativeAd)
        }
    }

    private fun renderFbFallback(
        context: Activity,
        layout: FrameLayout,
        imageView: ImageView? = null,
        shimmer: ShimmerFrameLayout? = null
    ) {
        val adsPref = PromoVault.getInstance(context)
        val fbId = adsPref.getString("faceB_NativeAds")

        if (fbId.isNullOrEmpty()) {
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            InHouseRegistry().fetchHouseAd(
                context,
                layout,
                InHouseRegistry.CustomAdType.BIG_NATIVE,
                imageView
            )
            return
        }

        val fbNative = com.facebook.ads.NativeAd(context, fbId)

        fbNative.loadAd(
            fbNative.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                override fun onMediaDownloaded(ad: Ad?) {
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        layout.findFocus()?.clearFocus()
                        layout.removeAllViews()
                        bindFbNative(fbNative, layout, context)
                    }
                }

                override fun onError(ad: Ad?, adError: AdError?) {
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        layout.findFocus()?.clearFocus()
                        InHouseRegistry().fetchHouseAd(
                            context,
                            layout,
                            InHouseRegistry.CustomAdType.BIG_NATIVE,
                            imageView
                        )
                    }
                }

                override fun onAdLoaded(ad: Ad?) {
                    if (fbNative !== ad) return
                    context.trackEvent("native_ads_show_big_native_fb_load")
                    fbNative.downloadMedia()
                }

                override fun onAdClicked(ad: Ad?) {}
                override fun onLoggingImpression(ad: Ad?) {}
            }).build()
        )
    }

    fun bindFbNative(
        nativeAd: com.facebook.ads.NativeAd,
        viewGroup: ViewGroup,
        activity: Activity,
        adSize: String? = null
    ) {

        viewGroup.isVisible = true

        nativeAd.unregisterView()

        val binding = FbNativeBinding.inflate(LayoutInflater.from(activity), viewGroup, false)

        viewGroup.removeAllViews()
        viewGroup.addView(binding.root)

        val adOptionsView = AdOptionsView(activity, nativeAd, binding.nativview)
        binding.adChoicesContainer.removeAllViews()
        binding.adChoicesContainer.addView(adOptionsView, 0)
        val bgColor = PromoVault.getInstance(activity).getString("NativeBgColor")
        val btnColor = PromoVault.getInstance(activity).getString("NativebtnColor")
        val txtColor =
            PromoVault.getInstance(activity).getString("NativetxtColor") ?: "#000000"
        val btntxtColor =
            PromoVault.getInstance(activity).getString("NativebtntxtColor") ?: "#000000"

        binding.nativeAdTitle.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdSocialContext.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdSponsoredLabel.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdBody.setTextColor(Color.parseColor(txtColor))

        binding.nativview.backgroundTintList =
            ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))

        binding.nativeAdCallToAction.backgroundTintList =
            ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))
        (binding.nativeAdCallToAction as TextView).apply {
            setTextColor(Color.parseColor(btntxtColor))
        }

        binding.nativeAdTitle.text = nativeAd.advertiserName
        binding.nativeAdBody.text = nativeAd.adBodyText
        binding.nativeAdSocialContext.text = nativeAd.adSocialContext
        binding.nativeAdSponsoredLabel.text = nativeAd.sponsoredTranslation

        if (nativeAd.hasCallToAction()) {
            binding.nativeAdCallToAction.text = nativeAd.adCallToAction
            binding.nativeAdCallToAction.isVisible = true
        } else {
            binding.nativeAdCallToAction.isVisible = false
        }

        val clickableViews = listOf(binding.nativeAdTitle, binding.nativeAdCallToAction)

        nativeAd.registerViewForInteraction(
            binding.root, binding.nativeAdMedia, binding.nativeAdIcon, clickableViews
        )
    }

    fun renderMidNative(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {
        val adsPref = PromoVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context)
            || !adsPref.getBoolean("IsAdsON")
            || !adsPref.getBoolean("NativeAd")
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }

        if (nativeCounter < adsPref.getInt("MidNativeCounter")) {
            nativeCounter += 1
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }
        nativeCounter = 0

        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()
        layout.visible()

        when (PromoKind.fromString(adsPref.getString("IsAdType"))) {
            PromoKind.GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (nativeAd != null) {
                            val binding = GooglemidnativeBinding.inflate(context.layoutInflater)
                            MidNativeTemplate(
                                nativeAd!!,
                                binding,
                                context
                            )

                            layout.removeAllViews()

                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(binding.root)

                            context.trackEvent("native_ads_show_mid_native_google")

                            if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(context)

                            nativeAd!!.setOnPaidEventListener {
                                PromoRevenueGauge.reportPaidEvent(context, it)
                            }

                            nativeAd = null
                            fetchNativeAds(context)
                            return@post
                        }

                        if (adsPref.getBoolean("IsFail_FB")) {
                            showMidFBNativeFallback(context, layout, shimmer, imageView)
                        } else {
                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            InHouseRegistry().fetchHouseAd(
                                context,
                                layout,
                                InHouseRegistry.CustomAdType.MID_NATIVE
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("MidNativeAds", "Google MidNative failed: ${e.message}")
                        clearToEmpty(layout, shimmer)
                    }
                }

            }

            PromoKind.FACEBOOK -> {

                showMidFBNativeFallback(context, layout, shimmer, imageView)
            }

            PromoKind.CUSTOM, PromoKind.UNKNOWN -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                InHouseRegistry().fetchHouseAd(
                    context,
                    layout,
                    InHouseRegistry.CustomAdType.MID_NATIVE
                )
            }
        }
    }

    private fun showMidFBNativeFallback(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null
    ) {
        val adsPref = PromoVault.getInstance(context)
        val fbId = adsPref.getString("faceB_NativeAds")

        if (fbId.isNullOrEmpty()) {
            layout.removeAllViews()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            InHouseRegistry().fetchHouseAd(
                context,
                layout,
                InHouseRegistry.CustomAdType.MID_NATIVE
            )
            return
        }

        val fbNative = com.facebook.ads.NativeAd(context, fbId)
        fbNative.loadAd(
            fbNative.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                override fun onMediaDownloaded(ad: Ad?) {
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        layout.findFocus()?.clearFocus()
                        layout.removeAllViews()
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        bindFbMidNative(fbNative, layout, context)
                        context.trackEvent("native_ads_show_mid_fb")
                    }
                }

                override fun onError(ad: Ad?, adError: AdError?) {
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        Log.e("NativeAds", "FB MidNative failed: ${adError?.errorMessage}")
                        layout.findFocus()?.clearFocus()
                        layout.removeAllViews()
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        InHouseRegistry().fetchHouseAd(
                            context,
                            layout,
                            InHouseRegistry.CustomAdType.MID_NATIVE
                        )
                    }
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

    fun bindFbMidNative(
        nativeAd: com.facebook.ads.NativeAd,
        viewGroup: ViewGroup,
        activity: Activity,
        adSize: String? = null
    ) {

        viewGroup.isVisible = true

        nativeAd.unregisterView()

        val binding = FbMidNativeBinding.inflate(LayoutInflater.from(activity), viewGroup, false)

        viewGroup.removeAllViews()
        viewGroup.addView(binding.root)

        val adOptionsView = AdOptionsView(activity, nativeAd, binding.nativview)
        binding.adChoicesContainer.removeAllViews()
        binding.adChoicesContainer.addView(adOptionsView, 0)

        val bgColor = PromoVault.getInstance(activity).getString("NativeBgColor")
        val btnColor = PromoVault.getInstance(activity).getString("NativebtnColor")
        val txtColor =
            PromoVault.getInstance(activity).getString("NativetxtColor") ?: "#000000"
        val btntxtColor =
            PromoVault.getInstance(activity).getString("NativebtntxtColor") ?: "#000000"

        binding.nativeAdTitle.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdSocialContext.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdSponsoredLabel.setTextColor(Color.parseColor(txtColor))
        binding.nativeAdBody.setTextColor(Color.parseColor(txtColor))

        binding.nativview.backgroundTintList =
            ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))

        binding.nativeAdCallToAction.backgroundTintList =
            ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))
        (binding.nativeAdCallToAction as TextView).apply {
            setTextColor(Color.parseColor(btntxtColor))
        }

        binding.nativeAdTitle.text = nativeAd.advertiserName
        binding.nativeAdBody.text = nativeAd.adBodyText
        binding.nativeAdSocialContext.text = nativeAd.adSocialContext
        binding.nativeAdSponsoredLabel.text = nativeAd.sponsoredTranslation

        if (nativeAd.hasCallToAction()) {
            binding.nativeAdCallToAction.text = nativeAd.adCallToAction
            binding.nativeAdCallToAction.isVisible = true
        } else {
            binding.nativeAdCallToAction.isVisible = false
        }

        val clickableViews = listOf(binding.nativeAdTitle, binding.nativeAdCallToAction)

        nativeAd.registerViewForInteraction(
            binding.root, binding.nativeAdIcon, clickableViews
        )
    }

    private fun MidNativeTemplate(
        nativeAd: NativeAd,
        binding: GooglemidnativeBinding,
        context: Activity
    ) {
        binding.apply {
            binding.mainNativeadView.headlineView = adHeadline
            binding.mainNativeadView.bodyView = adBody
            binding.mainNativeadView.callToActionView = adCallToAction
            binding.mainNativeadView.iconView = adAppIcon

            (binding.mainNativeadView.headlineView as TextView).text = nativeAd.headline

            val bgColor = PromoVault.getInstance(context).getString("NativeBgColor")
            val btnColor = PromoVault.getInstance(context).getString("NativebtnColor")
            val txtColor = PromoVault.getInstance(context).getString("NativetxtColor") ?: "#000000"
            val btntxtColor = PromoVault.getInstance(context).getString("NativebtntxtColor") ?: "#FFFFFF"

            binding.mainNativeadView.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))
            binding.mainNativeadView.callToActionView?.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))

            (binding.mainNativeadView.headlineView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (binding.mainNativeadView.bodyView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (adCallToAction as TextView).setTextColor(parseColorOrNull(btntxtColor, "#FFFFFF"))

            binding.mainNativeadView.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadView.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadView.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.callToActionView as TextView).text = nativeAd.callToAction
            }

            binding.mainNativeadView.setNativeAd(nativeAd)
        }
    }

    fun View.visible() {
        this.visibility = View.VISIBLE
    }

    fun View.invisible() {
        this.visibility = View.GONE
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

    fun renderMidNative2(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {
        val adsPref = PromoVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context) || !adsPref.getBoolean("IsAdsON") || !adsPref.getBoolean("NativeAd")) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }

        if (nativeCounter < adsPref.getInt("MidNativeCounter")) {
            nativeCounter += 1
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }
        nativeCounter = 0

        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()
        layout.visible()
        when (PromoKind.fromString(adsPref.getString("IsAdType"))) {
            GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (Companion.nativeAd != null) {
                            val binding = Googlemidnative2Binding.inflate(context.layoutInflater)
                            MidNativeTemplate2(
                                Companion.nativeAd!!,
                                binding,
                                context
                            )

                            layout.removeAllViews()

                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(binding.root)

                            context.trackEvent("native_ads_show_mid_native2_google")

                            if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(context)

                            nativeAd!!.setOnPaidEventListener {
                                PromoRevenueGauge.reportPaidEvent(context, it)
                            }

                            nativeAd = null
                            fetchNativeAds(context)
                            return@post
                        }

                        layout.removeAllViews()
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        InHouseRegistry().fetchHouseAd(
                            context,
                            layout,
                            InHouseRegistry.CustomAdType.MID_NATIVE
                        )
                    } catch (e: Exception) {
                        Log.e("MidNativeAds", "Google MidNative failed: ${e.message}")
                        clearToEmpty(layout, shimmer)
                    }
                }

            }

            CUSTOM, UNKNOWN -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                InHouseRegistry().fetchHouseAd(
                    context,
                    layout,
                    InHouseRegistry.CustomAdType.MID_NATIVE
                )
            }

            PromoKind.FACEBOOK -> {
                showMidFBNativeFallback(context, layout, shimmer, imageView)
            }
        }
    }

    private fun MidNativeTemplate2(
        nativeAd: NativeAd,
        binding: Googlemidnative2Binding,
        context: Activity
    ) {
        binding.apply {

            binding.mainNativeadView.mediaView = adMedia
            binding.mainNativeadView.headlineView = adHeadline
            binding.mainNativeadView.bodyView = adBody
            binding.mainNativeadView.callToActionView = adCallToAction
            binding.mainNativeadView.iconView = adAppIcon

            (binding.mainNativeadView.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadView.mediaView?.mediaContent = nativeAd.mediaContent

            val bgColor = PromoVault.getInstance(context).getString("NativeBgColor")
            val btnColor = PromoVault.getInstance(context).getString("NativebtnColor")
            val txtColor = PromoVault.getInstance(context).getString("NativetxtColor") ?: "#000000"
            val btntxtColor = PromoVault.getInstance(context).getString("NativebtntxtColor") ?: "#FFFFFF"

            binding.mainNativeadView.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(bgColor, "#FFFFFF"))
            binding.mainNativeadView.callToActionView?.backgroundTintList = ColorStateList.valueOf(parseColorOrNull(btnColor, "#000000"))

            (binding.mainNativeadView.headlineView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (binding.mainNativeadView.bodyView as TextView).setTextColor(parseColorOrNull(txtColor, "#000000"))
            (adCallToAction as TextView).setTextColor(parseColorOrNull(btntxtColor, "#FFFFFF"))

            binding.mainNativeadView.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadView.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadView.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.callToActionView as TextView).text = nativeAd.callToAction
            }

            binding.mainNativeadView.setNativeAd(nativeAd)
        }
    }

}
