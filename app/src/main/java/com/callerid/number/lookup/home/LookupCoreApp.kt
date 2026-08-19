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
import androidx.multidex.MultiDex
import com.google.firebase.FirebaseApp
import com.callerid.admesh.model.PromoKind
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.OpenPromoRegistry.isAdAvailable
import com.callerid.admesh.surface.tally.ShellSurfaceScreen
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity as LauncherHomeActivity
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.permit.PermitEngine
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

        MultiDex.install(this)
        PromoVault.getInstance(this)

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
                richPushActivity = ShellSurfaceScreen::class.java,
            ),
        )
        CoroutineScope(Dispatchers.Main).launch {
            try {
                FirebaseApp.initializeApp(this@LookupCoreApp)

                PermitEngine.init(this@LookupCoreApp)

                LiveConfigListener.start(this@LookupCoreApp)
            } catch (e: Exception) {
                LogRail.log("CallerPhoneLookApp", "LightHouse init failed: ${e.message}")
            }
        }

        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    handleAppForeground()
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

        if (
            activity is LaunchGateActivity ||
            activity is LauncherHomeActivity ||
            activity is ShellSurfaceScreen
        ) {
            LogRail.log("AppOpen", "⛔ Excluded screen")
            return
        }

        if (OpenPromoRegistry.skipNextAppOpenAd) {
            OpenPromoRegistry.skipNextAppOpenAd = false
            LogRail.log("AppOpen", "⛔ Skipped (app-initiated settings return)")
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
