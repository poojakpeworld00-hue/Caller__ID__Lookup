package com.callerid.number.lookup.home

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.PromoAnchorActivity
import com.callerid.admesh.surface.OpenPromoRegistry.isAdAvailable
import com.callerid.admesh.surface.tally.ShellSurfaceScreen
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity as LauncherHomeActivity
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.screen.pkgresult.PackageEventWatcher
import com.callerid.number.lookup.home.screen.recent.RecentAdWatcher
import com.callerid.number.lookup.home.screen.boot.LaunchGateActivity
import com.callerid.number.lookup.home.kit.CrashSentry
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
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        PromoVault.getInstance(this)

        // Hears app install / removal and shows the result screen from whichever of our screens is
        // foreground (queuing it otherwise). Also seeds the package metadata cache.
        PackageEventWatcher.register(this)

        // Watches for the app being reopened from the Recents list. Inert unless `recent_ad.enabled`
        // is on in Remote Config — registering it costs a dormant install almost nothing.
        RecentAdWatcher.register(this)

        config.appSideloadingStatus = SIDELOADING_FALSE

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
                
                dataDisclosureBullets = DATA_DISCLOSURE_BULLETS,
                dataDisclosureFooter = DATA_DISCLOSURE_FOOTER,
                richPushActivity = ShellSurfaceScreen::class.java,
            ),
        )
        
        
        if (PromoAnchorActivity.isAudienceForced) {
            LightHouse.debugForceInstallSource(
                if (PromoAnchorActivity.DEBUG_AUDIENCE_MARKETING) "paid" else "organic"
            )
        }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                FirebaseApp.initializeApp(this@LookupCoreApp)

                PermitEngine.init(this@LookupCoreApp)
            } catch (e: Exception) {
                LogRail.log("CallerPhoneLookApp", "LightHouse init failed: ${e.message}")
            }
        }

        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
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
            LogRail.log("AppOpen", "↩ other_app_return — running drawer ad sequence")
            activity.runWhenWindowFocused {
                if (activity.isFinishing || activity.isDestroyed) return@runWhenWindowFocused
                ShellPromoConfig.runDrawerAdFlow(activity) {}
            }
            return
        }

        // Ordinary returns never monetise the always-foreground launcher shells (an App Open there
        // would fire on every glance at the home screen).
        if (activity is LauncherHomeActivity || activity is ShellSurfaceScreen) {
            LogRail.log("AppOpen", "⛔ Excluded screen")
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
        }
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {

    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity

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
