package com.callerid.number.lookup.home.shell.entities

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable

data class GadgetInfo(
    var appPackageName: String,
    var appTitle: String,
    val appIcon: Drawable?,
    val widgetTitle: String,
    val widgetPreviewImage: Drawable?,
    var widthCells: Int,
    val heightCells: Int,
    val isShortcut: Boolean,
    val className: String,
    val providerInfo: AppWidgetProviderInfo?,
    val activityInfo: ActivityInfo?
) : GadgetRow() {
    override fun getHashToCompare() = getStringToCompare().hashCode()

    private fun getStringToCompare(): String {
        return copy(appIcon = null, widgetPreviewImage = null).toString()
    }
}
