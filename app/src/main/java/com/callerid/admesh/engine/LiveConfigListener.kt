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

    /** The permission engine's own parameter — a change there matters as much as the blob. */
    private const val RC_PERMISSION_KEY = "permission_engine"

    /** When the last successful fetch landed. Local bookkeeping, not a config value. */
    private const val LAST_SYNC_KEY = "__cfg_last_sync"

    /** Remote Config's own say over the backstop window, in hours. */
    private const val SYNC_HOURS_KEY = "Config_Sync_Hrs"

    /** Used when [SYNC_HOURS_KEY] is absent or negative. */
    private const val DEFAULT_STALE_HOURS = 6L

    /** Coalesces the foreground backstop with anything else already fetching. */
    @Volatile
    private var fetchInFlight = false

    private var registration: com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration? = null

    fun start(context: Context) {
        val app = context.applicationContext
        stop()

        registration = runCatching {
            FirebaseRemoteConfig.getInstance().addOnConfigUpdateListener(
                object : ConfigUpdateListener {
                    override fun onUpdate(configUpdate: ConfigUpdate) {
                        LogRail.log(TAG, "config update: ${configUpdate.updatedKeys}")
                        
                        if (configUpdate.updatedKeys.none { it == blobKey() || it == RC_PERMISSION_KEY }) {
                            LogRail.log(TAG, "none of those is ${blobKey()} → ignored")
                            return
                        }
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

    /**
     * The backstop for the push channel: a device that was offline when the template was
     * published never gets the update, and for a launcher the next cold start can be days out.
     *
     * Throttled by [SYNC_HOURS_KEY], so the dozens of daily foregrounds that land inside the
     * window cost nothing.
     */
    fun refreshIfStale(context: Context, force: Boolean = false, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val vault = PromoVault.getInstance(app)
        val window = staleAfterMs(app)
        val age = System.currentTimeMillis() - vault.getLong(LAST_SYNC_KEY, 0L)

        
        if (!force && age in 0 until window) {
            LogRail.log(
                TAG,
                "config is ${age / 60_000}min old (window ${window / 60_000}min) — no fetch",
            )
            onDone?.invoke()
            return
        }
        if (fetchInFlight) {
            LogRail.log(TAG, "a fetch is already running — joined")
            onDone?.invoke()
            return
        }

        fetchInFlight = true
        val rc = FirebaseRemoteConfig.getInstance()
        
        RemoteConfigRules.withSettings(rc) {
            runCatching {
                rc.fetchAndActivate().addOnCompleteListener { task ->
                    fetchInFlight = false
                    LogRail.log(TAG, "backstop fetch success=${task.isSuccessful}")
                    if (task.isSuccessful) apply(app)
                    onDone?.invoke()
                }
            }.onFailure {
                fetchInFlight = false
                LogRail.error(TAG, "backstop fetch failed", it)
                onDone?.invoke()
            }
        }
    }

    /**
     * The freshness window in millis.
     *
     * `> 0` is that many hours. **`0` means no window at all** — every [refreshIfStale] call
     * fetches, which is once per foreground, so it is a testing / force-fresh setting rather
     * than something to ship. Absent reads back as `-1` (see `PromoVault.getInt`) and falls
     * back to [DEFAULT_STALE_HOURS], as does any other negative value, so a typo can never
     * turn into a fetch-every-resume loop by accident.
     */
    private fun staleAfterMs(context: Context): Long {
        val hours = PromoVault.getInstance(context).getInt(SYNC_HOURS_KEY)
        if (hours == 0) return 0L
        return (if (hours > 0) hours.toLong() else DEFAULT_STALE_HOURS) * 60L * 60L * 1000L
    }

    private fun blobKey(): String =
        if (BuildConfig.DEBUG) "DEBUG_GET_DATA_LIST" else "GET_DATA_LIST"

    private fun apply(context: Context) {
        val blobKey = blobKey()
        val raw = FirebaseRemoteConfig.getInstance().getString(blobKey)
        if (raw.isBlank()) {
            LogRail.log(TAG, "$blobKey empty after update — keeping the values already in use")
            return
        }

        runCatching {
            val response = JSONObject(raw)
            val vault = PromoVault.getInstance(context)
            val onMarketing = vault.getBoolean("OnMaketing")

            
            LogRail.log(
                TAG,
                "ingest ← $blobKey / ${if (onMarketing) "marketing" else "organic"}",
            )

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
            
            vault.putLong(LAST_SYNC_KEY, System.currentTimeMillis())
            LogRail.log(TAG, "applied live config (marketing=$onMarketing)")
        }.onFailure { LogRail.error(TAG, "live config could not be applied", it) }
    }
}
