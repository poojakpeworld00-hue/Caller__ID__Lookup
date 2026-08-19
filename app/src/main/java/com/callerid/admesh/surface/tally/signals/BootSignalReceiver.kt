package com.callerid.admesh.surface.tally.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.surface.tally.ShellSurfaceScreen

class BootSignalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d("BootSignalReceiver", "Boot completed detected. Starting service...")
            context.trackEvent("boot_completed")

            val serviceIntent = Intent(context, ShellSurfaceScreen::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

}
