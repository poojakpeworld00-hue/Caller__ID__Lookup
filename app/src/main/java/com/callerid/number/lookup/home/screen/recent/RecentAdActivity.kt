package com.callerid.number.lookup.home.screen.recent

import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.callerid.admesh.engine.LauncherPlacementAds
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.DirectLinkOpener
import com.callerid.admesh.surface.InlinePromo
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.ScreenRecentAdBinding
import com.callerid.number.lookup.home.frame.FrameActivity

/**
 * The page [RecentAdWatcher] puts on top when the app is reopened from the Recents list.
 *
 * `recent_ad.native` chooses the body format (`big` / `mid` / `mid2`, `full_native` for a
 * full-screen native page, or `off` for none) and `recent_ad.close_ad` what fires on the way out
 * (`inter`, `full_native`, `custom`, `directlink`, or `none`). The screen
 * closes itself either way — the close ad never becomes a reason the user cannot leave.
 */
class RecentAdActivity : FrameActivity<ScreenRecentAdBinding>() {

    override val layoutId: Int = R.layout.screen_recent_ad

    private val settings by lazy { ShellPromoConfig.recentAdSettings(this) }

    /** The close ad fires once. A second tap, or back while it is loading, just leaves. */
    private var leaving = false

    override fun initView() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyInsets()
        fillAd()
        binding.recentContinueVw.setOnClickListener { leave() }
    }

    override fun performBack() = leave()

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.recentContentVw) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun fillAd() {
        if (!settings.showsBodyNative) return

        // QRScanner's `native: FullNative` — the whole page is a full-screen native, and closing
        // it closes the page.
        if (settings.nativeType.trim().lowercase() in FULL_NATIVE) {
            leaving = true
            LauncherPlacementAds.showFullNative(this, PLACEMENT) { close() }
            return
        }

        val frame = binding.recentAdFrameVw
        val shimmer = binding.recentShimmerVw
        val promo = InlinePromo()
        when (settings.nativeType.trim().lowercase()) {
            "big", "big_native", "bignative" -> promo.renderBigNative(this, frame, shimmer)
            "mid2", "mid_native2", "midnative2" -> promo.renderMidNative2(this, frame, shimmer)
            else -> promo.renderMidNative(this, frame, shimmer)
        }
    }

    /**
     * Closes the page, showing the configured close ad first. The interstitial closes us from its
     * own callback; a direct link is fire-and-forget, so we close straight after opening it rather
     * than leaving this page underneath the landing page.
     */
    private fun leave() {
        if (leaving) return
        leaving = true

        // `recent_ads_on: false` keeps the page but drops its close ad.
        if (!LauncherPlacementAds.placementEnabled(this, PLACEMENT)) return close()

        val closeAd = settings.closeAd.trim().lowercase()
        when {
            // `recent_googleInter` / a `recent_` link-first chain / `inter_fallback` go through the
            // placement engine; otherwise the app-wide interstitial as before.
            settings.closeShowsInterstitial ->
                if (LauncherPlacementAds.hasOwnInter(this, PLACEMENT)) {
                    LauncherPlacementAds.showInterstitial(this, PLACEMENT) { close() }
                } else {
                    FlowInterstitial().renderInterstitial(this) { close() }
                }
            // QRScanner's other two close ads: a full-screen native (`recent_googleFullNative`,
            // else `googleNative`), or this app's house ad.
            closeAd in FULL_NATIVE -> LauncherPlacementAds.showFullNative(this, PLACEMENT) { close() }
            closeAd == "custom" -> LauncherPlacementAds.showStep(this, PLACEMENT, "custom") { close() }
            settings.closeShowsLink -> {
                DirectLinkOpener.open(this, ShellPromoConfig.directLink(this))
                close()
            }
            else -> close()
        }
    }

    private companion object {
        /** The `recent_` placement keys: `recent_ads_on`, `recent_googleInter`, `recent_googleFullNative`, … */
        const val PLACEMENT = "recent"
        val FULL_NATIVE = setOf("full_native", "fullnative", "full")
    }

    private fun close() {
        if (isFinishing || isDestroyed) return
        finish()
    }
}
