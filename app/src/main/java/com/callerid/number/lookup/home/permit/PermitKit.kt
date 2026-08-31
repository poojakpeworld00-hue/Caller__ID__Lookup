package com.callerid.number.lookup.home.permit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.callerid.admesh.engine.PromoVault

object PermitKit {

    val CATALOG: Map<String, PermitSpec> = listOf(
        PermitSpec(
            key = "notification",
            androidPermission = Manifest.permission.POST_NOTIFICATIONS,
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
        PermitSpec(
            key = "phone_state",
            androidPermission = Manifest.permission.READ_PHONE_STATE,
            minSdk = Build.VERSION_CODES.M,

            enabledPrefGate = "HD_VBC_Show",
        ),
        PermitSpec(
            key = "call_log",
            androidPermission = Manifest.permission.READ_CALL_LOG,
            minSdk = Build.VERSION_CODES.M,
        ),
        PermitSpec(
            key = "contacts",
            androidPermission = Manifest.permission.READ_CONTACTS,
            minSdk = Build.VERSION_CODES.M,
        ),
    ).associateBy { it.key }

    fun spec(key: String): PermitSpec? = CATALOG[key]

    fun isApplicableOnThisSdk(spec: PermitSpec): Boolean =
        Build.VERSION.SDK_INT >= spec.minSdk

    fun isPrefGateOpen(context: Context, spec: PermitSpec): Boolean {
        val gate = spec.enabledPrefGate ?: return true
        return PromoVault.getInstance(context).getBoolean(gate)
    }

    /**
     * True when [key] can still be *offered* to the user right now — i.e. a request for it
     * would actually reach the OS dialog.
     *
     * This mirrors, in one place, every gate [PermitEngine.request] applies before firing a
     * request:
     *  - the key is a known [CATALOG] entry,
     *  - it is a runtime permission on this SDK ([isApplicableOnThisSdk]),
     *  - its business gate is open ([isPrefGateOpen], e.g. `HD_VBC_Show`),
     *  - Remote Config does not disable it (`enabled: false` in `permission_engine`; a
     *    *missing* rule means "no config" and stays offerable, exactly like
     *    [PermitEngine.request]),
     *  - a `show_once` rule has not already been shown this install.
     *
     * UI that lists engine-managed permissions (the Home permission sheet, the Quick Action
     * nudges) must use this so it never renders a row whose Allow button would be a no-op.
     * It says nothing about whether the permission is already granted — combine with
     * [isGranted] for that.
     */
    fun isOfferable(context: Context, key: String): Boolean {
        val spec = spec(key) ?: return false
        if (!isApplicableOnThisSdk(spec)) return false
        if (!isPrefGateOpen(context, spec)) return false
        val rule = PermitSource.rules().firstOrNull { it.key == key } ?: return true
        if (!rule.enabled) return false
        if (rule.showOnce && PermitVault(context).wasShown(key)) return false
        return true
    }

    fun isGranted(context: Context, spec: PermitSpec): Boolean {
        if (Build.VERSION.SDK_INT < spec.minSdk) return true
        return ContextCompat.checkSelfPermission(context, spec.androidPermission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
