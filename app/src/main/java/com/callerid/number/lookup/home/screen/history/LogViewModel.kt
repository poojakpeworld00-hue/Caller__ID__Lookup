package com.callerid.number.lookup.home.screen.history

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.CallEntry
import com.callerid.number.lookup.home.store.CallFlavor
import com.callerid.number.lookup.home.store.CallHistorySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val history = CallHistorySource(app)
    private var source: List<CallEntry> = emptyList()
    private var needle: String = ""

    private val _rows = MutableLiveData<List<LogRow>>(emptyList())
    val rows: LiveData<List<LogRow>> = _rows

    private val _filter = MutableLiveData(LogScope.ALL)
    val filter: LiveData<LogScope> = _filter

    private val _sort = MutableLiveData(LogOrder.NEWEST)
    val sort: LiveData<LogOrder> = _sort

    fun load() {
        viewModelScope.launch {
            source = withContext(Dispatchers.IO) { history.getCalls() }
            rebuild()
        }
    }

    fun setFilter(filter: LogScope) {
        if (_filter.value == filter) return
        _filter.value = filter
        rebuild()
    }

    fun setSort(sort: LogOrder) {
        if (_sort.value == sort) return
        _sort.value = sort
        rebuild()
    }

    fun setQuery(text: String) {
        val trimmed = text.trim()
        if (trimmed == needle) return
        needle = trimmed
        rebuild()
    }

    /**
     * The call flavour a scope admits, or null when it admits everything. Expressing
     * the scope as data removes the per-call branch that used to sit in `matches`.
     */
    private val LogScope.admits: CallFlavor?
        get() = when (this) {
            LogScope.ALL -> null
            LogScope.INCOMING -> CallFlavor.INCOMING
            LogScope.OUTGOING -> CallFlavor.OUTGOING
            LogScope.MISSED -> CallFlavor.MISSED
        }

    /** Day buckets carry their own heading, so no int sentinel has to be mapped back. */
    private enum class DayBucket(@StringRes val title: Int) {
        TODAY(R.string.recents_today),
        YESTERDAY(R.string.recents_yesterday),
        THIS_WEEK(R.string.recents_this_week),
        EARLIER(R.string.recents_earlier),
    }

    private fun rebuild() {
        val scope = _filter.value ?: LogScope.ALL
        val order = _sort.value ?: LogOrder.NEWEST
        val wanted = scope.admits
        val q = needle.lowercase(Locale.getDefault())

        val visible = source.filter { call ->
            (wanted == null || call.type == wanted) && (q.isEmpty() || call.mentions(q))
        }

        // Date orders keep the Today/Yesterday/… headings; name orders flatten,
        // because a heading about recency means nothing in an alphabetical list.
        _rows.value = when (order) {
            LogOrder.NEWEST -> withHeadings(visible.sortedByDescending { it.date })
            LogOrder.OLDEST -> withHeadings(visible.sortedBy { it.date })
            LogOrder.NAME_ASC -> visible.sortedBy { it.sortKey }.map(LogRow::Call)
            LogOrder.NAME_DESC -> visible.sortedByDescending { it.sortKey }.map(LogRow::Call)
        }
    }

    /** Matches the query against the caller's name, falling back to the number. */
    private fun CallEntry.mentions(lowercaseQuery: String): Boolean =
        name?.lowercase(Locale.getDefault())?.contains(lowercaseQuery) == true ||
            number.lowercase(Locale.getDefault()).contains(lowercaseQuery)

    /** Name sorts on the caller's name when there is one, otherwise on the number. */
    private val CallEntry.sortKey: String
        get() = (name?.takeIf { it.isNotBlank() } ?: number).lowercase(Locale.getDefault())

    private fun CallEntry.bucket(today: Long): DayBucket = when {
        date >= today -> DayBucket.TODAY
        date >= today - DAY_MS -> DayBucket.YESTERDAY
        date >= today - 6 * DAY_MS -> DayBucket.THIS_WEEK
        else -> DayBucket.EARLIER
    }

    /**
     * Emits a heading each time the day bucket changes. The list is already ordered,
     * so a change of bucket is a boundary in either direction — which is what lets
     * OLDEST walk Earlier → Today and still read correctly.
     */
    private fun withHeadings(calls: List<CallEntry>): List<LogRow> {
        if (calls.isEmpty()) return emptyList()
        val today = startOfToday()
        var previous: DayBucket? = null
        return buildList(calls.size + DayBucket.entries.size) {
            for (call in calls) {
                val bucket = call.bucket(today)
                if (bucket != previous) {
                    add(LogRow.Header(bucket.title))
                    previous = bucket
                }
                add(LogRow.Call(call))
            }
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
