package com.callerid.number.lookup.home.launcher.helpers

import com.callerid.number.lookup.home.launcher.models.AppLauncher

object IconCache {
    @Volatile
    private var cachedLaunchers = emptyList<AppLauncher>()

    var launchers: List<AppLauncher>
        get() = cachedLaunchers
        set(value) {
            synchronized(this) {
                cachedLaunchers = value
            }
        }

    fun clear() {
        launchers = emptyList()
    }
}