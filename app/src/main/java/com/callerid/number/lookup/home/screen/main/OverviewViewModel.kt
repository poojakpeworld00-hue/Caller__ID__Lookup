package com.callerid.number.lookup.home.screen.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.callerid.number.lookup.home.store.CallEntry
import com.callerid.number.lookup.home.store.CallCardModel
import com.callerid.number.lookup.home.store.CallHistorySource
import com.callerid.number.lookup.home.screen.shared.CallFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel(app: Application) : AndroidViewModel(app) {

    private val callLogRepository = CallHistorySource(app)

    private val _blockedCount = MutableLiveData(0)
    val blockedCount: LiveData<Int> = _blockedCount

    private val _spamCount = MutableLiveData(0)
    val spamCount: LiveData<Int> = _spamCount

    private val _recent = MutableLiveData<List<CallCardModel>>(emptyList())
    val recent: LiveData<List<CallCardModel>> = _recent

    init {

        _blockedCount.value = 128
        _spamCount.value = 37
    }

    fun loadRecent() {
        viewModelScope.launch {
            val calls = withContext(Dispatchers.IO) { callLogRepository.getCalls(limit = 2) }
            _recent.value = calls.map { toItem(it) }
        }
    }

    private fun toItem(entry: CallEntry): CallCardModel {
        val ctx = getApplication<Application>()
        val typeLabel = ctx.getString(CallFormatter.typeLabelRes(entry.type))
        val time = CallFormatter.timeLabel(entry.date)
        val duration = CallFormatter.durationLabel(entry.durationSec)
        val info = buildString {
            append(typeLabel).append(" · ").append(time)
            if (duration.isNotEmpty()) append(" · ").append(duration)
        }
        return CallCardModel(
            name = CallFormatter.displayName(entry.name, entry.number),
            time = time,
            info = info,
            initials = CallFormatter.initials(entry.name, entry.number),
            type = entry.type,
            number = entry.number,
            identified = !entry.name.isNullOrBlank()
        )
    }
}
