package com.callerid.number.lookup.home.launcher.helpers

import com.callerid.number.lookup.home.launcher.models.AppTile

object IconStore {
    @Volatile
    private var cachedLaunchers = emptyList<AppTile>()

    var launchers: List<AppTile>
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