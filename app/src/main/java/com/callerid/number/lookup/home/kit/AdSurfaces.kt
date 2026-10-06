package com.callerid.number.lookup.home.kit

import android.app.Activity
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.screen.boot.LaunchGateActivity
import io.launcher.home.activities.LauncherPanel

/**
 * Where an unrequested ad moment (App Open, queued event page, consent form) may land: our own
 * full screens only, never an ad page, the splash, the call screens or a page over another app.
 */
object AdSurfaces {

    /** Simple class names. */
    val EXCLUDED = setOf(
        "ChargingStatusActivity", "PackageResultActivity", "RecentAdActivity", "PromoWebActivity",
        "TipSheetActivity", "OverlayGateActivity", "RoleCoachActivity", "FsiGateActivity",
        "RingScreenActivity", "ShellSurfaceScreen", "AdActivity", "AudienceNetworkActivity",
    )

    fun isExcluded(activity: Activity) = activity::class.java.simpleName in EXCLUDED

    fun isLanding(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed || isExcluded(activity)) return false
        return (activity is FrameActivity<*> && activity !is LaunchGateActivity) || activity is LauncherPanel
    }
}
