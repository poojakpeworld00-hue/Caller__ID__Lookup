package com.callerid.number.lookup.home.screen.identify

data class LookupResult(
    val name: String?,
    val number: String,
    val rawNumber: String,
    val inContacts: Boolean,
    val regionCode: String,

    val country: String? = null,
    val carrier: String? = null,
    val lineType: String? = null,
    val valid: Boolean? = null,
    val city: String? = null,
    val isSpam: Boolean = false,
    val spamType: String? = null,
    val nicknames: List<String> = emptyList()
)

data class TraceRow(
    val rawNumber: String,
    val number: String,
    val name: String?,
    val subtitle: String?
)

sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Result(val result: LookupResult) : LookupState
}
