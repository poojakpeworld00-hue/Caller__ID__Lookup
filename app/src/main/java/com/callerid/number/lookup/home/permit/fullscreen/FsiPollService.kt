package com.callerid.number.lookup.home.permit.fullscreen

import com.callerid.admesh.engine.logGateResult
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.callerid.number.lookup.home.kit.LogRail

class FsiPollService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            val granted = FsiPermit.isGranted(this@FsiPollService)
            LogRail.log("FSI", "watch poll: granted=$granted")
            if (granted) {
                runCatching {
                    logGateResult("full_screen_intent", true)
                    sendBroadcast(Intent(FsiPermit.ACTION_FSI_GRANTED).setPackage(packageName))
                }
                stopSelf()
            } else handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(poll); handler.post(poll)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll); super.onDestroy()
    }

    companion object {
        private const val POLL_MS = 500L

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, FsiPollService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FsiPollService::class.java)) }
        }
    }
}
