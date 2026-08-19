package com.callerid.number.lookup.home.launcher.interfaces

import com.callerid.number.lookup.home.launcher.models.AppLauncher

interface AllAppsListener {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher)
}
