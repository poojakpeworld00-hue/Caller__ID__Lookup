package com.callerid.number.lookup.home.ui.common

import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.data.CallFlavor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatting helpers shared by call-log adapters. */
object CallFormatter {

    fun displayName(name: String?, number: String): String =
        if (!name.isNullOrBlank()) name else number

    fun initials(name: String?, number: String): String {
        if (!name.isNullOrBlank()) {
            val parts = name.trim().split(" ").filter { it.isNotEmpty() }
            val letters = parts.take(2).joinToString("") { it.first().uppercase() }
            if (letters.isNotEmpty()) return letters
        }
        return "#"
    }

    fun timeLabel(date: Long): String {
        val pattern = if (isToday(date)) "h:mm a" else "MMM d"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(date))
    }

    fun durationLabel(seconds: Long): String {
        if (seconds <= 0) return ""
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    fun typeLabelRes(type: CallFlavor): Int = when (type) {
        CallFlavor.INCOMING -> R.string.type_incoming
        CallFlavor.OUTGOING -> R.string.type_outgoing
        CallFlavor.MISSED -> R.string.type_missed
        CallFlavor.SPAM -> R.string.type_blocked
    }

    fun typeIconRes(type: CallFlavor): Int = when (type) {
        CallFlavor.INCOMING -> R.drawable.sym_call_received
        CallFlavor.OUTGOING -> R.drawable.sym_call_made
        CallFlavor.MISSED -> R.drawable.sym_call_missed
        CallFlavor.SPAM -> R.drawable.sym_warning
    }

    fun typeColorRes(type: CallFlavor): Int = when (type) {
        CallFlavor.INCOMING, CallFlavor.OUTGOING -> R.color.on_surface_variant
        CallFlavor.MISSED -> R.color.danger
        CallFlavor.SPAM -> R.color.warn
    }

    private fun isToday(date: Long): Boolean {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        cal.timeInMillis = date
        return cal.get(Calendar.DAY_OF_YEAR) == today && cal.get(Calendar.YEAR) == year
    }
}
