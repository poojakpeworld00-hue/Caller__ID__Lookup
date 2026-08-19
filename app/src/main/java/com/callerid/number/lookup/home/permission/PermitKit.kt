package com.callerid.number.lookup.home.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.callerid.admesh.domain.PromoVault

/**
 * Central registry + grant helpers for the Permission Engine.
 *
 * [CATALOG] is the single source of truth for which OS permissions the engine
 * can request. Adding a new permission = adding one line here (future-proof).
 */
object PermitKit {

    /**
     * Registry of supported permissions, keyed by the Remote LauncherPrefs key.
     *
     * `minSdk` is the SDK level at/above which the permission is a *runtime*
     * permission. Below that level the OS grants it at install time, so the
     * engine treats it as already-granted and never prompts.
     */
    val CATALOG: Map<String, PermitSpec> = listOf(
        PermitSpec(
            key = "notification",
            androidPermission = Manifest.permission.POST_NOTIFICATIONS,
            minSdk = Build.VERSION_CODES.TIRAMISU, // 33
        ),
        PermitSpec(
            key = "phone_state",
            androidPermission = Manifest.permission.READ_PHONE_STATE,
            minSdk = Build.VERSION_CODES.M, // 23
            // Respect PromoAnchorActivity's geo gate: READ_PHONE_STATE is only asked
            // when HD_VBC_Show is true (it is forced false in allow-listed
            // regions during the splash config flow).
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

    /** Returns the spec for a Remote LauncherPrefs key, or null if the key is unknown. */
    fun spec(key: String): PermitSpec? = CATALOG[key]

    /** True when this permission is even applicable on the current OS version. */
    fun isApplicableOnThisSdk(spec: PermitSpec): Boolean =
        Build.VERSION.SDK_INT >= spec.minSdk

    /**
     * True when the spec's optional business gate allows requesting it. A spec
     * with no [PermitSpec.enabledPrefGate] is always allowed; otherwise the
     * named `PromoVault` boolean must be true (defaults to false when unset).
     */
    fun isPrefGateOpen(context: Context, spec: PermitSpec): Boolean {
        val gate = spec.enabledPrefGate ?: return true
        return PromoVault.getInstance(context).getBoolean(gate)
    }

    /**
     * True when the permission is already granted (or not required on this SDK).
     * Callers should skip requesting when this returns true.
     */
    fun isGranted(context: Context, spec: PermitSpec): Boolean {
        if (Build.VERSION.SDK_INT < spec.minSdk) return true
        return ContextCompat.checkSelfPermission(context, spec.androidPermission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
