package com.callerid.number.lookup.home.runtime

import android.content.Context
import android.content.res.ColorStateList
import android.telephony.TelephonyManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.CallHistorySource
import com.callerid.number.lookup.home.store.ContactSource
import com.callerid.number.lookup.home.screen.shared.CallFormatter

object IdentOverlayCard {

    data class Info(
        val name: String?,
        val known: Boolean,
        val callCount: Int,
        val network: String?
    )

    fun resolve(context: Context, number: String): Info {
        val name = runCatching { ContactSource(context).lookupNameByNumber(number) }.getOrNull()

        val callCount = runCatching {
            val target = digitsTail(number)
            CallHistorySource(context).getCalls(limit = 2000)
                .count { digitsTail(it.number) == target }
        }.getOrDefault(0)

        val network = runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.networkOperatorName?.takeIf { it.isNotBlank() }
        }.getOrNull()

        return Info(name = name, known = !name.isNullOrBlank(), callCount = callCount, network = network)
    }

    fun bind(context: Context, root: View, number: String, info: Info) {
        val displayName = info.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.incall_unknown)

        root.findViewById<TextView>(R.id.lblIncallAvatar).text =
            CallFormatter.initials(info.name, number)
        root.findViewById<TextView>(R.id.lblIncallName).text = displayName
        root.findViewById<TextView>(R.id.lblIncallNumber).text = number

        bindStatusPill(context, root.findViewById(R.id.lblIncallStatus), info.known)

        root.findViewById<TextView>(R.id.lblIncallWhen).text =
            context.getString(R.string.incall_now)
        root.findViewById<TextView>(R.id.lblIncallCalls).text =
            context.getString(R.string.incall_calls, info.callCount)
        root.findViewById<TextView>(R.id.lblIncallNetwork).text =
            info.network?.takeIf { it.isNotBlank() } ?: "—"
    }

    private fun bindStatusPill(context: Context, pill: TextView, known: Boolean) {
        val textRes = if (known) R.string.incall_known else R.string.incall_unknown
        val fgRes = if (known) R.color.success else R.color.on_surface_variant
        val bgRes = if (known) R.color.success_soft else R.color.neutral_soft
        val iconRes = if (known) R.drawable.sym_verified else R.drawable.sym_info

        val fg = ContextCompat.getColor(context, fgRes)
        pill.setText(textRes)
        pill.setTextColor(fg)
        pill.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bgRes))
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        TextViewCompat.setCompoundDrawableTintList(pill, ColorStateList.valueOf(fg))
    }

    private fun digitsTail(number: String): String =
        number.filter { it.isDigit() }.takeLast(9)
}
