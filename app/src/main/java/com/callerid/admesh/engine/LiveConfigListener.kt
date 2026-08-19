package com.callerid.admesh.engine

import android.content.Context
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.permit.PermitSource
import com.callerid.number.lookup.home.kit.LogRail
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import org.json.JSONObject

/**
 * Applies Remote LauncherPrefs changes while the app is running.
 *
 * Without this the blob is only read at splash, so a value published in the console reaches a
 * device on its next cold start — which for a launcher can be days, since the home screen is
 * rarely killed.
 *
 * Realtime Remote LauncherPrefs pushes the change instead: [ConfigUpdateListener.onUpdate] fires,
 * the new values are activated, and the same ingest the splash runs re-populates PromoVault, so
 * every gate that reads from it — ad slots, the permission engine, the settings rows — picks
 * the change up on its next read.
 *
 * The install-referrer step is deliberately not re-run: the audience a user landed in does not
 * change because a config value did, and re-running it would re-POST attribution.
 */
object LiveConfigListener {

    private const val TAG = "LiveConfig"

    private var registration: com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration? = null

    /** Idempotent: a second call replaces the previous registration rather than stacking one. */
    fun start(context: Context) {
        val app = context.applicationContext
        stop()

        registration = runCatching {
            FirebaseRemoteConfig.getInstance().addOnConfigUpdateListener(
                object : ConfigUpdateListener {
                    override fun onUpdate(configUpdate: ConfigUpdate) {
                        LogRail.log(TAG, "config update: ${configUpdate.updatedKeys}")
                        FirebaseRemoteConfig.getInstance().activate()
                            .addOnCompleteListener { apply(app) }
                    }

                    override fun onError(error: FirebaseRemoteConfigException) {
                        // Not fatal: the splash fetch still applies the change on next launch.
                        LogRail.error(TAG, "realtime updates unavailable", error)
                    }
                }
            )
        }.onFailure { LogRail.error(TAG, "could not register for config updates", it) }
            .getOrNull()
    }

    fun stop() {
        runCatching { registration?.remove() }
        registration = null
    }

    private fun apply(context: Context) {
        val blobKey = if (BuildConfig.DEBUG) "DEBUG_GET_DATA_LIST" else "GET_DATA_LIST"
        val raw = FirebaseRemoteConfig.getInstance().getString(blobKey)
        if (raw.isBlank()) {
            LogRail.log(TAG, "$blobKey empty after update — keeping the values already in use")
            return
        }

        runCatching {
            val response = JSONObject(raw)
            val vault = PromoVault.getInstance(context)
            val onMarketing = vault.getBoolean("OnMaketing")

            vault.putString("GET_DATA_RAW", raw)
            vault.putBoolean(
                "__cfg_audience_split",
                response.has("marketing") || response.has("organic"),
            )
            PromoConfigLoader.ingest(
                context,
                PromoConfigLoader.audienceRoot(response, onMarketing),
            )

            // The permission engine caches its own parsed copy of the same blob.
            PermitSource.reload()
            LogRail.log(TAG, "applied live config (marketing=$onMarketing)")
        }.onFailure { LogRail.error(TAG, "live config could not be applied", it) }
    }
}
