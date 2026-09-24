package com.callerid.number.lookup.home.launcher

import android.app.Activity
import androidx.fragment.app.Fragment
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.LookupCoreApp
import com.callerid.number.lookup.home.kit.applyNativeAdTheme
import com.callerid.number.lookup.home.onboard.OnboardRouter
import com.callerid.number.lookup.home.onboard.SwipeCoachPrompt
import com.callerid.number.lookup.home.store.LanguageRegistry
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.launcher.home.activities.LauncherPanel
import io.launcher.home.api.LauncherBridge
import io.launcher.home.api.LauncherKeys

/** What only this app can answer for the launcher module. Pulled on demand, never cached. */
class CallerLauncherBridge : LauncherBridge {

    override fun configString(key: String, fallback: String): String {
        // The module's ads-helper slot entries are not used here: CallerLauncherAds reads this
        // app's own `launcher_ads` slot blocks instead, so the module keeps its defaults.
        if (key == LauncherKeys.ADS_CONFIG) return fallback
        return runCatching { FirebaseRemoteConfig.getInstance().getString(key) }
            .getOrNull()
            .orEmpty()
            .ifBlank { fallback }
    }

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
        return true
    }

    override fun onLauncherResume(activity: Activity) {
        // Back from the system "Default home app" list: the coach-mark drawn over it comes down.
        SwipeCoachPrompt.dismiss()
        LanguageRegistry.applySaved(activity)
        LauncherShellHost.of(activity)?.homeShellController?.onHostResume()
    }

    override fun isDebug(): Boolean = BuildConfig.DEBUG
}
