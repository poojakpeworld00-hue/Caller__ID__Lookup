package com.callerid.number.lookup.home.permit

import android.os.Handler
import android.os.Looper

class PermitScheduler {

    private val handler = Handler(Looper.getMainLooper())

    fun schedule(delayMs: Long, action: () -> Unit) {
        if (delayMs <= 0L) handler.post(action) else handler.postDelayed(action, delayMs)
    }

    fun clear() {
        handler.removeCallbacksAndMessages(null)
    }
}
