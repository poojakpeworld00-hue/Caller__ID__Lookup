package com.callerid.number.lookup.home.permit.fullscreen

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import androidx.activity.result.ActivityResultLauncher
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.kit.LogRail
import java.util.Locale

object FsiPermit {

    private const val TAG = "FSI"
    private const val ACTION_MANAGE = "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"

    const val ACTION_FSI_GRANTED =
        "com.callerid.number.lookup.home.action.FSI_GRANTED"

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        }.getOrDefault(false)
    }

    fun isCountryAllowed(context: Context, config: FsiSettings): Boolean {
        if (!config.countryFilterEnabled || config.excludedCountries.isEmpty()) {
            LogRail.log(TAG, "country: ALLOWED (filter off or empty list)")
            return true
        }
        val excluded = config.excludedCountries
        val signals = deviceCountrySignals(context)
        if (signals.isEmpty()) {
            LogRail.log(TAG, "country: ALLOWED (no country signal → fail-open)")
            return true
        }
        val blocked = signals.any { sig -> excluded.any { it.equals(sig, ignoreCase = true) } }
        LogRail.log(TAG, "country: ${if (blocked) "BLOCKED" else "ALLOWED"} [signals=$signals, excluded=$excluded]")
        return !blocked
    }

    private fun deviceCountrySignals(context: Context): List<String> {
        val out = mutableListOf<String>()
        PromoVault.getInstance(context).userCountry.takeIf { it.isNotBlank() }?.let { out += it.trim() }
        runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.simCountryIso?.takeIf { it.isNotBlank() }?.let { out += it.trim() }
            tm?.networkCountryIso?.takeIf { it.isNotBlank() }?.let { out += it.trim() }
        }
        Locale.getDefault().country.takeIf { it.isNotBlank() }?.let { out += it.trim() }
        return out.distinct()
    }

    fun canRun(context: Context, config: FsiSettings): Boolean {
        val sdkOk = Build.VERSION.SDK_INT >= config.minSdk
        val granted = isGranted(context)
        val countryOk = isCountryAllowed(context, config)
        val result = config.enabled && sdkOk && !granted && countryOk
        LogRail.log(
            TAG,
            "canRun=$result [enabled=${config.enabled}, sdk=${Build.VERSION.SDK_INT}>=${config.minSdk}?$sdkOk, granted=$granted, countryAllowed=$countryOk]"
        )
        return result
    }

    fun shouldShowScreen(context: Context, config: FsiSettings = FsiSettings.load(context)): Boolean {
        if (!canRun(context, config) || !config.screen.enabled) {
            LogRail.log(TAG, "SCREEN → NO (canRun failed or screen.enabled=${config.screen.enabled})")
            return false
        }
        val shown = StorageRegistry(context).fsiScreenShown
        val result = if (config.screen.showOnce) !shown else true
        LogRail.log(TAG, "SCREEN → ${if (result) "YES" else "NO"} [showOnce=${config.screen.showOnce}, alreadyShown=$shown]")
        return result
    }

    fun shouldShowDialog(context: Context, config: FsiSettings = FsiSettings.load(context)): Boolean {
        if (!canRun(context, config) || !config.dialog.enabled) {
            LogRail.log(TAG, "DIALOG → NO (canRun failed or dialog.enabled=${config.dialog.enabled})")
            return false
        }
        val prefs = StorageRegistry(context)
        val count = prefs.fsiDialogShowCount
        if (count >= config.dialog.maxShowCount) {
            LogRail.log(TAG, "DIALOG → NO (count=$count >= max_show_count=${config.dialog.maxShowCount})")
            return false
        }
        val last = prefs.fsiDialogLastShownMs
        if (last == 0L) {
            LogRail.log(TAG, "DIALOG → YES (first show; count=$count/${config.dialog.maxShowCount})")
            return true
        }
        val elapsedDays = (System.currentTimeMillis() - last) / (24L * 60L * 60L * 1000L)
        val result = elapsedDays >= config.dialog.showAfterDays
        LogRail.log(TAG, "DIALOG → ${if (result) "YES" else "NO"} [elapsed=${elapsedDays}d, need=${config.dialog.showAfterDays}d, count=$count/${config.dialog.maxShowCount}]")
        return result
    }

    fun markScreenShown(context: Context) {
        StorageRegistry(context).fsiScreenShown = true
        LogRail.log(TAG, "ledger: screen marked shown")
    }

    fun markDialogShown(context: Context) {
        val prefs = StorageRegistry(context)
        prefs.fsiDialogLastShownMs = System.currentTimeMillis()
        prefs.fsiDialogShowCount = prefs.fsiDialogShowCount + 1
        LogRail.log(TAG, "ledger: dialog shown, count now ${prefs.fsiDialogShowCount}")
    }

    private fun manageIntent(context: Context) =
        Intent(ACTION_MANAGE, Uri.parse("package:${context.packageName}"))
            .addFlags(
               Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )

    fun openSettings(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        OpenPromoRegistry.skipNextAppOpenAd = true
        val launched = runCatching { launcher.launch(manageIntent(activity)) }.isSuccess
        if (launched) {
            LogRail.log(TAG, "openSettings: FSI manage page launched in-task, watcher armed")
            FsiPollService.start(activity)
        } else {
            LogRail.error(TAG, "Failed to open FSI settings", null)
        }
    }

    fun stopWatch(context: Context) = FsiPollService.stop(context)
}
