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
import com.callerid.admesh.engine.AdsGate
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

    /** Counts Recents presses for `system_buttons.recents.ads_counter`. */
    private const val PRESS_COUNTER_KEY = "__recent_press_counter"

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
                topResumed = true
                // Back from the overview with the page switched off: the Recents ad runs now.
                if (flowOnReturn) {
                    flowOnReturn = false
                    runReturnFlow(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (top.get() === activity) {
                    topResumed = false
                    topPausedAt = SystemClock.uptimeMillis()
                }
                // The armed screen pausing is the app going to the overview — the moment to launch.
                if (armed.get() === activity) {
                    armed.clear()
                    if (armedForPlayStore) {
                        armedForPlayStore = false
                        launchPlayStoreFrom(activity)
                    } else if (!ShellPromoConfig.recentAdSettings(activity).pageOn) {
                        armFlowOnReturn(activity)
                    } else {
                        launchAdPage(activity)
                    }
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
                    "recentapps", "recent_apps" -> {
                        val app = context.applicationContext
                        if (!armPlayStoreIfFirstSession(app)) armIfAllowed(app)
                    }
                    // Home is an exit, not a return. Never fire on the way out.
                    "homekey" -> { armed.clear(); armedForPlayStore = false; flowOnReturn = false }
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
        com.callerid.number.lookup.home.kit.Analytics.log("nav_recents_press")

        // Only a screen in front right now: `top` outlives its pause (the launcher home is never
        // destroyed), so a Recents press inside another app must not arm it.
        val activity = top.get()?.takeIf { topResumed } ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        // The ad page must never arm the next one, and the event screens close themselves on
        // Recents (SystemDialogHelper) — an ad page launched from them would outlive them.
        if (activity is RecentAdActivity) return
        if (activity is com.callerid.number.lookup.home.screen.pkgresult.PackageResultActivity ||
            activity is com.callerid.number.lookup.home.screen.charging.ChargingStatusActivity
        ) return
        // The launcher home is not a card in Recents; the page would land in the home task instead.
        if (activity is io.launcher.home.activities.LauncherPanel) return

        if (!PromoVault.getInstance(context).getBoolean("IsAdsON")) return

        val settings = ShellPromoConfig.recentAdSettings(context)
        // `launcher_ads.system_buttons.recents` (enabled, ads_counter, min_gap_sec) when present,
        // else `recent_ad` (enabled, min_gap_sec).
        val button = if (ShellPromoConfig.hasSystemButton(context, "recents")) {
            ShellPromoConfig.systemButtonSettings(context, "recents")
        } else {
            null
        }
        if (!(button?.enabled ?: settings.enabled)) return

        val gapMs = button?.minGapMs ?: settings.minGapMs
        if (gapMs > 0L) {
            val vault = PromoVault.getInstance(context)
            val last = vault.getLong(GATE_KEY, 0L)
            val elapsed = System.currentTimeMillis() - last
            if (last > 0L && elapsed < gapMs) {
                log("throttled — ${elapsed}ms since the last one, need ${gapMs}ms")
                return
            }
        }
        if (button != null &&
            !ShellPromoConfig.counterDue(context, PRESS_COUNTER_KEY, button.counter, "system_buttons.recents")
        ) return

        armed = WeakReference(activity)
        log("armed ${activity::class.java.simpleName}")
    }

    /** Whether [top] is actually resumed — a paused screen will never deliver the pause we wait on. */
    private var topResumed = false

    /** Uptime [top] last paused. A Recents broadcast can trail the pause it caused by a few frames. */
    private var topPausedAt = 0L

    private const val LATE_BROADCAST_MS = 1_000L

    private var armedForPlayStore = false

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private const val PLAY_STORE_FIRED_KEY = "__recent_playstore_fired"

    /**
     * `recent_playstore` (QRScanner): a Recents press within the first `recent_playstore_window_sec`
     * (default 180) after install opens the Play Store home instead of the overview — once per
     * install. Returns true when it armed (or fired), so the recent-page ad is not armed on top.
     *
     * The window counts from the install time Android records, so an existing user updating the
     * app never gets it. Launched from the screen's `onPause` — the last moment its window still
     * counts as visible for the background-activity-launch check — or at once if the screen has
     * already paused, since no second pause is coming.
     */
    private fun armPlayStoreIfFirstSession(context: Context): Boolean {
        return runCatching {
        val vault = PromoVault.getInstance(context)
        if (!vault.getBoolean("recent_playstore")) return false
        if (vault.getBoolean(PLAY_STORE_FIRED_KEY)) return false
        // 0 (or less) = no window: the first Recents press ever, whenever it comes.
        val windowSec = vault.getInt("recent_playstore_window_sec", 180)
        if (windowSec > 0) {
            val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            if (System.currentTimeMillis() - installedAt > windowSec * 1000L) return false
        }
        val activity = top.get() ?: return false
        if (activity.isFinishing || activity.isDestroyed || armedForPlayStore) return false

        if (!topResumed) {
            // Paused long ago means the press was made in another app, where a background start is
            // refused on Android 10+; keep the one shot for a press made in the app.
            if (SystemClock.uptimeMillis() - topPausedAt > LATE_BROADCAST_MS) return false
            log("play store: top paused just now — starting")
            launchPlayStoreFrom(activity)
            return true
        }
        armedForPlayStore = true
        armed = WeakReference(activity)
        log("play store: armed on ${activity::class.java.simpleName}")
        // A Recents press that never pauses this screen must not leave it armed.
        main.postDelayed({
            if (armedForPlayStore) {
                armedForPlayStore = false
                armed.clear()
                log("play store: disarmed — no pause within 2s (one-shot kept)")
            }
        }, 2_000L)
        true
        }.getOrElse { Log.w(TAG, "recent_playstore failed", it); false }
    }

    /** The Play Store home. CLEAR_TASK so it opens at its home tab, not the page it was left on. */
    private fun launchPlayStoreFrom(activity: Activity) {
        runCatching {
            val intent = activity.packageManager.getLaunchIntentForPackage("com.android.vending")
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps"))
                    .setPackage("com.android.vending")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity.startActivity(intent)
            // Spent only once a start did not throw.
            PromoVault.getInstance(activity).putBoolean(PLAY_STORE_FIRED_KEY, true)
            com.callerid.number.lookup.home.kit.Analytics.log("recent_playstore_open")
            log("play store: fired (one-shot spent)")
        }.onFailure { Log.w(TAG, "launchPlayStoreFrom failed", it) }
    }

    /** Set when the Recents press paused a screen and the page is off: the ad runs on the next resume. */
    private var flowOnReturn = false

    private fun armFlowOnReturn(activity: Activity) {
        if (ShellPromoConfig.systemButtonSettings(activity, "recents").flow == null) {
            log("page off and no system_buttons.recents.ad — nothing to run on return")
            return
        }
        PromoVault.getInstance(activity).putLong(GATE_KEY, System.currentTimeMillis())
        // The return to the app is this ad's moment; the App Open ad must not stack on it.
        AdsGate.skipNextAppOpen()
        flowOnReturn = true
        log("page off — the Recents ad runs when the app is back in front")
    }

    private fun runReturnFlow(activity: Activity) {
        val block = ShellPromoConfig.systemButtonSettings(activity, "recents").flow ?: return
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            val flow = ShellPromoConfig.flowFrom(activity, block, "system_buttons.recents.ad")
            ShellPromoConfig.runFlow(
                activity, flow, "__recent_flow_count", "__recent_flow_seq_ptr", "system_buttons.recents.ad",
            ) {}
        }
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
