package com.callerid.number.lookup.home.shell.screens

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.number.lookup.home.databinding.ScreenOnboardingWelcomeBinding
import com.callerid.number.lookup.home.shell.ext.excludeAppFromRecents
import com.callerid.number.lookup.home.shell.support.OnboardRouter
import com.callerid.number.lookup.home.shell.support.breathe
import com.callerid.number.lookup.home.shell.support.riseIn
import com.callerid.number.lookup.home.shell.support.stampIn
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.kit.followAdContainer
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.viewBinding

class HelloStepActivity : ShellBaseActivity() {

    private val binding by viewBinding(ScreenOnboardingWelcomeBinding::inflate)
    private var shieldPulse: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        excludeAppFromRecents()

        binding.onboardingContinueVw.setOnClickListener { requestOnboardingPermissions() }
        binding.onboardingSkipVw.setOnClickListener { goToNextStep() }

        val ui = ShellPromoConfig.onboardUi(this, ShellPromoConfig.OnboardScreen.WELCOME)
        binding.onboardingSkipVw.beVisibleIf(ui.skipEnabled)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goToNextStep()
        })

        ShellPromoConfig.renderSlot(
            activity = this,
            slot = ShellPromoConfig.onboardSlot(this, ShellPromoConfig.OnboardScreen.WELCOME),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        playEntrance()
    }

    private fun playEntrance() = with(binding) {
        riseIn(
            listOf(
                onboardingHeroVw,
                onboardingTitleVw,
                onboardingLeadVw,
                onboardingFeaturesVw,
                onboardingFooterVw,
            )
        )
        stampIn(onboardingBadgeVw)
        shieldPulse = breathe(onboardingShieldVw)
    }

    override fun onDestroy() {

        shieldPulse?.cancel()
        shieldPulse = null
        super.onDestroy()
    }

    private fun requestOnboardingPermissions() {

        PermitEngine.check(this) { goToNextStep() }
    }

    private fun goToNextStep() {
        ShellPromoConfig.runOnboardInterstitial(this, ShellPromoConfig.OnboardScreen.WELCOME) {
            OnboardRouter.advance(this)
        }
    }
}
