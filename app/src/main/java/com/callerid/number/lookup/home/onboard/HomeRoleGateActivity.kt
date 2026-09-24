package com.callerid.number.lookup.home.onboard

import android.animation.ValueAnimator
import com.callerid.number.lookup.home.permit.PermitEngine
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.number.lookup.home.databinding.ScreenOnboardingDefaultLauncherBinding
import io.launcher.home.extensions.isDefaultLauncher
import io.launcher.home.extensions.roleManager
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

    private var leaving = false

    private var requestInFlight = false

    private var autoOpenPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        excludeAppFromRecents()

        // Once the role is held (auto_next off), the CTA is how the user moves on.
        binding.onboardingSetDefaultVw.setOnClickListener {
            if (isDefaultLauncher()) goHome() else openHomeSettings()
        }
        binding.onboardingSkipVw.setOnClickListener { goToNextStep() }

        val ui = ShellPromoConfig.onboardUi(this, ShellPromoConfig.OnboardScreen.SET_DEFAULT)
        binding.onboardingSkipVw.beVisibleIf(ui.skipEnabled)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (OnboardRouter.backMovesForward(this@HomeRoleGateActivity)) promptForRole()
                else OnboardRouter.passBackThrough(this@HomeRoleGateActivity, this)
            }
        })

        ShellPromoConfig.renderSlot(
            activity = this,
            slot = ShellPromoConfig.onboardSlot(this, ShellPromoConfig.OnboardScreen.SET_DEFAULT),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        OnboardRouter.bindStepHeader(
            this, ShellPromoConfig.OnboardScreen.SET_DEFAULT, binding.root
        )

        // Fresh arrival at step 1 opens the system default-app page straight away, rather than
        // waiting for the CTA — the user's first decision is made in the place it can actually be
        // made. Spent by the first onResume (see below), and skipped on a rotation restore so the
        // page is not reopened underneath the user.
        if (savedInstanceState == null) autoOpenPending = true

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
            // Only a granted role advances. When the user taps Cancel or dismisses the system
            // ROLE_HOME dialog it returns RESULT_CANCELED and is still not the default launcher,
            // so we stay on this screen instead of walking on to the next onboarding page. (A
            // grant lands as isDefaultLauncher() above / onResume -> goHome; Skip stays explicit.)
            REQ_ROLE_HOME -> if (resultCode == RESULT_OK) goToNextStep() else askOnDecline()
        }
    }

    private var askedOnDecline = false

    /**
     * QRScanner's `set_home` moment: the user came back from the Home-app choice without picking
     * us, so both system surfaces are closed and nothing races the permission dialog. Whether
     * anything is asked is `permission_engine` — a rule listing `HomeRoleGateActivity`. Once per
     * visit, so a second decline does not ask again.
     */
    private fun askOnDecline() {
        if (askedOnDecline) return
        askedOnDecline = true
        PermitEngine.check(this)
    }

    override fun onResume() {
        super.onResume()

        // Back in front: take the coach hint down. Must run before the auto-open below, which
        // re-arms it — dismissing after would cancel the hint we just posted.
        SwipeCoachPrompt.dismiss()

        if (isDefaultLauncher()) {
            advanceAfterGrant()
            return
        }

        // First arrival only: open the system default-app page (with the hint) without a tap.
        // On the way back the existing onActivityResult path fires the ROLE_HOME dialog.
        if (autoOpenPending) {
            autoOpenPending = false
            openHomeSettings()
        }
    }

    /**
     * The role is held. `auto_next` (default on) moves on after `auto_next_delay_ms`; off waits for
     * the CTA, as in QRScanner.
     */
    private fun advanceAfterGrant() {
        val (auto, delayMs) = ShellPromoConfig.onboardAutoNext(this, ShellPromoConfig.OnboardScreen.SET_DEFAULT)
        if (!auto) return
        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) goHome()
        }, delayMs)
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
                imageHero,
                onboardingTitleVw,
                onboardingLeadVw,
                onboardingSetDefaultVw,
            )
        )
    }
}
