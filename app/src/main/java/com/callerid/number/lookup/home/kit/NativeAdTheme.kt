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

const val NATIVE_THEME_KEY = "NativeTheme"

fun Context.nativeThemeMode(
    theme: String = AppPrefs.selectedTheme(this).ifEmpty { THEME_SYSTEM }
): String = when (theme) {
    THEME_DARK -> "NativeDark"
    THEME_LIGHT -> "NativeLight"
    THEME_SYSTEM -> {
        val isSystemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        if (isSystemDark) "NativeDark" else "NativeLight"
    }

    else -> "NativeLight"
}

fun Context.applyNativeAdTheme(theme: String = AppPrefs.selectedTheme(this).ifEmpty { THEME_SYSTEM }) {
    val adsPref = PromoVault.getInstance(this)
    val modeKey = nativeThemeMode(theme)

    try {
        val palette = JSONObject(adsPref.getString(NATIVE_THEME_KEY, "{}").orEmpty().ifBlank { "{}" })
        val themeJson = palette.optJSONObject(modeKey)
        if (themeJson == null) {
            Log.d(TAG, "No $modeKey palette in $NATIVE_THEME_KEY — native colors left unchanged")
            return
        }

        adsPref.update {
            putString("NativebtnColor", themeJson.optString("btnColor"))
            putString("NativebtntxtColor", themeJson.optString("btnText"))
            putString("NativeBgColor", themeJson.optString("bgColor"))
            putString("NativetxtColor", themeJson.optString("textColor"))
        }

        Log.d(TAG, "Applied $modeKey theme to ads dynamically")
    } catch (e: Exception) {
        Log.e(TAG, "Error applying native theme dynamically", e)
    }
}
