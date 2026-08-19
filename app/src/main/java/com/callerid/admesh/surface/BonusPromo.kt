package com.callerid.admesh.surface

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.interstitial.FlowInterstitial

class BonusPromo {

    companion object {
        private var loadedAd: RewardedAd? = null
        private var isLoading = false

        fun preload(context: Context) {
            val pref = PromoVault.getInstance(context)
            if (!pref.getBoolean("IsAdsON")) return
            if (loadedAd != null || isLoading) return

            val unitId = pref.getString("googleRewarded")
            if (unitId.isNullOrEmpty()) return

            isLoading = true
            Log.d("BonusPromo", "Preloading…")

            RewardedAd.load(
                context,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        loadedAd = ad
                        isLoading = false
                        Log.d("BonusPromo", "Preloaded OK")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadedAd = null
                        isLoading = false
                        Log.e("BonusPromo", "Preload failed: ${error.message}")
                    }
                }
            )
        }

        private fun showDirectLinkFallback(activity: Activity, onClosed: () -> Unit) {
            val pref = PromoVault.getInstance(activity)
            if (pref.getBoolean("IsCustomADS")) {
                Log.d("BonusPromo", "Falling back to DirectLink")
                FlowInterstitial.openDirectLink(activity) { onClosed() }
            } else {
                onClosed()
            }
        }
    }

    fun show(activity: Activity, onRewarded: () -> Unit) {
        val pref = PromoVault.getInstance(activity)

        if (!pref.getBoolean("IsAdsON")) {
            onRewarded()
            return
        }

        val ad = loadedAd
        if (ad == null) {
            Log.d("BonusPromo", "No preloaded ad — showing DirectLink fallback")
            preload(activity)
            showDirectLinkFallback(activity, onRewarded)
            return
        }

        loadedAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
                onRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e("BonusPromo", "Show failed: ${error.message} — showing DirectLink fallback")
                preload(activity)
                showDirectLinkFallback(activity, onRewarded)
            }
        }

        ad.show(activity) {
            Log.d("BonusPromo", "Reward earned: ${it.type} x${it.amount}")
        }
    }
}
