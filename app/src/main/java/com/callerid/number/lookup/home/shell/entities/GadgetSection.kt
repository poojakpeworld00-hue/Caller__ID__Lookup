package com.callerid.number.lookup.home.shell.entities

import android.graphics.drawable.Drawable

data class GadgetSection(var appTitle: String, var appIcon: Drawable?) : GadgetRow() {
    override fun getHashToCompare() = getStringToCompare().hashCode()

    private fun getStringToCompare(): String {
        return copy(appIcon = null).toString()
    }
}
