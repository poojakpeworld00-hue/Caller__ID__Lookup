package com.callerid.number.lookup.home.screen.blocking

import com.callerid.number.lookup.home.store.BlockedEntry

data class BlockedRowUi(
    val entry: BlockedEntry,
    val label: String,
    val isSpam: Boolean,
)
