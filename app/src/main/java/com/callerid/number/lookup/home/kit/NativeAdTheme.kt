package com.callerid.number.lookup.home.kit

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_DARK
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_LIGHT
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_SYSTEM
import org.json.JSONObject

private const val TAG = "NativeTheme"

fun Context.applyNativeAdTheme(
    theme: String = AppPrefs.selectedTheme(this).ifEmpty { THEME_SYSTEM }
) {
    val adsPref = PromoVault.getInstance(this)
    val modeKey = when (theme) {
        THEME_LIGHT -> "NativeLight"
        THEME_DARK -> "NativeDark"
        THEME_SYSTEM -> {
            val isSystemDark =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            if (isSystemDark) "NativeDark" else "NativeLight"
        }

        else -> "NativeLight"
    }

    try {

        val marketingJson = JSONObject(adsPref.getString("NativeTheme_marketing", "{}"))
        val defaultJson = JSONObject(adsPref.getString("NativeTheme_default", "{}"))

        val themeJson = if (adsPref.getBoolean("OnMaketing") && marketingJson.has(modeKey))
            marketingJson.optJSONObject(modeKey)
        else
            defaultJson.optJSONObject(modeKey)

        if (themeJson == null) {

            Log.d(TAG, "No $modeKey palette in NativeTheme_* — native colors left unchanged")
            return
        }

        adsPref.putString("NativebtnColor", themeJson.optString("btnColor"))
        adsPref.putString("NativebtntxtColor", themeJson.optString("btnText"))
        adsPref.putString("NativeBgColor", themeJson.optString("bgColor"))
        adsPref.putString("NativetxtColor", themeJson.optString("textColor"))

        Log.d(TAG, "Applied $modeKey theme to ads dynamically")
    } catch (e: Exception) {
        Log.e(TAG, "Error applying native theme dynamically", e)
    }
}
