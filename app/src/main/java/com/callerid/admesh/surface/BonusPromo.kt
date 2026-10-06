package com.callerid.admesh.surface

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.callerid.admesh.engine.AdsGate
import com.callerid.admesh.engine.PromoRevenueGauge
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.admesh.surface.interstitial.FullScreenWaiter
import com.callerid.admesh.surface.interstitial.InterLoader
import com.callerid.number.lookup.home.BuildConfig

/**
 * `onRewarded` runs only when the reward is earned; closing early runs `onNotEarned`. With no ad
 * to watch at all (ads off, no consent, no fill) the feature is not held back: `onRewarded` runs,
 * after the DirectLink fallback when `IsCustomADS` is on.
 */
class BonusPromo {

    companion object {
        private const val TAG = "BonusPromo"

        private const val MAX_AGE_MS = 60 * 60_000L

        private const val ON_DEMAND_TIMEOUT_MS = 8_000L

        private var loadedAd: RewardedAd? = null
        private var loadedAt = 0L
        private var isLoading = false
        private val waiting = mutableListOf<(RewardedAd?) -> Unit>()
        private val main = Handler(Looper.getMainLooper())

        private fun enabled(context: Context): Boolean {
            val pref = PromoVault.getInstance(context)
            return pref.getBoolean("IsAdsON") &&
                pref.getString("RewardedAds")?.trim()?.lowercase() != "false" &&
                AdsGate.canRequestAds(context)
        }

        private fun freshAd(): RewardedAd? {
            if (loadedAd != null && SystemClock.elapsedRealtime() - loadedAt > MAX_AGE_MS) {
                Log.d(TAG, "preloaded rewarded expired — dropped")
                loadedAd = null
            }
            return loadedAd
        }

        fun preload(context: Context) = load(context, null)

        private fun load(context: Context, onResult: ((RewardedAd?) -> Unit)?) {
            if (!enabled(context)) return onResult?.invoke(null) ?: Unit
            freshAd()?.let { ad -> return onResult?.invoke(ad) ?: Unit }
            onResult?.let { waiting += it }
            if (isLoading) return

            val unitId = PromoVault.getInstance(context).getString("googleRewarded")
            if (unitId.isNullOrEmpty()) {
                flush(null)
                return
            }

            isLoading = true
            Log.d(TAG, "Loading…")
            RewardedAd.load(
                context.applicationContext,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        loadedAd = ad
                        loadedAt = SystemClock.elapsedRealtime()
                        isLoading = false
                        Log.d(TAG, "Loaded OK")
                        flush(ad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadedAd = null
                        isLoading = false
                        Log.e(TAG, "Load failed: ${error.message}")
                        flush(null)
                    }
                }
            )
        }

        private fun flush(ad: RewardedAd?) {
            val callbacks = waiting.toList()
            waiting.clear()
            callbacks.forEach { runCatching { it(ad) } }
        }

        private fun showDirectLinkFallback(activity: Activity, onClosed: () -> Unit) {
            val pref = PromoVault.getInstance(activity)
            if (pref.getBoolean("IsCustomADS")) {
                Log.d(TAG, "Falling back to DirectLink")
                FlowInterstitial.openDirectLink(activity) { onClosed() }
            } else {
                onClosed()
            }
        }
    }

    /**
     * No ad ready → loaded on demand (spinner, [ON_DEMAND_TIMEOUT_MS]); still none → DirectLink
     * fallback, then [onRewarded]. Exactly one of [onRewarded] / [onNotEarned] runs, once.
     */
    fun show(activity: Activity, onNotEarned: () -> Unit = {}, onRewarded: () -> Unit) {
        if (!enabled(activity)) {
            onRewarded()
            return
        }

        val ready = freshAd()
        if (ready != null) {
            present(activity, ready, onNotEarned, onRewarded)
            return
        }

        var settled = false
        FullScreenWaiter.show(activity, InterLoader.enabled(PromoVault.getInstance(activity)))
        val timeout = Runnable {
            if (settled) return@Runnable
            settled = true
            FullScreenWaiter.hide()
            Log.d(TAG, "on-demand load timed out — fallback")
            showDirectLinkFallback(activity, onRewarded)
        }
        main.postDelayed(timeout, ON_DEMAND_TIMEOUT_MS)
        load(activity) { ad ->
            if (settled) return@load
            settled = true
            main.removeCallbacks(timeout)
            FullScreenWaiter.hide()
            if (ad != null && !activity.isFinishing && !activity.isDestroyed) {
                present(activity, ad, onNotEarned, onRewarded)
            } else {
                showDirectLinkFallback(activity, onRewarded)
            }
        }
    }

    private fun present(activity: Activity, ad: RewardedAd, onNotEarned: () -> Unit, onRewarded: () -> Unit) {
        loadedAd = null
        var earned = false
        var finished = false

        ad.setOnPaidEventListener { PromoRevenueGauge.reportPaidEvent(activity, it) }
        if (BuildConfig.DEBUG) PromoRevenueGauge.emitDebugRevenue(activity)

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                AdsGate.fullScreenDismissed()
                preload(activity)
                if (finished) return
                finished = true
                if (earned) onRewarded() else {
                    Log.d(TAG, "Closed before the reward was earned")
                    onNotEarned()
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdsGate.fullScreenDismissed()
                Log.e(TAG, "Show failed: ${error.message} — showing DirectLink fallback")
                preload(activity)
                if (finished) return
                finished = true
                showDirectLinkFallback(activity, onRewarded)
            }
        }

        AdsGate.fullScreenShown()
        ad.show(activity) {
            Log.d(TAG, "Reward earned: ${it.type} x${it.amount}")
            earned = true
        }
    }
}
