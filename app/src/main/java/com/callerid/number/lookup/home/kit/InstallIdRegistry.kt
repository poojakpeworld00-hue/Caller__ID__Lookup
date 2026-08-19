package com.callerid.number.lookup.home.kit

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

object InstallIdRegistry {

    fun isCallerIdEnabled(context: Context): Boolean {
        if (!isRoleAvailable(context)) return true
        val rm = context.getSystemService(RoleManager::class.java) ?: return true
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
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
