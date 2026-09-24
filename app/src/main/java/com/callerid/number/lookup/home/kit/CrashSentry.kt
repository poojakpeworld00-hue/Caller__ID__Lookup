package com.callerid.number.lookup.home.kit

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import io.launcher.home.extensions.isDefaultLauncher
import com.callerid.number.lookup.home.screen.boot.LaunchGateActivity

object CrashSentry {

    private const val TAG = "CrashSentry"

    private const val RESTART_AFTER_CRASH = true

    private const val LOOP_WINDOW_MS = 10_000L

    private const val PREFS_NAME = "crash_guard"
    private const val KEY_LAST_CRASH_AT = "last_crash_at"

    fun install(app: Application, isForeground: () -> Boolean) {
        val chained = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->

            runCatching {
                LogRail.error(TAG, "Uncaught exception on thread '${thread.name}'", error)
            }

            val restarting = runCatching {
                RESTART_AFTER_CRASH && isForeground() && !isCrashLoop(app)
            }.getOrDefault(false)

            if (restarting) {

                runCatching { relaunch(app) }
            }
            runCatching { Log.w(TAG, "crash handled on '${thread.name}', restarting=$restarting") }

            if (chained != null) {
                chained.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
            }
        }

        LogRail.log(TAG, "installed (chained=${chained != null})")
    }

    private fun isCrashLoop(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val previous = prefs.getLong(KEY_LAST_CRASH_AT, 0L)

        prefs.edit().putLong(KEY_LAST_CRASH_AT, now).commit()

        return previous != 0L && now - previous in 0..LOOP_WINDOW_MS
    }

    private fun relaunch(app: Application) {
        if (runCatching { app.isDefaultLauncher() }.getOrDefault(false)) {
            app.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        app.startActivity(
            Intent(app, LaunchGateActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
    }
}
