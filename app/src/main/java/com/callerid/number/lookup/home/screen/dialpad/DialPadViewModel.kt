package com.callerid.number.lookup.home.screen.dialpad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.callerid.number.lookup.home.store.CallHistorySource
import com.callerid.number.lookup.home.store.FrequentDigit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DialPadViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CallHistorySource(app)

    private val all = mutableListOf<FrequentDigit>()
    private var query = ""

    private val _frequent = MutableLiveData<List<FrequentDigit>>(emptyList())
    val frequent: LiveData<List<FrequentDigit>> = _frequent

    fun load() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.getMostUsed(limit = 200) }
            all.clear()
            all.addAll(list)
            applyFilter()
        }
    }

    fun filter(text: String) {
        query = text.trim()
        applyFilter()
    }

    private fun applyFilter() {

        if (query.isEmpty()) {
            _frequent.value = all.take(TOP_LIMIT)
            return
        }
        val digits = query.filter { it.isDigit() }
        _frequent.value = all.filter { item ->
            (digits.isNotEmpty() && item.number.filter { it.isDigit() }.contains(digits)) ||
                item.number.contains(query) ||
                item.name?.contains(query, ignoreCase = true) == true
        }
    }

    private companion object {
        const val TOP_LIMIT = 20
    }
}
