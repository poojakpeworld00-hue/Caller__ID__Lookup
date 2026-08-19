package com.callerid.number.lookup.home.kit

import android.util.Log
import com.callerid.number.lookup.home.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

object LogRail {

    private const val TAG = "LogRail"

    fun log(tag: String = TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun error(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, throwable)
            return
        }

        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("$tag: $message")
            if (throwable != null) crashlytics.recordException(throwable)
        }
    }

    inline fun runDebug(block: () -> Unit) {
        if (BuildConfig.DEBUG) block()
    }
}
