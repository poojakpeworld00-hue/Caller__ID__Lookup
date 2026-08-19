package com.callerid.number.lookup.home.ui.onboarding

import android.animation.ValueAnimator
import android.content.Intent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.callerid.admesh.domain.ShellPromoConfig
import com.callerid.admesh.domain.logKeyEvent
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.base.FrameActivity
import com.callerid.number.lookup.home.data.StorageRegistry
import com.callerid.number.lookup.home.databinding.ScreenOnboardingBinding
import com.callerid.number.lookup.home.launcher.helpers.OnboardRouter
import com.callerid.number.lookup.home.permission.PermitEngine
import com.callerid.number.lookup.home.ui.AppHomeActivity
import com.callerid.number.lookup.home.ui.intro.RevealConfig
import com.callerid.number.lookup.home.ui.intro.RevealPolicy
import com.callerid.number.lookup.home.util.followAdContainer
import org.fossify.commons.extensions.beVisibleIf

class SlideIntroActivity : FrameActivity<ScreenOnboardingBinding>() {

    override val layoutId: Int = R.layout.screen_onboarding

    private val prefs by lazy { StorageRegistry(this) }
    private val pages = SlideCatalog.all
    private val dots = mutableListOf<View>()

    // Spring settle (dampingRatio 0.8 / stiffness 380 ≈ this overshoot) — the design's
    // default motion, used for the dot pill stretch and the CTA morph.
    private val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
    private var wasLast = false

    /** One-shot guard so a first-page back can't fire the forward flow twice. */
    private var forwarding = false

    override fun initView() {
        // Record this intro show for the once/count frequency gate.
        RevealPolicy.markShown(this, RevealConfig.ONBOARDING)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Ad frame pinned at the bottom, `launcher_ads.onboarding.intro.slot` — a mid2 native
        // unless Remote LauncherPrefs switches it to a banner or turns it off.
        ShellPromoConfig.showSlot(
            activity = this,
            slot = ShellPromoConfig.onboardingSlot(this, ShellPromoConfig.OnboardScreen.INTRO),
            container = binding.adNativeFrame,
            shimmer = binding.adShimmer,
        )
        binding.adNativeDivider.followAdContainer(binding.adNativeFrame)

        binding.viewPager.adapter = SlideAdapter(pages)
        binding.viewPager.offscreenPageLimit = 1
        buildDots()
        updateDots(0)

        // Parallax: the illustration tracks the swipe fully; the title (0.85×) and
        // body (0.7×) lag behind it as they scroll.
        binding.viewPager.setPageTransformer { page, position ->
            val w = page.width.toFloat()
            page.findViewById<View?>(R.id.tvTitle)?.translationX = position * w * 0.15f
            page.findViewById<View?>(R.id.tvDesc)?.translationX = position * w * 0.30f
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })

        // `onboarding.intro.skip_enabled: false` hides Skip, so the carousel has to be paged
        // through to its end (Back still moves forward, see below).
        val ui = ShellPromoConfig.onboardingUi(this, ShellPromoConfig.OnboardScreen.INTRO)
        binding.btnSkip.beVisibleIf(ui.skipEnabled)

        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.lastIndex) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        // Back walks FORWARD through the pages (1 → 2 → 3) with the pager's smooth
        // scroll, so every page is seen before the user can leave. Only once the
        // last page is showing does back skip out — same path as the Skip button.
        // `back_action: "next_screen"` overrides that: any Back leaves for the next
        // screen straight away, wherever the carousel has got to.
        // The callback stays enabled so back never falls through to FrameActivity's
        // exit handler; `forwarding` blocks a double finish.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = binding.viewPager.currentItem
                if (!ui.backAdvances && current < pages.lastIndex) {
                    binding.viewPager.setCurrentItem(current + 1, true)
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
            binding.dots.addView(dot)
            dots.add(dot)
        }
    }

    private fun updateDots(active: Int) {
        val activeWidth = dp(24)
        val size = dp(8)
        dots.forEachIndexed { i, dot ->
            // Active dot stretches into a pill; the others shrink back — spring settle.
            animateDotWidth(dot, if (i == active) activeWidth else size)
            dot.setBackgroundResource(
                if (i == active) R.drawable.form_dot_active else R.drawable.form_dot
            )
        }
        // Final step: the CTA morphs to "Get Started" on the hero gradient — the
        // one place onboarding uses the gradient (Visual System rule).
        val last = active == pages.lastIndex
        binding.btnNext.setText(if (last) R.string.onboarding_get_started else R.string.onboarding_next)
        binding.btnNext.setBackgroundResource(
            if (last) R.drawable.form_btn_gradient else R.drawable.form_btn_primary
        )
        if (last != wasLast) {
            wasLast = last
            popCta()
        }
    }

    /** Springs a dot's width to [target] (the pill-stretch shared-bounds effect). */
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

    /** Width/scale spring when the CTA morphs between Next and Get Started. */
    private fun popCta() {
        binding.btnNext.animate().cancel()
        binding.btnNext.scaleX = 0.94f
        binding.btnNext.scaleY = 0.94f
        binding.btnNext.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(spring)
            .setDuration(340L)
            .start()
    }

    private fun finishOnboarding() {
        prefs.isOnboardingDone = true
        logKeyEvent("onboarding_completed")
        PermitEngine.check(this) {
            // Permission done → show this screen's interstitial (`onboarding.intro.
            // inter_enabled`, on by default; the callback fires immediately when there is
            // nothing to show) → THEN navigate.
            ShellPromoConfig.runOnboardingInter(this, ShellPromoConfig.OnboardScreen.INTRO) {
                // In the launcher's first run, hand back to the order — usually the language
                // picker, but the order decides. Outside it, this is the last screen.
                if (OnboardRouter.isOnboardingActive(this)) {
                    OnboardRouter.advance(this)
                } else {
                    OnboardRouter.goHome(this)
                }
            }
        }
    }
}
