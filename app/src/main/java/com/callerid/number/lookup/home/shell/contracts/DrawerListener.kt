package com.callerid.number.lookup.home.shell.contracts

import com.callerid.number.lookup.home.shell.entities.AppTile

interface DrawerListener {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppTile)
}
