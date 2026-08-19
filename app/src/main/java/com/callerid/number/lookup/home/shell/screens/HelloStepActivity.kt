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

/**
 * First launcher onboarding screen, shown once.
 *
 * The screen used to carry a toggle per permission; the design it is now built to has none,
 * so Continue hands off to [PermitEngine], which asks for whatever `permission_engine` has
 * configured for this Activity — notifications and phone state — in priority order, honouring
 * each rule's delay and skipping anything already granted or not applicable on this SDK.
 *
 * The engine matches rules by Activity simple name, so `"HelloStepActivity"` has to
 * appear in the `activities` list of each rule in Remote LauncherPrefs. With no rule targeting this
 * screen the engine completes immediately and Continue simply moves on — which is also what
 * happens once every permission is already granted.
 *
 * Declining is not a dead end: the flow always continues to whatever `onboarding.order` puts
 * next (the "set as default launcher" step, unless Remote LauncherPrefs reordered it), and the
 * permissions stay reachable later from Settings. Skip goes to the same place without asking
 * for anything.
 */
class HelloStepActivity : ShellBaseActivity() {

    private val binding by viewBinding(ScreenOnboardingWelcomeBinding::inflate)
    private var shieldPulse: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        excludeAppFromRecents()

        binding.onboardingContinueVw.setOnClickListener { requestOnboardingPermissions() }
        binding.onboardingSkipVw.setOnClickListener { goToNextStep() }

        // `onboarding.welcome.skip_enabled: false` makes the screen a required step —
        // Continue (and Back, which behaves like Skip) are then the only ways on.
        val ui = ShellPromoConfig.onboardUi(this, ShellPromoConfig.OnboardScreen.WELCOME)
        binding.onboardingSkipVw.beVisibleIf(ui.skipEnabled)

        // Back moves the flow on rather than out. Onboarding runs once and there is nothing
        // behind this screen worth returning to, so Back behaves like Skip.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goToNextStep()
        })

        // Ad frame pinned above the CTA, `launcher_ads.onboarding.welcome.slot` — a mid native
        // unless Remote LauncherPrefs says otherwise. renderSlot hides the frame outright when the slot
        // is off (as the renderers do when ads are off or the network is down), and
        // followAdContainer drops the hairline with it.
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
        // an infinite animator keeps a hard reference to the view it drives
        shieldPulse?.cancel()
        shieldPulse = null
        super.onDestroy()
    }

    private fun requestOnboardingPermissions() {
        // onComplete fires once the whole configured queue is done — or straight away when
        // there is nothing to ask. It deliberately does NOT fire if the run is interrupted
        // (another Activity triggers the engine, or this one is torn down mid-flow), so a
        // half-finished prompt chain can never navigate the user onwards behind its back.
        PermitEngine.check(this) { goToNextStep() }
    }

    /**
     * Whatever `launcher_ads.onboarding.order` puts after this screen — the default-home ask
     * unless the order was changed — with this screen's exit interstitial in front of it.
     */
    private fun goToNextStep() {
        ShellPromoConfig.runOnboardInterstitial(this, ShellPromoConfig.OnboardScreen.WELCOME) {
            OnboardRouter.advance(this)
        }
    }
}
