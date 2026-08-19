package com.callerid.number.lookup.home.screen.directory

import com.callerid.number.lookup.home.store.ContactItem

enum class ContactFilter { ALL, FAVORITES, RECENTS, GROUPS }

sealed interface ContactRow {
    data class Header(val letter: String) : ContactRow
    data class Item(val contact: ContactItem) : ContactRow
}
