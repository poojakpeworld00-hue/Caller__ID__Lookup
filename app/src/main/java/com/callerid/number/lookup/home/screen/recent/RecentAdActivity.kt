package com.callerid.number.lookup.home.screen.recent

import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
 * `recent_ad.native` chooses the body format (`big` / `mid` / `mid2`, or `off` for none) and
 * `recent_ad.close_ad` what fires on the way out (`inter`, `directlink`, or `none`). The screen
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

        when {
            settings.closeShowsInterstitial -> FlowInterstitial().renderInterstitial(this) { close() }
            settings.closeShowsLink -> {
                DirectLinkOpener.open(this, ShellPromoConfig.directLink(this))
                close()
            }
            else -> close()
        }
    }

    private fun close() {
        if (isFinishing || isDestroyed) return
        finish()
    }
}
