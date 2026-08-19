package com.callerid.number.lookup.home.screen.blocking

import com.callerid.number.lookup.home.store.BlockedEntry

/**
 * A blocklist row ready for display.
 *
 * @param entry  the underlying blocked number + timestamp.
 * @param label  resolved contact name, or a friendly fallback.
 * @param isSpam when true the row uses the red-tinted "spam" treatment.
 */
data class BlockedRowUi(
    val entry: BlockedEntry,
    val label: String,
    val isSpam: Boolean,
)
