package com.callerid.number.lookup.home.screen.identify

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.callerid.number.lookup.home.store.ContactSource
import com.callerid.number.lookup.home.store.identify.TraceArchive
import com.callerid.number.lookup.home.store.identify.LocalDigitResolver
import com.callerid.number.lookup.home.wire.LookupPayload
import com.callerid.number.lookup.home.runtime.ApiCredentials
import com.callerid.number.lookup.home.runtime.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LookupViewModel(app: Application) : AndroidViewModel(app) {

    private val contactsRepository = ContactSource(app)
    private val historyStore = TraceArchive(app)

    private val _state = MutableLiveData<LookupState>(LookupState.Idle)
    val state: LiveData<LookupState> = _state

    private val _history = MutableLiveData(historyStore.all())
    val history: LiveData<List<TraceRow>> = _history

    private var searchToken = 0

    private var selectedIso: String? = null
    private var selectedDial: String? = null

    fun setRegion(iso: String, dial: String) {
        selectedIso = iso
        selectedDial = dial
    }

    fun search(raw: String) {
        val typed = NumberInfo.normalize(raw)

        val normalized = when {
            typed.startsWith("+") -> typed
            !selectedDial.isNullOrBlank() -> "+" + selectedDial + typed.filter { it.isDigit() }
            else -> typed
        }
        val digitCount = normalized.count { it.isDigit() }
        if (digitCount < MIN_DIGITS) {
            _state.value = LookupState.Idle
            return
        }

        val token = ++searchToken
        _state.value = LookupState.Loading
        viewModelScope.launch {

            val region = (selectedIso ?: Locale.getDefault().country).ifBlank { "US" }

            val data = withContext(Dispatchers.IO) {
                val contactName = contactsRepository.lookupNameByNumber(normalized)
                val offline = LocalDigitResolver.lookup(normalized, region)
                val apiList = fetchFromApi(normalized)
                Triple(contactName, offline, apiList)
            }
            delay(SHIMMER_MIN_MS)
            if (token != searchToken) return@launch

            val (contactName, offline, apiList) = data
            val api = apiList.firstOrNull()

            val apiPrimary = api?.name?.trim()?.takeIf { it.isNotBlank() }
            val displayName = apiPrimary ?: contactName

            val apiCarrier = apiList.firstNotNullOfOrNull { it.carrierOrNull }
            val apiCountry = apiList.firstNotNullOfOrNull { it.country?.takeIf { c -> c.isNotBlank() } }
            val apiLineType = apiList.firstNotNullOfOrNull { it.lineTypeOrNull }
            val apiCity = apiList.firstNotNullOfOrNull { it.city?.takeIf { c -> c.isNotBlank() } }

            val nicknames = apiList
                .mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotBlank() } }
                .distinct()
                .filter {
                    !it.equals(displayName, ignoreCase = true) &&
                        !it.equals(contactName, ignoreCase = true)
                }
                .take(10)

            val result = LookupResult(
                name = displayName,
                number = NumberInfo.format(normalized),
                rawNumber = normalized,
                inContacts = contactName != null,
                regionCode = NumberInfo.regionCode(normalized),
                country = apiCountry ?: offline?.regionName ?: offline?.location,
                carrier = apiCarrier ?: offline?.carrier,
                lineType = apiLineType ?: offline?.lineType,
                valid = offline?.valid,
                city = apiCity ?: offline?.location,
                isSpam = api?.is_spam == true || api?.is_user_spam == true,
                spamType = api?.spamType,
                nicknames = nicknames
            )
            Log.d(TAG, "carrier: api=$apiCarrier offline=${offline?.carrier} → ${result.carrier}")
            Log.d(TAG, "result: carrier=${result.carrier}, lineType=${result.lineType}, country=${result.country}")
            _state.value = LookupState.Result(result)
            saveToHistory(result)
        }
    }

    private suspend fun fetchFromApi(phone: String): List<LookupPayload> = runCatching {
        if (!ApiCredentials.isConfigured) {
            Log.w(TAG, "checkPhoneNumber skipped: API credentials are placeholders")
            return@runCatching emptyList()
        }
        val response = HttpClientFactory.api.checkPhoneNumber(
            id = ApiCredentials.API_ID,
            phone = phone,
            hashKey = ApiCredentials.API_HASH,
            token = ApiCredentials.API_TOKEN
        )
        if (response.isSuccessful) {
            response.body()?.data.orEmpty()
        } else {
            Log.e(TAG, "checkPhoneNumber failed (${response.code()})")
            emptyList()
        }
    }.onFailure { Log.e(TAG, "checkPhoneNumber error: ${it.message}") }.getOrDefault(emptyList())

    private fun saveToHistory(result: LookupResult) {
        val subtitle = result.country
            ?: NumberInfo.regionName(result.rawNumber)
            ?: result.number
        historyStore.add(
            TraceRow(
                rawNumber = result.rawNumber,
                number = result.number,
                name = result.name,
                subtitle = subtitle
            )
        )
        _history.value = historyStore.all()
    }

    fun clearHistory() {
        historyStore.clear()
        _history.value = emptyList()
    }

    fun refreshHistory() {
        _history.value = historyStore.all()
    }

    fun clear() {
        _state.value = LookupState.Idle
    }

    fun regionName(normalized: String): String? = NumberInfo.regionName(normalized)

    companion object {
        private const val TAG = "LookupViewModel"
        private const val MIN_DIGITS = 3
        private const val SHIMMER_MIN_MS = 2_000L
    }
}
