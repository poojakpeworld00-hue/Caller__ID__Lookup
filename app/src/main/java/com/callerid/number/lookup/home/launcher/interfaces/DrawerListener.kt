package com.callerid.number.lookup.home.launcher.interfaces

import com.callerid.number.lookup.home.launcher.models.AppTile

interface DrawerListener {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppTile)
}
