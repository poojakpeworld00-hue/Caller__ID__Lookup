package com.callerid.number.lookup.home.shell.contracts

import com.callerid.number.lookup.home.shell.entities.GadgetInfo

interface WidgetPanelListener {
    fun onWidgetLongPressed(appWidget: GadgetInfo)
}
