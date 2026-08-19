package com.callerid.number.lookup.home.shell.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.engine.ShellPromoConfig.OnboardScreen
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity
import com.callerid.number.lookup.home.shell.screens.HomeRoleGateActivity
import com.callerid.number.lookup.home.shell.screens.HelloStepActivity
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.shell.ext.isDefaultLauncher
import com.callerid.number.lookup.home.screen.locale.LanguageSelectActivity
import com.callerid.number.lookup.home.screen.slides.SlideIntroActivity

object OnboardRouter {

    private const val TAG = "OnboardRouter"

    const val EXTRA_LAUNCHER_ONBOARDING = "extra_launcher_onboarding"

    fun isOnboarding(activity: Activity): Boolean =
        activity.intent.getBooleanExtra(EXTRA_LAUNCHER_ONBOARDING, false)

    fun isOnboardingActive(activity: Activity): Boolean =
        isOnboarding(activity) || !wasOnboardingCompleted(activity)

    fun wasOnboardingCompleted(context: Context): Boolean = context.config.wasOnboardingCompleted

    fun onboardingIntent(context: Context, target: Class<*>): Intent =
        Intent(context, target).putExtra(EXTRA_LAUNCHER_ONBOARDING, true)

    fun markOnboardingCompleted(context: Context) {
        context.config.wasOnboardingCompleted = true
    }

    fun goHome(activity: Activity) {
        markOnboardingCompleted(activity)
        activity.startActivity(
            Intent(activity, HomeBoardActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        activity.finish()
    }

    fun homeActivity(): Class<*> = HomeBoardActivity::class.java

    fun firstScreen(context: Context): Class<*> = resolveFrom(context, 0)

    fun advance(activity: Activity, skipRest: Boolean = false) {
        if (skipRest) {
            log("skipping the rest of the order")
            return goHome(activity)
        }

        val next = nextActivity(activity)
        if (next == homeActivity()) {
            return goHome(activity)
        }

        activity.startActivity(onboardingIntent(activity, next))
        activity.finish()
    }

    fun nextActivity(context: Context): Class<*> =
        resolveFrom(context, context.config.onboardingStep + 1)

    fun resumeIfUnfinished(activity: Activity): Boolean {
        if (wasOnboardingCompleted(activity)) {
            return false
        }

        val next = resolveFrom(activity, activity.config.onboardingStep)
        if (next == homeActivity()) {
            return false
        }

        log("home reached mid-run → resuming onboarding")
        activity.startActivity(onboardingIntent(activity, next))
        return true
    }

    private fun resolveFrom(context: Context, from: Int): Class<*> {
        val order = ShellPromoConfig.onboardOrder(context)
        var index = from

        while (index < order.size) {
            val screen = order[index]
            if (isApplicable(context, screen)) {
                context.config.onboardingStep = index
                log("→ [$index] ${screen.key}")
                return activityFor(screen)
            }

            if (screen == OnboardScreen.SET_DEFAULT && alreadyGranted(context)) {
                log("[$index] ${screen.key}: role already held → home")
                break
            }

            log("skipping [$index] ${screen.key}")
            index++
        }

        log("order finished → home")
        markOnboardingCompleted(context)
        return homeActivity()
    }

    private fun isApplicable(context: Context, screen: OnboardScreen): Boolean = when (screen) {
        OnboardScreen.SET_DEFAULT -> {
            val step = ShellPromoConfig.defaultBoardStep(context)
            step.enabled && !(step.skipIfDefault && context.isDefaultLauncher())
        }

        else -> true
    }

    private fun alreadyGranted(context: Context): Boolean {
        val step = ShellPromoConfig.defaultBoardStep(context)
        return step.enabled && step.skipIfDefault && step.skipRestOnGrant &&
                context.isDefaultLauncher()
    }

    private fun activityFor(screen: OnboardScreen): Class<*> = when (screen) {
        OnboardScreen.WELCOME -> HelloStepActivity::class.java
        OnboardScreen.SET_DEFAULT -> HomeRoleGateActivity::class.java
        OnboardScreen.INTRO -> SlideIntroActivity::class.java
        OnboardScreen.LANGUAGE -> LanguageSelectActivity::class.java
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
