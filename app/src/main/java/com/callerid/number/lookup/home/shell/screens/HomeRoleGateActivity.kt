package com.callerid.number.lookup.home.shell.screens

import android.animation.ValueAnimator
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.number.lookup.home.databinding.ScreenOnboardingDefaultLauncherBinding
import com.callerid.number.lookup.home.shell.ext.excludeAppFromRecents
import com.callerid.number.lookup.home.shell.ext.isDefaultLauncher
import com.callerid.number.lookup.home.shell.ext.roleManager
import com.callerid.number.lookup.home.shell.support.OnboardRouter
import com.callerid.number.lookup.home.shell.support.SwipeCoachPrompt
import com.callerid.number.lookup.home.shell.support.breathe
import com.callerid.number.lookup.home.shell.support.riseIn
import com.callerid.number.lookup.home.shell.support.stampIn
import com.callerid.number.lookup.home.shell.support.twinkle
import com.callerid.number.lookup.home.kit.followAdContainer
import com.callerid.number.lookup.home.kit.openActivity
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.isQPlus

class HomeRoleGateActivity : ShellBaseActivity() {

    private companion object {
        const val REQ_HOME_SETTINGS = 7011
        const val REQ_ROLE_HOME = 7012
    }

    private val binding by viewBinding(ScreenOnboardingDefaultLauncherBinding::inflate)
    private var shieldPulse: ValueAnimator? = null
    private var sparklePulses: List<ValueAnimator> = emptyList()

    private var leaving = false

    private var requestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        excludeAppFromRecents()

        binding.onboardingSetDefaultVw.setOnClickListener { openHomeSettings() }
        binding.onboardingSkipVw.setOnClickListener { goToNextStep() }

        val ui = ShellPromoConfig.onboardUi(this, ShellPromoConfig.OnboardScreen.SET_DEFAULT)
        binding.onboardingSkipVw.beVisibleIf(ui.skipEnabled)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = promptForRole()
        })

        ShellPromoConfig.renderSlot(
            activity = this,
            slot = ShellPromoConfig.onboardSlot(this, ShellPromoConfig.OnboardScreen.SET_DEFAULT),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        playEntrance()
    }

    private fun openHomeSettings() {
        if (leaving || requestInFlight) return

        launchHomeSettings()

        SwipeCoachPrompt.showAfterSettings(this)
    }

    private fun launchHomeSettings() {
        if (leaving) return

        val opened = launchForResult(Intent(Settings.ACTION_HOME_SETTINGS), REQ_HOME_SETTINGS) ||
                launchForResult(
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                    REQ_HOME_SETTINGS
                )

        if (!opened) {
            promptForRole()
        }
    }

    private fun promptForRole() {
        if (leaving || requestInFlight) return

        if (!isQPlus()) {
            goToNextStep()
            return
        }

        if (!launchForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE_HOME)) {
            goToNextStep()
        }
    }

    @Suppress("DEPRECATION")
    private fun launchForResult(intent: Intent, requestCode: Int): Boolean = try {
        startActivityForResult(intent, requestCode)
        requestInFlight = true
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        requestInFlight = false
        if (leaving) return

        if (isDefaultLauncher()) {

            return
        }

        when (requestCode) {
            REQ_HOME_SETTINGS -> promptForRole()
            REQ_ROLE_HOME -> goToNextStep()
        }
    }

    override fun onResume() {
        super.onResume()

        SwipeCoachPrompt.dismiss()

        if (isDefaultLauncher()) {
            goHome()
        }
    }

    private fun goHome() {
        if (leaving) return
        leaving = true
        ShellPromoConfig.runOnboardInterstitial(this, ShellPromoConfig.OnboardScreen.SET_DEFAULT) {
            OnboardRouter.advance(
                activity = this,
                skipRest = ShellPromoConfig.defaultBoardStep(this).skipRestOnGrant,
            )
        }
    }

    private fun goToNextStep() {
        if (leaving) return
        leaving = true
        ShellPromoConfig.runOnboardInterstitial(this, ShellPromoConfig.OnboardScreen.SET_DEFAULT) {
            OnboardRouter.advance(this)
        }
    }

    private fun playEntrance() = with(binding) {
        riseIn(
            listOf(
                onboardingHeroVw,
                onboardingTitleVw,
                onboardingLeadVw,
                onboardingFooterVw,
            )
        )
        stampIn(onboardingBadgeVw)
        shieldPulse = breathe(onboardingShieldVw)
        sparklePulses = twinkle(
            listOf(onboardingSparkle1Vw, onboardingSparkle2Vw, onboardingSparkle3Vw)
        )
    }

    override fun onDestroy() {

        shieldPulse?.cancel()
        shieldPulse = null
        sparklePulses.forEach { it.cancel() }
        sparklePulses = emptyList()
        super.onDestroy()
    }
}
