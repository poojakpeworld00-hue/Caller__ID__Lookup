package com.callerid.number.lookup.home.screen.history

import androidx.annotation.StringRes
import com.callerid.number.lookup.home.store.CallEntry

enum class LogScope { ALL, INCOMING, OUTGOING, MISSED }

enum class LogOrder { NEWEST, OLDEST, NAME_ASC, NAME_DESC }

sealed interface LogRow {
    data class Header(@param:StringRes val titleRes: Int) : LogRow
    data class Call(val entry: CallEntry) : LogRow
}
