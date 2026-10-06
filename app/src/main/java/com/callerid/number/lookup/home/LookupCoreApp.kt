package com.callerid.number.lookup.home

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetHost
import android.content.Context
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.callerid.admesh.engine.AdsGate
import com.callerid.admesh.engine.PromoConfigLoader
import com.callerid.admesh.surface.AdPreloader
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.PromoAnchorActivity
import com.callerid.admesh.surface.OpenPromoRegistry.isAdAvailable
import com.callerid.admesh.surface.tally.ShellSurfaceScreen
import com.callerid.number.lookup.home.launcher.AppExitAd
import com.callerid.number.lookup.home.launcher.CallerLauncherAds
import com.callerid.number.lookup.home.launcher.CallerLauncherBridge
import com.callerid.number.lookup.home.launcher.SystemButtonAds
import com.callerid.number.lookup.home.launcher.UnlockAdWatcher
import io.launcher.home.activities.LauncherPanel
import io.launcher.home.api.LauncherRegistry
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.BaseConfig
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.screen.pkgresult.PackageEventWatcher
import com.callerid.number.lookup.home.screen.charging.ChargeEventWatcher
import com.callerid.number.lookup.home.screen.recent.RecentAdWatcher
import com.callerid.number.lookup.home.screen.boot.LaunchGateActivity
import com.callerid.number.lookup.home.kit.AdSurfaces
import com.callerid.number.lookup.home.kit.Analytics
import com.callerid.number.lookup.home.kit.CrashKeys
import com.callerid.number.lookup.home.kit.CrashSentry
import com.callerid.number.lookup.home.onboard.OnboardRouter
import com.callerid.number.lookup.home.kit.LogRail
import io.lighthouse.push.LightHouse
import io.lighthouse.push.LightHouseConfig
import io.lighthouse.push.extended.LightHouseRichPush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fossify.commons.helpers.SIDELOADING_FALSE
import com.callerid.admesh.engine.LiveConfigListener
import com.callerid.admesh.engine.LauncherPlacementAds
import com.callerid.admesh.surface.interstitial.FlowInterstitial

/**
 * The disclosure bullets, ours rather than the SDK's defaults.
 *
 * The first five are the SDK's own list, kept verbatim — dropping them to add ours would take
 * the analytics and advertising disclosures off the screen. The contacts line is the one this
 * app has to add for itself: [com.callerid.number.lookup.home.runtime.ContactSync] sends the
 * address book to our own backend, which no SDK-provided text covers.
 *
 * What that upload actually contains, so the wording stays true to it: contact names and phone
 * numbers, once per install, only after the user grants READ_CONTACTS, and never from a debug
 * build. Email, job title and website fields are sent empty.
 */
private val DATA_DISCLOSURE_BULLETS = listOf(
    "How you use the app — the screens you open and how long you spend — to measure and improve performance",
    "Usage and analytics data — to understand which features matter and make the app better",
    "Device and app identifiers — to group analytics correctly and keep your preferences in sync",
    "A general region from your network — to understand usage trends and show relevant content",
    "An advertising identifier — to show and measure ads that keep the app free",
    "Your saved contacts — names and phone numbers are sent to our servers over a secure connection " +
        "and matched against our caller database, so a number that calls you can be shown with a name. " +
        "This happens once, only after you allow access to contacts, and you can refuse without losing " +
        "the rest of the app.",
)

/** The SDK's own footer, with the contact upload named as ours rather than an ad partner's. */
private const val DATA_DISCLOSURE_FOOTER =
    "By tapping Agree & Continue you accept our Terms and confirm you're okay with this. Usage and " +
        "performance data — and some advertising data — is processed by the analytics and ad services " +
        "we use; your contacts are sent only to our own caller-lookup service. See our Privacy Policy " +
        "for details."

class LookupCoreApp : Application() , Application.ActivityLifecycleCallbacks,
    LifecycleObserver{
    private var currentActivity: Activity? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {

        lateinit var appContext: Context
            private set

        // v2: v1 shipped to test devices without the widget-host release below.
        private const val LEGACY_LAUNCHER_DROPPED = "legacy_launcher_dropped_v2"
        private const val LEGACY_WIDGET_HOST_ID = 12345
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        PromoVault.getInstance(this)
        Analytics.init(this)

        // Hears app install / removal and shows the result screen from whichever of our screens is
        // foreground (queuing it otherwise). Also seeds the package metadata cache.
        PackageEventWatcher.register(this)

        // Charger plugged in / pulled out → the charging screen. Inert unless `system_ads.charge` /
        // `discharge` is enabled in Remote Config.
        ChargeEventWatcher.register(this)

        // An ad after unlock, on our own home. Inert unless `launcher_config.unlock_ads.enabled`.
        UnlockAdWatcher.register(this)

        // Watches for the app being reopened from the Recents list. Inert unless `recent_ad.enabled`
        // is on in Remote Config — registering it costs a dormant install almost nothing.
        RecentAdWatcher.register(this)

        // Default-home "Next" ad owed when the role was granted from the Settings list.
        OnboardRouter.registerGrantAd(this)

        // Home / Back on the launcher home: `launcher_ads.system_buttons.{home,back}`.
        SystemButtonAds.register(this)

        BaseConfig.newInstance(this).appSideloadingStatus = SIDELOADING_FALSE

        dropLegacyLauncherState()
        LauncherRegistry.install(
            context = this,
            bridge = CallerLauncherBridge(),
            ads = CallerLauncherAds(),
        )

        LightHouseRichPush.setActivities(
            splashActivity = LaunchGateActivity::class.java,
            richPushActivity = ShellSurfaceScreen::class.java,
        )
        LightHouse.initialize(
            context = this,
            config = LightHouseConfig(
                apiKey = Veiled.s(BuildConfig.LH_API_KEY),
                baseUrl = Veiled.s(BuildConfig.LH_BASE_URL),
                
                attributionWaitMs = 5_000L,
                // rc_sync push: fetch + ingest now instead of waiting for the next foreground.
                useRemoteConfig = true,
                onRemoteConfigSync = { version ->
                    LogRail.log("LH_RemoteConfig", "rc_sync v=$version → fetching")
                    LiveConfigListener.refreshIfStale(this@LookupCoreApp, force = true)
                },
                dataDisclosureBullets = DATA_DISCLOSURE_BULLETS,
                dataDisclosureFooter = DATA_DISCLOSURE_FOOTER,
                richPushActivity = ShellSurfaceScreen::class.java,
            ),
        )
        
        
        // A device flagged as a reinstall is classified organic before the forced source is read;
        // clearing the flag lets the forced source win.
        if (PromoAnchorActivity.isAudienceForced) {
            LightHouse.debugForceFlagged(false)
            LightHouse.debugForceInstallSource(
                if (PromoAnchorActivity.DEBUG_AUDIENCE_MARKETING) "paid" else "organic"
            )
        }
        LightHouse.debugForceFlagged(false)
        LightHouse.debugForceInstallSource("paid")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                FirebaseApp.initializeApp(this@LookupCoreApp)

                PermitEngine.init(this@LookupCoreApp)
            } catch (e: Exception) {
                LogRail.error("CallerPhoneLookApp", "Firebase / PermitEngine init failed", e)
            }
        }

        // A config ingested while the launcher home is on screen takes effect at once.
        PromoConfigLoader.onApplied = {
            CrashKeys.update(this)
            (currentActivity as? LauncherPanel)?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { home ->
                if (!home.reapplyLauncherStyle()) home.onConfigUpdated()
            }
        }

        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    CrashKeys.update(this@LookupCoreApp)
                    handleAppForeground()
                    
                    LiveConfigListener.start(this@LookupCoreApp)
                    
                    LiveConfigListener.refreshIfStale(this@LookupCoreApp)
                }

                override fun onStop(owner: LifecycleOwner) {
                    
                    LiveConfigListener.stop()
                }
            }
        )

        CrashSentry.install(this) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    }

    /**
     * One-time, on the first start after the in-app launcher was replaced by the :launcher module.
     *
     * The old launcher kept its home grid in a hand-rolled SQLite `apps.db` (v5) and marked it built
     * with `was_home_screen_init` in fossify's shared prefs. The module uses the same file name for
     * its Room database and the same pref key, so left alone Room would try to adopt a schema it did
     * not create and the module would skip seeding a grid it never built.
     */
    private fun dropLegacyLauncherState() {
        val prefs = getSharedPrefs()
        if (prefs.getBoolean(LEGACY_LAUNCHER_DROPPED, false)) return
        deleteDatabase("apps.db")
        // Both launchers bind widgets under host id 12345. The ids the old one allocated are
        // still held by the system with no grid row pointing at them; release them all so the
        // module starts on a clean host (system_server NPEs on size updates for such ids).
        runCatching { AppWidgetHost(this, LEGACY_WIDGET_HOST_ID).deleteHost() }
        prefs.edit()
            .remove("was_home_screen_init")
            // Grid sizes come from the module's OEM layout match on first run, not the old defaults.
            .remove("home_column_count")
            .remove("home_row_count")
            .remove("drawer_column_count")
            .putBoolean(LEGACY_LAUNCHER_DROPPED, true)
            .apply()
    }

    private fun handleAppForeground() {

        LogRail.log("AppOpen", "handleAppForeground() called")

        val activity = currentActivity
        if (activity == null) {
            LogRail.log("AppOpen", "❌ No RESUMED activity")
            return
        }

        LogRail.log("AppOpen", "Activity = ${activity::class.java.simpleName}")

        if (activity.isFinishing || activity.isDestroyed) {
            LogRail.log("AppOpen", "❌ Activity invalid")
            return
        }

        // The splash owns its own ad path and must never be monetised on foreground.
        if (activity is LaunchGateActivity) {
            LogRail.log("AppOpen", "⛔ Excluded screen (splash)")
            return
        }

        // Every return refills what the session has used up.
        AdPreloader.topUp(activity)

        // Back from an ad's own click-out, or an ad is still up: never an App Open on top of it.
        if (AdsGate.isFullScreenShowing) {
            LogRail.log("AppOpen", "⛔ A full-screen ad is showing")
            OpenPromoRegistry.consumeExpectReturnAd()
            return
        }

        if (OpenPromoRegistry.skipNextAppOpenAd) {
            OpenPromoRegistry.skipNextAppOpenAd = false
            // A programmatic return is not monetised, so drop the launch flag too.
            OpenPromoRegistry.consumeExpectReturnAd()
            LogRail.log("AppOpen", "⛔ Skipped (app-initiated settings return)")
            return
        }

        // The reference's other_app_return: coming back from an app the launcher just opened is the
        // one time an ad shows on the launcher home. Here we run the config-driven app-drawer ad
        // sequence (App Open / interstitial / directlink fallback, counter-gated). The directlink, if
        // it wins the sequence, opens on the way back — never at the same time as the app it launched.
        if (OpenPromoRegistry.consumeExpectReturnAd()) {
            // Unlocked back onto the launcher: the unlock ad is this moment's one ad.
            if (UnlockAdWatcher.claimsForeground()) {
                LogRail.log("AppOpen", "⛔ Return ad skipped — unlock ad owns this foreground")
                return
            }
            LogRail.log("AppOpen", "↩ other_app_return")
            activity.runWhenWindowFocused {
                if (activity.isFinishing || activity.isDestroyed) return@runWhenWindowFocused
                // `gestures.app_exit` on: the app-click flow on the `appExit` placement. Off: the
                // older `launcher_ads.app_drawer` sequence, as before.
                if (!AppExitAd.run(activity)) ShellPromoConfig.runDrawerAdFlow(activity) {}
            }
            return
        }

        // Ordinary returns never monetise the always-foreground launcher shells (an App Open there
        // would fire on every glance at the home screen).
        if (activity is LauncherPanel || activity is ShellSurfaceScreen) {
            LogRail.log("AppOpen", "⛔ Excluded screen")
            return
        }

        // Ad pages, screens with their own ad, pages over another app: no App Open stacked on them.
        if (AdSurfaces.isExcluded(activity)) {
            LogRail.log("AppOpen", "⛔ Excluded screen (${activity::class.java.simpleName})")
            return
        }

        // Just unlocked onto our home: the unlock ad is this moment's one ad, not both.
        if (UnlockAdWatcher.claimsForeground()) {
            LogRail.log("AppOpen", "⛔ Unlock ad owns this foreground")
            return
        }

        // `appOpen_ad_flow` / an `appOpen_` link chain: the dynamic flow instead of the App Open ad.
        if (LauncherPlacementAds.hasOwnFlow(activity, "appOpen")) {
            if (!PromoVault.getInstance(activity).getBoolean("IsAdsON") ||
                !LauncherPlacementAds.placementEnabled(activity, "appOpen") ||
                OpenPromoRegistry.isShowingAd || FlowInterstitial.isInterShow
            ) return
            LogRail.log("AppOpen", "🚀 appOpen flow (appOpen_*)")
            activity.runWhenWindowFocused {
                if (activity.isFinishing || activity.isDestroyed) return@runWhenWindowFocused
                LauncherPlacementAds.showInterstitial(activity, "appOpen") {}
            }
            return
        }

        val adType = PromoKind.fromString(
            PromoVault.getInstance(activity).getString("IsAdType")
        )

        LogRail.log(
            "AppOpen",
            "AdType=$adType | available=${isAdAvailable} | showing=${OpenPromoRegistry.isShowingAd}"
        )

        if (
            adType == PromoKind.GOOGLE &&
            isAdAvailable &&
            !OpenPromoRegistry.isShowingAd
        ) {

            activity.runWhenWindowFocused {

                if (activity.isFinishing || activity.isDestroyed) return@runWhenWindowFocused

                LogRail.log("AppOpen", "🚀 Showing App Open Ad")

                OpenPromoRegistry.renderAdIfAvailable(
                    activity,
                    object : OpenPromoRegistry.OnShowAdCompleteListener {
                        override fun onShowAdComplete() {
                            LogRail.log("AppOpen", "✅ App Open Ad closed safely")
                        }
                    }
                )
            }

        } else {
            LogRail.log("AppOpen", "❌ Ad NOT shown (conditions failed)")
            // Not loaded (or expired): load now for the next return.
            if (adType == PromoKind.GOOGLE && !OpenPromoRegistry.isShowingAd) OpenPromoRegistry.loadAd(activity)
        }
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {

    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        // Consent + SDK start for sessions the splash never ran in (launcher after a reboot, a push).
        if (AdSurfaces.isLanding(activity)) {
            AdsGate.ensure(activity) {
                activity.window?.decorView?.post { AdPreloader.topUp(activity) }
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    override fun onActivityPaused(p0: Activity) {

    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {

    }

    override fun onActivityStarted(p0: Activity) {

    }

    override fun onActivityStopped(p0: Activity) {

    }

    private fun Activity.runWhenWindowFocused(action: () -> Unit) {
        if (hasWindowFocus()) {
            action()
        } else {
            val decorView = window.decorView
            val listener =
                object : ViewTreeObserver.OnWindowFocusChangeListener {
                    override fun onWindowFocusChanged(hasFocus: Boolean) {
                        if (hasFocus) {
                            decorView.viewTreeObserver
                                .removeOnWindowFocusChangeListener(this)
                            action()
                        }
                    }
                }
            decorView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        }
    }

}
