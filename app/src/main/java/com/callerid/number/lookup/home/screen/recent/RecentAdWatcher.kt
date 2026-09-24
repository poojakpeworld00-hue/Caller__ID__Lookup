package com.callerid.number.lookup.home.screen.recent

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.number.lookup.home.BuildConfig
import java.lang.ref.WeakReference

/**
 * Shows [RecentAdActivity] when the user reopens the app from the **Recents / overview** list.
 *
 * ## Catching the "opened the overview" moment
 *
 * The platform has no lifecycle callback for it. What it does give is the `CLOSE_SYSTEM_DIALOGS`
 * broadcast, whose `reason` extra is `"recentapps"` when the Recents key is pressed and `"homekey"`
 * for Home. So:
 *
 *  1. On `recentapps`, if every gate passes, the screen on top is *armed* (remembered).
 *  2. When that armed screen **pauses** — the app going to the overview — the ad page is launched
 *     into its task with `SINGLE_TOP | REORDER_TO_FRONT`, so tapping the app's card in Recents
 *     lands on the ad rather than where the user left off.
 *  3. On `homekey` the arming is cleared. Home is a deliberate exit, not a return, and must not fire.
 *
 * ## Off unless Remote Config says otherwise
 *
 * `IsAdsON`, `recent_ad.enabled` and the throttle are all checked at arm time, so a disabled config
 * arms nothing and the launch path never runs at all. Registering the callbacks and the receiver
 * costs a dormant install almost nothing.
 *
 * ## Two things to know before turning it on
 *
 * This is an **out-of-context ad**: it appears on a resume the user did not tap for, which Google
 * Play's Disruptive Ads policy treats as a suspension risk. And `CLOSE_SYSTEM_DIALOGS` is a signal
 * the platform has been steadily narrowing, so it may simply stop arriving. Both are why it ships
 * disabled.
 */
object RecentAdWatcher {

    private const val TAG = "RecentAd"

    /** Throttle key, so a burst of Recents presses cannot show the page repeatedly. */
    private const val GATE_KEY = "__recent_ad_last_shown"

    /** `CLOSE_SYSTEM_DIALOGS` can arrive in bursts; ignore anything inside this window. */
    private const val ARM_DEBOUNCE_MS = 700L

    /** The current, shown Activity — the one that would be armed. */
    private var top = WeakReference<Activity>(null)

    /** The screen armed by a Recents press, launched from when it pauses. */
    private var armed = WeakReference<Activity>(null)

    private var lastArmUptime = 0L
    private var registered = false

    /** Registers the lifecycle callbacks and the Recents receiver. Idempotent. */
    fun register(app: Application) {
        if (registered) return
        registered = true

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                top = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                // The armed screen pausing is the app going to the overview — the moment to launch.
                if (armed.get() === activity) {
                    armed.clear()
                    launchAdPage(activity)
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (top.get() === activity) top.clear()
                if (armed.get() === activity) armed.clear()
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
                when (intent.getStringExtra("reason")) {
                    "recentapps", "recent_apps" -> armIfAllowed(context.applicationContext)
                    // Home is an exit, not a return. Never fire on the way out.
                    "homekey" -> armed.clear()
                }
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** Arms the screen on top, if every gate passes. Everything is checked here, not at launch. */
    private fun armIfAllowed(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastArmUptime < ARM_DEBOUNCE_MS) return
        lastArmUptime = now

        val activity = top.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        // The ad page must never arm the next one.
        if (activity is RecentAdActivity) return

        if (!PromoVault.getInstance(context).getBoolean("IsAdsON")) return

        val settings = ShellPromoConfig.recentAdSettings(context)
        if (!settings.enabled) return

        if (settings.minGapMs > 0L) {
            val vault = PromoVault.getInstance(context)
            val last = vault.getLong(GATE_KEY, 0L)
            val elapsed = System.currentTimeMillis() - last
            if (last > 0L && elapsed < settings.minGapMs) {
                log("throttled — ${elapsed}ms since the last one, need ${settings.minGapMs}ms")
                return
            }
        }

        armed = WeakReference(activity)
        log("armed ${activity::class.java.simpleName}")
    }

    private fun launchAdPage(from: Activity) {
        runCatching {
            PromoVault.getInstance(from).putLong(GATE_KEY, System.currentTimeMillis())
            from.startActivity(
                Intent(from, RecentAdActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
            log("launched the recent page")
        }.onFailure { Log.e(TAG, "could not launch the recent page", it) }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
