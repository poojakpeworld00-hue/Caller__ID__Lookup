package com.callerid.admesh.engine

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.callerid.number.lookup.home.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object AdsGate {

    private const val TAG = "AdsGate"

    private const val FULL_SCREEN_MAX_MS = 5 * 60_000L

    private const val SKIP_APP_OPEN_MS = 10 * 60_000L

    private val sdkStarted = AtomicBoolean(false)
    private val consentInFlight = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val onReady = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var isSdkReady: Boolean = false
        private set

    fun canRequestAds(context: Context): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(context.applicationContext).canRequestAds()
    }.getOrDefault(false)

    fun startSdk(context: Context): Boolean {
        val app = context.applicationContext
        if (!canRequestAds(app)) {
            log("SDK not started: consent does not allow ad requests")
            return false
        }
        if (sdkStarted.getAndSet(true)) return true
        executor.execute {
            // Audience Network is the fallback network; start it behind the same consent gate.
            runCatching { com.facebook.ads.AudienceNetworkAds.initialize(app) }
                .onFailure { Log.w(TAG, "AudienceNetworkAds.initialize failed", it) }
            runCatching {
                MobileAds.initialize(app) {
                    isSdkReady = true
                    log("Mobile Ads SDK ready")
                    onReady.forEach { runCatching(it) }
                    onReady.clear()
                }
            }.onFailure {
                sdkStarted.set(false)
                Log.e(TAG, "MobileAds.initialize failed", it)
            }
        }
        return true
    }

    fun whenReady(block: () -> Unit) {
        if (isSdkReady) block() else onReady += block
    }

    fun ensure(activity: Activity, onStarted: (() -> Unit)? = null) {
        if (sdkStarted.get()) return
        if (startSdk(activity)) {
            onStarted?.let { whenReady(it) }
            return
        }
        if (!consentInFlight.compareAndSet(false, true)) return
        runCatching {
            GmaConsentRegistry.getInstance(activity.applicationContext).gatherConsent(activity) { error ->
                consentInFlight.set(false)
                if (error != null) log("consent update failed: ${error.message}")
                if (startSdk(activity)) onStarted?.let { whenReady(it) }
            }
        }.onFailure {
            consentInFlight.set(false)
            Log.w(TAG, "consent request failed", it)
        }
    }

    @Volatile
    private var fullScreenSince = 0L

    fun fullScreenShown() {
        fullScreenSince = SystemClock.uptimeMillis()
    }

    fun fullScreenDismissed() {
        fullScreenSince = 0L
    }

    val isFullScreenShowing: Boolean
        get() {
            val since = fullScreenSince
            return since != 0L && SystemClock.uptimeMillis() - since < FULL_SCREEN_MAX_MS
        }

    @Volatile
    private var skipAppOpenSince = 0L

    fun skipNextAppOpen() {
        skipAppOpenSince = SystemClock.uptimeMillis()
    }

    val isSkippingAppOpen: Boolean
        get() {
            val since = skipAppOpenSince
            return since != 0L && SystemClock.uptimeMillis() - since < SKIP_APP_OPEN_MS
        }

    fun clearSkipAppOpen() {
        skipAppOpenSince = 0L
    }

    fun consumeSkipAppOpen(): Boolean {
        val since = skipAppOpenSince
        skipAppOpenSince = 0L
        return since != 0L && SystemClock.uptimeMillis() - since < SKIP_APP_OPEN_MS
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
