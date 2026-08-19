package com.callerid.number.lookup.home.screen.history

import androidx.annotation.StringRes
import com.callerid.number.lookup.home.store.CallEntry

/** Top filter tabs. */
enum class LogScope { ALL, INCOMING, OUTGOING, MISSED }

/**
 * Sort order applied by the toolbar sort button. Date sorts keep the
 * Today/Yesterday/… grouping; name sorts flatten the list (no date headers).
 */
enum class LogOrder { NEWEST, OLDEST, NAME_ASC, NAME_DESC }

/** A row in the recents list: either a date section header or a call. */
sealed interface LogRow {
    data class Header(@param:StringRes val titleRes: Int) : LogRow
    data class Call(val entry: CallEntry) : LogRow
}
