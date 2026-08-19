package com.callerid.number.lookup.home.screen.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.CallEntry
import com.callerid.number.lookup.home.store.CallHistorySource
import com.callerid.number.lookup.home.store.CallFlavor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CallDetailUi(
    val name: String,
    val number: String,
    val verified: Boolean,
    val totalDuration: String,
    val totalCalls: String,
    val callsSubtitle: String,
    val history: List<CallEntry>
)

class CallDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CallHistorySource(app)

    private val _ui = MutableLiveData<CallDetailUi>()
    val ui: LiveData<CallDetailUi> = _ui

    fun load(number: String, fallbackName: String?) {
        viewModelScope.launch {
            val calls = withContext(Dispatchers.IO) { repository.getCalls(limit = 1000) }
            val target = normalize(number)
            val mine = calls
                .filter { normalize(it.number) == target }
                .sortedByDescending { it.date }

            val name = mine.firstOrNull { !it.name.isNullOrBlank() }?.name
                ?: fallbackName?.takeIf { it.isNotBlank() }
                ?: number
            val verified = mine.any { !it.name.isNullOrBlank() } || !fallbackName.isNullOrBlank()

            val recent = lastThirtyDays(mine)

            _ui.value = CallDetailUi(
                name = name,
                number = number,
                verified = verified,
                totalDuration = formatDuration(recent.sumOf { it.durationSec }),
                totalCalls = recent.size.toString(),
                callsSubtitle = callsSubtitle(recent),
                history = mine
            )
        }
    }

    private fun lastThirtyDays(calls: List<CallEntry>): List<CallEntry> {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        return calls.filter { it.date >= cutoff }
    }

    private fun callsSubtitle(calls: List<CallEntry>): String {
        val app = getApplication<Application>()
        if (calls.isEmpty()) return app.getString(R.string.detail_calls_none)

        val types = calls.mapTo(HashSet()) { it.type }
        val onlyMissed = types.all { it == CallFlavor.MISSED || it == CallFlavor.SPAM }
        return when {
            types == setOf(CallFlavor.OUTGOING) -> app.getString(R.string.detail_calls_outgoing)
            types == setOf(CallFlavor.INCOMING) -> app.getString(R.string.detail_calls_incoming)
            onlyMissed -> app.getString(R.string.detail_calls_missed)
            else -> app.getString(R.string.detail_calls_mixed)
        }
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> getApplication<Application>().getString(R.string.detail_no_talk_time)
        }
    }

    private fun normalize(number: String): String =
        number.filter { it.isDigit() }.ifEmpty { number.trim() }

    private companion object {
        const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
