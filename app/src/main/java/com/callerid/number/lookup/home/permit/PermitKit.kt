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

    fun isGranted(context: Context, spec: PermitSpec): Boolean {
        if (Build.VERSION.SDK_INT < spec.minSdk) return true
        return ContextCompat.checkSelfPermission(context, spec.androidPermission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
