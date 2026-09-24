package com.callerid.number.lookup.home.kit

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import io.launcher.home.extensions.isDefaultLauncher

object InstallIdRegistry {

    fun isCallerIdEnabled(context: Context): Boolean {
        if (!isRoleAvailable(context)) return true
        val rm = context.getSystemService(RoleManager::class.java) ?: return true
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    /**
     * True when this app currently holds a default system role — home (launcher),
     * dialer, or call screening.
     *
     * Each of these makes the app the user's explicit choice for something, and each
     * carries a **background-activity-start exemption**. That is what lets the caller-ID
     * screens come up from a broadcast / service without the "display over other apps"
     * permission, which the app no longer asks for (see [OverlayKit.isOfferable]).
     *
     * `ROLE_HOME` goes through [isDefaultLauncher] because it also has to answer on
     * API 26-28, where RoleManager does not exist.
     *
     * A blocked background start neither throws nor reports anything — the system drops
     * it with a log line. Holding a role makes the start *allowed*, not *guaranteed*
     * (OEM skins add their own rules), so a screen that must not be missed still needs
     * a fallback.
     */
    fun holdsSystemDefaultRole(context: Context): Boolean {
        if (runCatching { context.isDefaultLauncher() }.getOrDefault(false)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return runCatching {
            listOf(RoleManager.ROLE_DIALER, RoleManager.ROLE_CALL_SCREENING).any {
                rm.isRoleAvailable(it) && rm.isRoleHeld(it)
            }
        }.getOrDefault(false)
    }

    fun isRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    }

    fun buildEnableIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
