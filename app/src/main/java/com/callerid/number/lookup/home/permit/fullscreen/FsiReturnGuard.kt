package com.callerid.number.lookup.home.permit.fullscreen

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.callerid.number.lookup.home.kit.LogRail

class FsiReturnGuard(private val activity: Activity) {

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            LogRail.log("FSI", "return: grant broadcast received → front ${activity::class.java.simpleName}")

            runCatching {
                activity.startActivity(
                    Intent(activity, activity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            }
        }
    }

    fun register() {
        if (registered) return
        ContextCompat.registerReceiver(
            activity, receiver,
            IntentFilter(FsiPermit.ACTION_FSI_GRANTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { activity.unregisterReceiver(receiver) }
        registered = false
    }
}
