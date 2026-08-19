package com.callerid.number.lookup.home.store

import android.content.Context
import android.provider.CallLog

data class CallEntry(
    val name: String?,
    val number: String,
    val type: CallFlavor,
    val date: Long,
    val durationSec: Long
)

data class FrequentDigit(
    val name: String?,
    val number: String,
    val count: Int
)

class CallHistorySource(private val context: Context) {

    fun getCalls(limit: Int = 500): List<CallEntry> {
        val result = mutableListOf<CallEntry>()
        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

            while (cursor.moveToNext() && result.size < limit) {
                val type = when (cursor.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> CallFlavor.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallFlavor.OUTGOING
                    CallLog.Calls.MISSED_TYPE -> CallFlavor.MISSED
                    CallLog.Calls.REJECTED_TYPE -> CallFlavor.MISSED
                    CallLog.Calls.BLOCKED_TYPE -> CallFlavor.SPAM
                    else -> CallFlavor.INCOMING
                }
                result.add(
                    CallEntry(
                        name = cursor.getString(nameIdx),
                        number = cursor.getString(numberIdx).orEmpty().ifBlank { "Unknown" },
                        type = type,
                        date = cursor.getLong(dateIdx),
                        durationSec = cursor.getLong(durationIdx)
                    )
                )
            }
        }
        return result
    }

    fun getMostUsed(limit: Int = 20): List<FrequentDigit> =
        getCalls(limit = 1000)
            .filter { it.number.isNotBlank() && !it.number.equals("Unknown", ignoreCase = true) }
            .groupBy { it.number }
            .map { (number, entries) ->
                FrequentDigit(
                    name = entries.firstOrNull { !it.name.isNullOrBlank() }?.name,
                    number = number,
                    count = entries.size
                )
            }
            .sortedByDescending { it.count }
            .take(limit)
}
