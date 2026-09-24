package com.callerid.number.lookup.home.screen.slides

import android.animation.ValueAnimator
import android.content.Intent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.engine.trackEvent
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.databinding.ScreenOnboardingBinding
import com.callerid.number.lookup.home.onboard.OnboardRouter
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.screen.AppHomeActivity
import com.callerid.number.lookup.home.screen.reveal.RevealConfig
import com.callerid.number.lookup.home.screen.reveal.RevealPolicy
import com.callerid.number.lookup.home.kit.followAdContainer
import org.fossify.commons.extensions.beVisibleIf

class SlideIntroActivity : FrameActivity<ScreenOnboardingBinding>() {

    override val layoutId: Int = R.layout.screen_onboarding

    private val prefs by lazy { StorageRegistry(this) }
    private val pages = SlideCatalog.all
    private val dots = mutableListOf<View>()

    private val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
    private var wasLast = false

    private var forwarding = false

    override fun initView() {

        RevealPolicy.markShown(this, RevealConfig.ONBOARDING)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ShellPromoConfig.renderSlot(
            activity = this,
            slot = ShellPromoConfig.onboardSlot(this, ShellPromoConfig.OnboardScreen.INTRO),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        binding.vuPager.adapter = SlideAdapter(pages)
        binding.vuPager.offscreenPageLimit = 1
        buildDots()
        updateDots(0)

        binding.vuPager.setPageTransformer { page, position ->
            val w = page.width.toFloat()
            page.findViewById<View?>(R.id.lblTitle)?.translationX = position * w * 0.15f
            page.findViewById<View?>(R.id.lblDesc)?.translationX = position * w * 0.30f
        }

        binding.vuPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })

        val ui = ShellPromoConfig.onboardUi(this, ShellPromoConfig.OnboardScreen.INTRO)
        binding.padSkip.beVisibleIf(ui.skipEnabled)

        binding.padSkip.setOnClickListener { finishOnboarding() }
        binding.padNext.setOnClickListener {
            val current = binding.vuPager.currentItem
            if (current < pages.lastIndex) {
                binding.vuPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = binding.vuPager.currentItem
                if (!ui.backAdvances && current < pages.lastIndex) {
                    binding.vuPager.setCurrentItem(current + 1, true)
                } else if (!forwarding) {
                    forwarding = true
                    finishOnboarding()
                }
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildDots() {
        val size = dp(8)
        val gap = dp(5)
        pages.indices.forEach { _ ->
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
            dot.layoutParams = lp
            binding.dotsVw.addView(dot)
            dots.add(dot)
        }
    }

    private fun updateDots(active: Int) {
        val activeWidth = dp(24)
        val size = dp(8)
        dots.forEachIndexed { i, dot ->

            animateDotWidth(dot, if (i == active) activeWidth else size)
            dot.setBackgroundResource(
                if (i == active) R.drawable.form_dot_active else R.drawable.form_dot
            )
        }

        val last = active == pages.lastIndex
        binding.padNext.setText(if (last) R.string.onboarding_get_started else R.string.onboarding_next)
        binding.padNext.setBackgroundResource(
            if (last) R.drawable.form_btn_gradient else R.drawable.form_btn_primary
        )
        if (last != wasLast) {
            wasLast = last
            popCta()
        }
    }

    private fun animateDotWidth(dot: View, target: Int) {
        val lp = dot.layoutParams as LinearLayout.LayoutParams
        if (lp.width == target) return
        (dot.getTag(R.id.tag_dot_anim) as? ValueAnimator)?.cancel()
        ValueAnimator.ofInt(lp.width, target).apply {
            duration = 320L
            interpolator = spring
            addUpdateListener {
                lp.width = it.animatedValue as Int
                dot.layoutParams = lp
            }
            dot.setTag(R.id.tag_dot_anim, this)
            start()
        }
    }

    private fun popCta() {
        binding.padNext.animate().cancel()
        binding.padNext.scaleX = 0.94f
        binding.padNext.scaleY = 0.94f
        binding.padNext.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(spring)
            .setDuration(340L)
            .start()
    }

    private fun finishOnboarding() {
        prefs.isOnboardingDone = true
        trackEvent("onboarding_completed")
        PermitEngine.check(this) {

            ShellPromoConfig.runOnboardInterstitial(this, ShellPromoConfig.OnboardScreen.INTRO) {

                if (OnboardRouter.isOnboardingActive(this)) {
                    OnboardRouter.advance(this)
                } else {
                    OnboardRouter.goHome(this)
                }
            }
        }
    }
}
