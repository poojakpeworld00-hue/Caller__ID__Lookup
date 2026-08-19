package com.callerid.number.lookup.home.screen.consent

import com.callerid.admesh.engine.logGateResult
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class OverlayWatchService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            if (OverlayKit.isGranted(this@OverlayWatchService)) {
                logGateResult("overlay", true)
                sendBroadcast(Intent(ACTION_OVERLAY_GRANTED).setPackage(packageName))
                stopSelf()
            } else {
                handler.postDelayed(this, POLL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    companion object {
        const val ACTION_OVERLAY_GRANTED =
            "com.callerid.number.lookup.home.action.OVERLAY_GRANTED"
        private const val POLL_MS = 500L

        fun start(context: Context) {
            context.startService(Intent(context, OverlayWatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayWatchService::class.java))
        }
    }
}
