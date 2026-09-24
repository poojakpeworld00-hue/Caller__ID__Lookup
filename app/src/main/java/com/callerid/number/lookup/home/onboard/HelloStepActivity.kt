package com.callerid.number.lookup.home.onboard

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.CellGestureTileBinding
import com.callerid.number.lookup.home.databinding.ScreenOnboardingWelcomeBinding
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
            override fun handleOnBackPressed() {
                if (OnboardRouter.backMovesForward(this@HelloStepActivity)) goToNextStep()
                else OnboardRouter.passBackThrough(this@HelloStepActivity, this)
            }
        })

        ShellPromoConfig.renderSlot(
            activity = this,
            slot = ShellPromoConfig.onboardSlot(this, ShellPromoConfig.OnboardScreen.WELCOME),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        OnboardRouter.bindStepHeader(
            this, ShellPromoConfig.OnboardScreen.WELCOME, binding.root
        )

        bindGestureTiles()
        playEntrance()
    }

    /**
     * The four launcher gestures, ported from the reference Ready screen: one chevron glyph
     * rotated per direction (up -90, right 0, down 90, left 180), each with its own label pair.
     */
    private fun bindGestureTiles() = with(binding) {
        bindTile(tileSwipeUp, -90f, R.string.gesture_swipe_up, R.string.gesture_swipe_up_body)
        bindTile(tileSwipeRight, 0f, R.string.gesture_swipe_right, R.string.gesture_swipe_right_body)
        bindTile(tileSwipeDown, 90f, R.string.gesture_swipe_down, R.string.gesture_swipe_down_body)
        bindTile(tileSwipeLeft, 180f, R.string.gesture_swipe_left, R.string.gesture_swipe_left_body)
    }

    private fun bindTile(tile: CellGestureTileBinding, rotation: Float, title: Int, body: Int) {
        tile.tileIcon.rotation = rotation
        tile.tileTitle.setText(title)
        tile.tileBody.setText(body)
    }

    private fun playEntrance() = with(binding) {
        riseIn(
            listOf(
                onboardingHeroVw,
                onboardingTitleVw,
                onboardingLeadVw,
                onboardingGesturesVw,
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
