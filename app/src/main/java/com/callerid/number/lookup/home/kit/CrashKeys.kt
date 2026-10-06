package com.callerid.number.lookup.home.kit

import android.content.Context
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.launcher.home.extensions.isDefaultLauncher

/** Crashlytics custom keys (audience, ads, default home, build). Refreshed on foreground and config ingest. */
object CrashKeys {

    fun update(context: Context) {
        runCatching {
            val vault = PromoVault.getInstance(context)
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("audience", if (vault.getBoolean("OnMaketing")) "marketing" else "organic")
                setCustomKey("ads_on", vault.getBoolean("IsAdsON"))
                setCustomKey("ad_type", vault.getString("IsAdType").orEmpty())
                setCustomKey("default_home", runCatching { context.isDefaultLauncher() }.getOrDefault(false))
                setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            }
        }
    }
}
