package com.callerid.number.lookup.home.permit

import com.callerid.number.lookup.home.kit.LogRail
import org.json.JSONObject

object FirebasePermitParser {

    private const val TAG = "PermitEngine"
    private const val ROOT_KEY = "permission_engine"

    fun parse(json: String?): List<PermitRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(json)

            val engine = root.optJSONObject(ROOT_KEY) ?: root

            val rules = ArrayList<PermitRule>()
            val keys = engine.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = engine.optJSONObject(key) ?: continue

                val activitiesArr = obj.optJSONArray("activities")
                val activities = if (activitiesArr != null) {
                    (0 until activitiesArr.length())
                        .mapNotNull { activitiesArr.optString(it).takeIf(String::isNotBlank) }
                } else emptyList()

                rules += PermitRule(
                    key = key,
                    enabled = obj.optBoolean("enabled", false),
                    activities = activities,
                    delayMs = obj.optLong("delay", 0L),
                    priority = obj.optInt("priority", Int.MAX_VALUE),
                    showOnce = obj.optBoolean("show_once", obj.optBoolean("showOnce", false)),
                )
            }
            LogRail.log(TAG, "Parsed ${rules.size} permission rule(s) from Remote Config")
            rules
        } catch (e: Exception) {
            LogRail.error(TAG, "Failed to parse permission_engine config", e)
            emptyList()
        }
    }
}
