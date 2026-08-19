package com.callerid.number.lookup.home.ui.lookup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.callerid.number.lookup.home.data.lookup.TraceArchive

/** Backs the standalone search-history screen; reads/clears the shared history store. */
class TraceViewModel(app: Application) : AndroidViewModel(app) {

    private val historyStore = TraceArchive(app)

    private val _history = MutableLiveData<List<TraceRow>>(emptyList())
    val history: LiveData<List<TraceRow>> = _history

    fun load() {
        _history.value = historyStore.all()
    }

    fun clear() {
        historyStore.clear()
        _history.value = emptyList()
    }
}
