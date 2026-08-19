package com.callerid.number.lookup.home.launcher.models

open class GadgetRowHolder(val widgets: ArrayList<GadgetInfo>) : GadgetRow() {
    override fun getHashToCompare() = widgets.sumOf { it.getHashToCompare() }
}
