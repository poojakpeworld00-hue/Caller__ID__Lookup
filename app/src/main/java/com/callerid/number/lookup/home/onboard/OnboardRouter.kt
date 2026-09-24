package com.callerid.number.lookup.home.onboard

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.engine.ShellPromoConfig.OnboardScreen
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.kit.LogRail
import io.launcher.home.activities.LauncherPanel
import org.fossify.commons.extensions.getSharedPrefs
import io.launcher.home.extensions.isDefaultLauncher
import com.callerid.number.lookup.home.screen.locale.LanguageSelectActivity
import com.callerid.number.lookup.home.screen.slides.SlideIntroActivity

object OnboardRouter {

    private const val TAG = "OnboardRouter"

    const val EXTRA_LAUNCHER_ONBOARDING = "extra_launcher_onboarding"

    // Kept in fossify's shared "Prefs" file under the old launcher's key names, so an install that
    // finished (or is part-way through) onboarding before the launcher swap does not replay it.
    private const val WAS_ONBOARDING_COMPLETED = "was_onboarding_completed"
    private const val ONBOARDING_STEP = "onboarding_step"

    fun isOnboarding(activity: Activity): Boolean =
        activity.intent.getBooleanExtra(EXTRA_LAUNCHER_ONBOARDING, false)

    fun isOnboardingActive(activity: Activity): Boolean =
        isOnboarding(activity) || !wasOnboardingCompleted(activity)

    fun wasOnboardingCompleted(context: Context): Boolean = context.getSharedPrefs().getBoolean(WAS_ONBOARDING_COMPLETED, false)

    fun onboardingIntent(context: Context, target: Class<*>): Intent =
        Intent(context, target).putExtra(EXTRA_LAUNCHER_ONBOARDING, true)

    fun markOnboardingCompleted(context: Context) {
        context.getSharedPrefs().edit().putBoolean(WAS_ONBOARDING_COMPLETED, true).apply()
    }

    fun goHome(activity: Activity) {
        markOnboardingCompleted(activity)
        activity.startActivity(
            Intent(activity, LauncherPanel::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        activity.finish()
    }

    fun homeActivity(): Class<*> = LauncherPanel::class.java

    /**
     * The screens that carry a "Step n of m" indicator, in numbering order. The intro slides are
     * deliberately absent — they show their own page dots — so the count the user sees never
     * includes a step they will not be numbered through.
     */
    private val NUMBERED_STEPS = setOf(
        OnboardScreen.SET_DEFAULT,
        OnboardScreen.LANGUAGE,
        OnboardScreen.WELCOME,
    )

    /** 1-based position of [screen] and the numbered total, computed from the live order. */
    data class StepPosition(val index: Int, val total: Int)

    /**
     * Where [screen] sits among the numbered steps of the current onboarding order, or null when
     * it is not a numbered step (e.g. the intro slides, or a screen dropped from `onboarding.order`).
     * The total follows the order so removing a step from Remote Config re-counts the rest.
     */
    fun stepPosition(context: Context, screen: OnboardScreen): StepPosition? {
        val numbered = ShellPromoConfig.onboardOrder(context).filter { it in NUMBERED_STEPS }
        val pos = numbered.indexOf(screen)
        return if (pos < 0) null else StepPosition(pos + 1, numbered.size)
    }

    /**
     * Fills the shared "Step n of m" header (see `view_onboarding_step_header.xml`) found under
     * [root], and hides it when [screen] is not a numbered step. The pill text and the progress
     * fill are set here rather than in each screen so the three steps cannot disagree on the count.
     */
    fun bindStepHeader(context: Context, screen: OnboardScreen, root: View) {
        val header = root.findViewById<View>(R.id.onboarding_step_headerVw) ?: return
        val position = stepPosition(context, screen)
        if (position == null) {
            header.visibility = View.GONE
            return
        }
        header.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.onboarding_step_pillVw).text =
            context.getString(R.string.onboarding_step_counter, position.index, position.total)
        root.findViewById<ProgressBar>(R.id.onboarding_step_progressVw).progress =
            position.index * 100 / position.total
    }

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
        resolveFrom(context, context.getSharedPrefs().getInt(ONBOARDING_STEP, 0) + 1)

    fun resumeIfUnfinished(activity: Activity): Boolean {
        if (wasOnboardingCompleted(activity)) {
            return false
        }

        val next = resolveFrom(activity, activity.getSharedPrefs().getInt(ONBOARDING_STEP, 0))
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
                context.getSharedPrefs().edit().putInt(ONBOARDING_STEP, index).apply()
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

/** Drops this app's task from Recents; the onboarding steps are not somewhere to come back to. */
fun Activity.excludeAppFromRecents() {
    try {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        manager.appTasks.forEach { task -> task.setExcludeFromRecents(true) }
    } catch (e: Exception) {
        LogRail.error("Recents", "could not exclude task from recents", e)
    }
}
