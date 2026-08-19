package com.callerid.number.lookup.home.shell.entities

import android.graphics.drawable.Drawable
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.SORT_BY_TITLE
import org.fossify.commons.helpers.SORT_DESCENDING

val appLauncherComparator = compareBy<AppTile>(
    { it.title.normalizeString().lowercase() },
    { it.packageName }
)

data class AppTile(
    var id: Long?,
    var title: String,
    var packageName: String,
    var activityName: String,
    var order: Int,
    var thumbnailColor: Int,

    var drawable: Drawable?
) : Comparable<AppTile> {

    constructor() : this(null, "", "", "", 0, 0, null)

    companion object {
        var sorting = 0
    }

    override fun equals(other: Any?) = packageName.equals((other as AppTile).packageName, true)

    override fun hashCode() = super.hashCode()

    fun getBubbleText() = title

    fun getLauncherIdentifier() = "$packageName/$activityName"

    override fun compareTo(other: AppTile): Int {
        var result = when {
            sorting and SORT_BY_TITLE != 0 -> title.normalizeString().lowercase().compareTo(other.title.normalizeString().lowercase())
            else -> {
                if (order > 0 && other.order == 0) {
                    -1
                } else if (order == 0 && other.order > 0) {
                    1
                } else if (order > 0 && other.order > 0) {
                    order.compareTo(other.order)
                } else {
                    title.lowercase().compareTo(other.title.lowercase())
                }
            }
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }
}
