package com.callerid.phonelookup.home.launcher.interfaces

import com.callerid.phonelookup.home.launcher.models.AppLauncher

interface AllAppsListener {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher)
}
