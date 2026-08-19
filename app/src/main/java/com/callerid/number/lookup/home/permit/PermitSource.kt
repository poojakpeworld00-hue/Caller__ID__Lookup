package com.callerid.number.lookup.home.permit

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.kit.LogRail
import org.json.JSONObject
import com.callerid.admesh.engine.RemoteConfigRules

/**
 * Single access point for the engine's configuration.
 *
 * Reads the `permission_engine` block from Firebase Remote LauncherPrefs and caches the
 * parsed [PermitRule]s in memory. Remote LauncherPrefs already persists activated
 * values to disk, so the last-known config is available immediately on the next
 * cold start — the engine works even before a fresh fetch completes.
 *
 * LauncherPrefs resolution order (first non-empty wins):
 *  1. A dedicated Remote LauncherPrefs parameter named `permission_engine`.
 *  2. The `permission_engine` key inside the app's existing data blob
 *     (`GET_DATA_LIST` / `DEBUG_GET_DATA_LIST`), so no new RC parameter is
 *     strictly required.
 */
object PermitSource {

    private const val TAG = "PermitEngine"
    private const val RC_KEY = "permission_engine"

    /**
     * Compiled-in safety-net configuration. Used **only** when Remote LauncherPrefs
     * supplies no `permission_engine` value (before the first successful fetch,
     * or if the parameter is never set on the server). Any Remote LauncherPrefs value
     * completely overrides this.
     *
     * Notification + phone state are driven by the engine and triggered
     * explicitly — from the splash flow (PromoAnchorActivity) and from the permission
     * bottom sheet's Continue button on AppHomeActivity. So the default targets
     * both `LaunchGateActivity` and `AppHomeActivity` with no delay (the trigger point
     * already picks the moment). Remote LauncherPrefs fully overrides this.
     * `phone_state` stays subject to the `HD_VBC_Show` gate.
     */
    private const val DEFAULT_CONFIG = """
        {
          "permission_engine": {
            "notification": { "enabled": true, "activities": ["SplashActivity", "HomeBoardActivity"], "delay": 0, "priority": 1 },
            "phone_state":  { "enabled": true, "activities": ["SplashActivity", "HomeBoardActivity"], "delay": 0, "priority": 2 }
          }
        }
    """

    @Volatile
    private var cached: List<PermitRule>? = null

    /** Returns cached rules, parsing from Remote LauncherPrefs on first access. */
    fun rules(): List<PermitRule> = cached ?: reload()

    /** Re-reads (and re-parses) the currently activated Remote LauncherPrefs values. */
    @Synchronized
    fun reload(): List<PermitRule> {
        val parsed = FirebasePermitParser.parse(rawConfig())
        cached = parsed
        return parsed
    }

    /**
     * Triggers a fresh Remote LauncherPrefs fetch, then refreshes the cache. Safe to
     * call once at startup; failures fall back silently to cached/activated
     * values so the flow is never blocked.
     */
    fun refreshFromRemote(onReady: (() -> Unit)? = null) {
        try {
            val rc = FirebaseRemoteConfig.getInstance()
            RemoteConfigRules.applyTo(rc)
            rc.fetchAndActivate().addOnCompleteListener { task ->
                LogRail.log(TAG, "Remote LauncherPrefs fetch success=${task.isSuccessful}")
                reload()
                onReady?.invoke()
            }
        } catch (e: Exception) {
            LogRail.error(TAG, "refreshFromRemote failed; using cached config", e)
            reload()
            onReady?.invoke()
        }
    }

    /** Resolves the raw JSON for the engine from Remote LauncherPrefs (see class doc). */
    private fun rawConfig(): String {
        return try {
            val rc = FirebaseRemoteConfig.getInstance()

            // 1) Dedicated parameter.
            rc.getString(RC_KEY).takeIf { it.isNotBlank() }?.let { return it }

            // 2) Nested inside the app's existing data blob.
            val blobKey = if (BuildConfig.DEBUG) "DEBUG_GET_DATA_LIST" else "GET_DATA_LIST"
            val blob = rc.getString(blobKey)
            if (blob.isNotBlank()) {
                val obj = JSONObject(blob)
                // Top-level audience split: descend into marketing/organic first,
                // then fall back to the flat top level (legacy config).
                val root = audienceBlock(obj)
                if (root.has(RC_KEY)) return root.getJSONObject(RC_KEY).toString()
                if (obj.has(RC_KEY)) return obj.getJSONObject(RC_KEY).toString()
            }

            // Nothing configured remotely → fall back to the compiled-in default.
            LogRail.log(TAG, "No remote permission_engine config; using compiled-in default")
            DEFAULT_CONFIG
        } catch (e: Exception) {
            LogRail.error(TAG, "Failed to read Remote LauncherPrefs; using compiled-in default", e)
            DEFAULT_CONFIG
        }
    }

    /** marketing/organic sub-object (by OnMaketing), else the flat blob. */
    private fun audienceBlock(obj: JSONObject): JSONObject {
        val isMarketing = PromoVault.getOrNull()?.getBoolean("OnMaketing") ?: false
        val preferred = if (isMarketing) "marketing" else "organic"
        val fallback = if (isMarketing) "organic" else "marketing"
        obj.optJSONObject(preferred)?.let { return it }
        obj.optJSONObject(fallback)?.let { return it }
        return obj
    }
}
