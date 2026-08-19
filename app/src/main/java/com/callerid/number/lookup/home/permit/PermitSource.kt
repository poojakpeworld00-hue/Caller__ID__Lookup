package com.callerid.number.lookup.home.permit

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.kit.LogRail
import org.json.JSONObject
import com.callerid.admesh.engine.RemoteConfigRules

object PermitSource {

    private const val TAG = "PermitEngine"
    private const val RC_KEY = "permission_engine"

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

    fun rules(): List<PermitRule> = cached ?: reload()

    @Synchronized
    fun reload(): List<PermitRule> {
        val parsed = FirebasePermitParser.parse(rawConfig())
        cached = parsed
        return parsed
    }

    fun refreshFromRemote(onReady: (() -> Unit)? = null) {
        try {
            val rc = FirebaseRemoteConfig.getInstance()
            RemoteConfigRules.applyTo(rc)
            rc.fetchAndActivate().addOnCompleteListener { task ->
                LogRail.log(TAG, "Remote Config fetch success=${task.isSuccessful}")
                reload()
                onReady?.invoke()
            }
        } catch (e: Exception) {
            LogRail.error(TAG, "refreshFromRemote failed; using cached config", e)
            reload()
            onReady?.invoke()
        }
    }

    private fun rawConfig(): String {
        return try {
            val rc = FirebaseRemoteConfig.getInstance()

            rc.getString(RC_KEY).takeIf { it.isNotBlank() }?.let { return it }

            val blobKey = if (BuildConfig.DEBUG) "DEBUG_GET_DATA_LIST" else "GET_DATA_LIST"
            val blob = rc.getString(blobKey)
            if (blob.isNotBlank()) {
                val obj = JSONObject(blob)

                val root = audienceBlock(obj)
                if (root.has(RC_KEY)) return root.getJSONObject(RC_KEY).toString()
                if (obj.has(RC_KEY)) return obj.getJSONObject(RC_KEY).toString()
            }

            LogRail.log(TAG, "No remote permission_engine config; using compiled-in default")
            DEFAULT_CONFIG
        } catch (e: Exception) {
            LogRail.error(TAG, "Failed to read Remote Config; using compiled-in default", e)
            DEFAULT_CONFIG
        }
    }

    private fun audienceBlock(obj: JSONObject): JSONObject {
        val isMarketing = PromoVault.getOrNull()?.getBoolean("OnMaketing") ?: false
        val preferred = if (isMarketing) "marketing" else "organic"
        val fallback = if (isMarketing) "organic" else "marketing"
        obj.optJSONObject(preferred)?.let { return it }
        obj.optJSONObject(fallback)?.let { return it }
        return obj
    }
}
