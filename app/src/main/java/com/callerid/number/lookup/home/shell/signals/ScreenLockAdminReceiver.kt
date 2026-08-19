package com.callerid.number.lookup.home.shell.signals

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.callerid.number.lookup.home.R

class ScreenLockAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.lock_device_admin_warning)
    }
}
