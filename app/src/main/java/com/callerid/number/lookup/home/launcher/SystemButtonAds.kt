package com.callerid.number.lookup.home.launcher

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.callerid.admesh.engine.AdsGate
import com.callerid.admesh.engine.LauncherPlacementAds
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.kit.Analytics
import com.callerid.number.lookup.home.onboard.OnboardRouter

/**
 * An ad on the system Home / Back buttons on the launcher home: `launcher_ads.system_buttons.{home,back}`
 * (`enabled`, `ad_type`, `ads_counter`, `min_gap_sec`, optional `ad` flow). Blank `ad_type` runs the
 * button's placement chain.
 *
 * Home counts only when the user was already in this app (a second press on the launcher, or Home
 * from one of our screens); coming home from another app is [AppExitAd]'s moment. Back is a Back on
 * the bare workspace.
 */
object SystemButtonAds {

    private const val TAG = "SystemButtonAds"

    private const val HOME_PRESS_WINDOW_MS = 3_000L

    private const val LAST_SHOWN_PREFIX = "__system_button_last_"
    private const val COUNTER_PREFIX = "__system_button_counter_"

    /** Uptime of a `homekey` broadcast received while this app was in the foreground, or 0. */
    private var homeFromAppAt = 0L
    private var registered = false

    fun register(app: Application) {
        if (registered) return
        registered = true
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
                    if (intent.getStringExtra("reason") != "homekey") return
                    val foreground = ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                    homeFromAppAt = if (foreground) SystemClock.uptimeMillis() else 0L
                }
            },
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** From the launcher's onNewIntent, once it has unwound its drawer and panels. */
    fun onHome(activity: Activity, alreadyOnHome: Boolean) {
        val fromApp = homeFromAppAt != 0L && SystemClock.uptimeMillis() - homeFromAppAt <= HOME_PRESS_WINDOW_MS
        homeFromAppAt = 0L
        if (!alreadyOnHome && !fromApp) return
        run(activity, "home", "home")
    }

    fun onBack(activity: Activity) = run(activity, "back", "back")

    private fun run(activity: Activity, button: String, placement: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        Analytics.log("nav_${button}_press")
        val vault = PromoVault.getInstance(activity)
        if (!vault.getBoolean("IsAdsON")) return
        if (!OnboardRouter.wasOnboardingCompleted(activity)) return
        if (!LauncherPlacementAds.placementEnabled(activity, placement)) return

        val settings = ShellPromoConfig.systemButtonSettings(activity, button)
        if (!settings.enabled) return
        if (OpenPromoRegistry.isShowingAd || FlowInterstitial.isInterShow || UnlockAdWatcher.claimsForeground() ||
            AdsGate.isFullScreenShowing
        ) {
            log("$button: another ad is up — skipped")
            return
        }

        val last = vault.getLong(LAST_SHOWN_PREFIX + button, 0L)
        val now = System.currentTimeMillis()
        if (settings.minGapMs > 0L && last > 0L && now - last < settings.minGapMs) {
            log("$button: inside min_gap_sec — skipped")
            return
        }
        if (!ShellPromoConfig.counterDue(activity, COUNTER_PREFIX + button, settings.counter, "system_buttons.$button")) return

        vault.putLong(LAST_SHOWN_PREFIX + button, now)
        log("$button: showing ${settings.adType.ifEmpty { "placement chain" }}")
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            // `ad` { mode, sequence }: already paced by `ads_counter` above.
            settings.flow?.let { block ->
                val flow = ShellPromoConfig.flowFrom(activity, block, "system_buttons.$button.ad")
                ShellPromoConfig.runFlow(
                    activity, flow, COUNTER_PREFIX + button + "_flow", "__system_button_${button}_seq_ptr",
                    "system_buttons.$button.ad",
                ) {}
                return@post
            }
            when (settings.adType) {
                "", "inter", "interstitial" -> LauncherPlacementAds.showInterstitial(activity, placement) {}
                "full_native", "fullnative", "full" -> LauncherPlacementAds.showFullNative(activity, placement) {}
                else -> LauncherPlacementAds.showStep(activity, placement, settings.adType) {}
            }
        }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
