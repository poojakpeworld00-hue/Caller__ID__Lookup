package com.callerid.number.lookup.home.shell.entities

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.drawable.Drawable
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_ICON

data class BoardItem(
    var id: Long?,
    var left: Int,
    var top: Int,
    var right: Int,
    var bottom: Int,
    var page: Int,
    var packageName: String,
    var activityName: String,
    var title: String,
    var type: Int,
    var className: String,
    var widgetId: Int,
    var shortcutId: String,
    var icon: Bitmap? = null,
    var docked: Boolean = false,
    var parentId: Long? = null,

    var drawable: Drawable? = null,
    var providerInfo: AppWidgetProviderInfo? = null,
    var activityInfo: ActivityInfo? = null,
    var widthCells: Int = 1,
    var heightCells: Int = 1
) {
    companion object {
        const val FOLDER_MAX_CAPACITY = 16
    }

    constructor() : this(null, -1, -1, -1, -1, 0, "", "", "", ITEM_TYPE_ICON, "", -1, "", null, false, null, null, null, null, 1, 1)

    fun getWidthInCells() = if (right == -1 || left == -1) {
        widthCells
    } else {
        right - left + 1
    }

    fun getHeightInCells() = if (bottom == -1 || top == -1) {
        heightCells
    } else {
        bottom - top + 1
    }

    fun getDockAdjustedTop(rowCount: Int): Int {
        return if (!docked) {
            top
        } else {
            rowCount - 1
        }
    }

    fun getDockAdjustedBottom(rowCount: Int): Int {
        return if (!docked) {
            bottom
        } else {
            rowCount - 1
        }
    }

    fun getItemIdentifier() = "$packageName/$activityName"

    fun getTopLeft(rowCount: Int) = Point(left, getDockAdjustedTop(rowCount))
}
