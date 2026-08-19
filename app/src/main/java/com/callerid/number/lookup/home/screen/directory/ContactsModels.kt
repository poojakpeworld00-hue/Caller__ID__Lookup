package com.callerid.number.lookup.home.screen.directory

import com.callerid.number.lookup.home.store.ContactItem

/** Top filter tabs for the contacts list. */
enum class ContactFilter { ALL, FAVORITES, RECENTS, GROUPS }

/** A row in the contacts list: an alphabetical section letter or a contact. */
sealed interface ContactRow {
    data class Header(val letter: String) : ContactRow
    data class Item(val contact: ContactItem) : ContactRow
}
