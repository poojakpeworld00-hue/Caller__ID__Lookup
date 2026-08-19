package com.callerid.number.lookup.home.screen.reveal

import android.content.Context
import com.callerid.admesh.engine.PromoVault
import org.json.JSONObject

enum class RevealCadence {
    ALWAYS, ONCE, EVERY_DAYS, APP_LAUNCHES, NEVER;

    companion object {
        fun from(raw: String?): RevealCadence = when (raw?.trim()?.lowercase()) {
            "always" -> ALWAYS
            "once" -> ONCE
            "every_days" -> EVERY_DAYS
            "app_launches" -> APP_LAUNCHES
            "never" -> NEVER
            else -> ONCE
        }
    }
}

data class RevealSpec(
    val enabled: Boolean,
    val frequency: RevealCadence,
    val interval: Int
)

object RevealConfig {

    private const val RC_KEY = "intro_display"

    const val LANGUAGE = "language"
    const val TERMS = "terms"
    const val ONBOARDING = "onboarding"
    const val PERMISSION_SHEET = "permission_sheet"

    fun language(context: Context): RevealSpec =
        load(context, LANGUAGE, defaultEnabled = true, defaultFrequency = RevealCadence.ONCE)

    fun terms(context: Context): RevealSpec =
        load(context, TERMS, defaultEnabled = false, defaultFrequency = RevealCadence.ONCE)

    fun onboarding(context: Context): RevealSpec =
        load(context, ONBOARDING, defaultEnabled = false, defaultFrequency = RevealCadence.ONCE)

    fun permissionSheet(context: Context): RevealSpec =
        load(context, PERMISSION_SHEET, defaultEnabled = true, defaultFrequency = RevealCadence.ALWAYS)

    private fun load(
        context: Context,
        key: String,
        defaultEnabled: Boolean,
        defaultFrequency: RevealCadence
    ): RevealSpec {
        val ads = PromoVault.getInstance(context)
        val raw = ads.getString(RC_KEY)
        val obj = if (raw.isNullOrBlank()) null else runCatching {
            val root = JSONObject(raw)
            audienceContainer(root, isMarketing = ads.getBoolean("OnMaketing")).optJSONObject(key)
        }.getOrNull()

        if (obj == null) return RevealSpec(defaultEnabled, defaultFrequency, 0)

        return RevealSpec(
            enabled = obj.optBoolean("enabled", defaultEnabled),
            frequency = RevealCadence.from(
                obj.optString("prompt_frequency", defaultFrequency.name.lowercase())
            ),
            interval = obj.optInt("prompt_interval", 0)
        )
    }

    private fun audienceContainer(root: JSONObject, isMarketing: Boolean): JSONObject {
        val preferred = if (isMarketing) "marketing" else "organic"
        val fallback = if (isMarketing) "organic" else "marketing"
        root.optJSONObject(preferred)?.let { return it }
        root.optJSONObject(fallback)?.let { return it }
        return root
    }
}
