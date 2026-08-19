package com.callerid.number.lookup.home.shell.contracts

import android.view.Menu
import com.callerid.number.lookup.home.shell.entities.BoardItem

interface TileMenuListener {
    fun onAnyClick()
    fun hide(gridItem: BoardItem)
    fun rename(gridItem: BoardItem)
    fun resize(gridItem: BoardItem)
    fun appInfo(gridItem: BoardItem)
    fun remove(gridItem: BoardItem)
    fun uninstall(gridItem: BoardItem)
    fun onDismiss()
    fun beforeShow(menu: Menu)
}

abstract class TileMenuListenerBase : TileMenuListener {
    override fun onAnyClick() = Unit
    override fun hide(gridItem: BoardItem) = Unit
    override fun rename(gridItem: BoardItem) = Unit
    override fun resize(gridItem: BoardItem) = Unit
    override fun appInfo(gridItem: BoardItem) = Unit
    override fun remove(gridItem: BoardItem) = Unit
    override fun uninstall(gridItem: BoardItem) = Unit
    override fun onDismiss() = Unit
    override fun beforeShow(menu: Menu) = Unit
}
