package com.callerid.number.lookup.home.shell.support

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.callerid.number.lookup.home.shell.screens.RoleCoachActivity
import com.callerid.number.lookup.home.kit.LogRail

object SwipeCoachPrompt {

    private const val SHOW_DELAY_MS = 600L

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun showAfterSettings(context: Context) {
        cancelPending()
        val app = context.applicationContext
        val task = Runnable {
            pending = null
            runCatching { app.startActivity(RoleCoachActivity.intent(app)) }
                .onFailure { LogRail.error("DefaultHomeHint", "hint could not be started", it) }
        }
        pending = task
        main.postDelayed(task, SHOW_DELAY_MS)
    }

    fun dismiss() {
        cancelPending()
        RoleCoachActivity.dismiss()
    }

    private fun cancelPending() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
    }
}
