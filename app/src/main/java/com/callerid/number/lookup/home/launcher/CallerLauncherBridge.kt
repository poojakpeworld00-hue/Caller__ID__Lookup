package com.callerid.number.lookup.home.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.provider.Telephony
import androidx.fragment.app.Fragment
import com.callerid.admesh.engine.LauncherPlacementAds
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.LookupCoreApp
import com.callerid.number.lookup.home.kit.applyNativeAdTheme
import com.callerid.number.lookup.home.onboard.OnboardRouter
import com.callerid.number.lookup.home.onboard.SwipeCoachPrompt
import com.callerid.number.lookup.home.screen.AppHomeActivity
import com.callerid.number.lookup.home.store.LanguageRegistry
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.launcher.home.activities.LauncherPanel
import io.launcher.home.api.LauncherBridge
import io.launcher.home.api.LauncherKeys
import org.json.JSONObject

/** What only this app can answer for the launcher module. Pulled on demand, never cached. */
class CallerLauncherBridge : LauncherBridge {

    override fun configString(key: String, fallback: String): String {
        // The module's ads-helper slot entries are not used here: CallerLauncherAds reads this
        // app's own `launcher_ads` slot blocks instead, so the module keeps its defaults.
        // Only the drawer ad's row is read from it; the value comes from `launcher_ads`
        // (`app_drawer.bottom_native.position`, QRScanner's `drawerAdPosition`).
        if (key == LauncherKeys.ADS_CONFIG) {
            val row = ShellPromoConfig.drawerSlot(LookupCoreApp.appContext).position
            return """{"screenWiseAds":{"app_drawer":{"ad_row_position":$row}}}"""
        }
        if (key == LauncherKeys.LAUNCHER_CONFIG) return launcherConfig(fallback)
        return topLevel(key).ifBlank { fallback }
    }

    private fun topLevel(key: String): String =
        runCatching { FirebaseRemoteConfig.getInstance().getString(key) }.getOrNull().orEmpty()

    /**
     * `launcher_config`, merged key by key from two places:
     *
     *  - the top-level `launcher_config` parameter (audience-resolved here if it has
     *    `organic` / `marketing` blocks), as the base;
     *  - the copy inside the GET_DATA_LIST / DEBUG_GET_DATA_LIST audience block (as in QRScanner),
     *    already audience-resolved at ingestion, whose keys win.
     *
     * So a key left out of the data list (e.g. `panels`) is still controlled by the top-level parameter.
     */
    private fun launcherConfig(fallback: String): String {
        val base = runCatching { JSONObject(topLevel(LauncherKeys.LAUNCHER_CONFIG)) }.getOrNull()
            ?.let { root -> root.optJSONObject(if (isOrganicAudience()) "organic" else "marketing") ?: root }
        val overlay = PromoVault.getInstance(LookupCoreApp.appContext)
            .getString(LauncherKeys.LAUNCHER_CONFIG, "")
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (base == null && overlay == null) return fallback
        val merged = base ?: JSONObject()
        overlay?.let { o -> o.keys().forEach { merged.put(it, o.get(it)) } }
        return merged.toString()
    }

    /**
     * The dock's reserved slot. `launcher_config.dock_host_app: false` gives it to the default SMS
     * app instead of this app (the slot sits where a messaging app belongs). Asked on every resume,
     * and the launcher swaps the row when the answer changes, so the switch reaches existing installs;
     * a slot the user has since filled with something else is left alone.
     */
    override fun dockSlotPackage(context: Context): String? {
        val showHost = runCatching { JSONObject(launcherConfig("")).optBoolean("dock_host_app", true) }
            .getOrDefault(true)
        return if (showHost) context.applicationContext.packageName else Telephony.Sms.getDefaultSmsPackage(context)
    }

    /**
     * What our own icon opens from the launcher (dock, drawer, apps panel) when the host panel is
     * off: the app's home, not its LAUNCHER entry — that is the splash, which may route straight
     * back to this launcher. The launcher only runs once onboarding is done.
     */
    override fun hostLaunchComponent(context: Context): ComponentName =
        ComponentName(context, AppHomeActivity::class.java)

    override fun isOrganicAudience(): Boolean =
        !PromoVault.getInstance(LookupCoreApp.appContext).getBoolean("OnMaketing")

    override fun panelFragment(): Fragment = LauncherShellFragment()

    /**
     * Granting the Home role mid-onboarding makes the system start the launcher instead of the next
     * onboarding step. Finish the flow first; [OnboardRouter.goHome] starts the launcher again.
     */
    override fun onLauncherStart(activity: Activity): Boolean {
        if (OnboardRouter.resumeIfUnfinished(activity)) {
            activity.finish()
            return false
        }
        // The launcher is not one of our FrameActivities, so it inherits none of their setup.
        LanguageRegistry.applySaved(activity)
        activity.applyNativeAdTheme()
        (activity as? LauncherPanel)?.let { LauncherShellHost.attach(it) }
        LauncherPlacementAds.preload(activity)
        return true
    }

    /**
     * Another app was just opened from the launcher: the return from it is its own monetised moment
     * (the `launcher_ads.app_drawer` sequence, run by LookupCoreApp on the next foreground).
     */
    override fun onAppLaunched(packageName: String) {
        OpenPromoRegistry.expectReturnAd()
        ShellPromoConfig.preloadDrawerAds(LookupCoreApp.appContext)
        // The app-exit flow loads while the user is in the other app.
        LauncherPlacementAds.preload(LookupCoreApp.appContext)
    }

    override fun onLauncherResume(activity: Activity) {
        // Back from the system "Default home app" list: the coach-mark drawn over it comes down.
        SwipeCoachPrompt.dismiss()
        LanguageRegistry.applySaved(activity)
        LauncherShellHost.of(activity)?.homeShellController?.onHostResume()
        // Replaces whatever the last gesture used, so the next one has an ad ready.
        LauncherPlacementAds.preload(activity)
    }

    override fun isDebug(): Boolean = BuildConfig.DEBUG
}
