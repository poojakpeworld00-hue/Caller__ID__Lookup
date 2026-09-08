package com.callerid.number.lookup.home.shell.support

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.callerid.admesh.surface.TipSheetActivity
import com.callerid.admesh.surface.TipSheetWindow
import com.callerid.number.lookup.home.shell.screens.RoleCoachActivity
import com.callerid.number.lookup.home.kit.LogRail

object SwipeCoachPrompt {

    private const val SHOW_DELAY_MS = 600L

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /**
     * Raises the "pick this app" hint over the system *Default home app* list.
     *
     * Two routes, and which one is available decides everything:
     *
     *  - **An overlay window** ([TipSheetWindow]) when SYSTEM_ALERT_WINDOW is granted. It is
     *    not in any task, so it sits over the Settings page whatever the system does with
     *    ours, and it can go up immediately.
     *  - **A translucent activity in its own task** otherwise, posted after
     *    [SHOW_DELAY_MS] so the Settings page is already on screen. Starting it in the same
     *    instant as the Settings intent — which is what the Home banner used to do — puts it
     *    in *our* task behind Settings, where the user meets it on the way back to the
     *    launcher instead of over the list it is describing.
     */
    fun showAfterSettings(context: Context) {
        cancelPending()
        val app = context.applicationContext

        
        if (TipSheetWindow.show(app, TipSheetActivity.MODE_HOME, delayMs = SHOW_DELAY_MS)) return

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
        
        TipSheetWindow.dismiss()
        RoleCoachActivity.dismiss()
    }

    private fun cancelPending() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
    }
}
