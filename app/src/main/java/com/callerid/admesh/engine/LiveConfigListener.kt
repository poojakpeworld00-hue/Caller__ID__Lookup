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

object LiveConfigListener {

    private const val TAG = "LiveConfig"

    private var registration: com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration? = null

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
            PromoConfigLoader.absorb(
                context,
                PromoConfigLoader.audienceBlock(response, onMarketing),
            )

            PermitSource.reload()
            LogRail.log(TAG, "applied live config (marketing=$onMarketing)")
        }.onFailure { LogRail.error(TAG, "live config could not be applied", it) }
    }
}
