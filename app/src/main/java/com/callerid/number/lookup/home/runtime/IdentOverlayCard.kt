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
import com.callerid.number.lookup.home.screen.identify.DialCountries
import com.callerid.number.lookup.home.screen.identify.NumberInfo
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.wire.LookupPayload
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object IdentOverlayCard {

    private const val TAG = "IdentOverlayCard"

    /**
     * The card is on screen for as long as the phone rings, so a lookup that has not answered
     * by now is one the user will never see the result of.
     */
    private const val LOOKUP_TIMEOUT_MS = 6_000L

    data class Info(
        val name: String?,
        val known: Boolean,
        val callCount: Int,
        val network: String?,
        /** The name came from the lookup API rather than the device. */
        val fromApi: Boolean = false,
        /** The API flagged this number as spam. */
        val spam: Boolean = false
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

    /**
     * Fills the card in two passes.
     *
     * The device knows things instantly — contact name, how often this number has called,
     * the network — so that goes up first and the card is never blank while a request is in
     * flight. Only then, and only for a number the device could not name, does it ask the
     * same lookup API the search screen uses.
     *
     * A saved contact always wins: it is the name the user chose for this person, and the
     * crowd-sourced one would be a downgrade.
     */
    suspend fun bindResolving(context: Context, root: View, number: String) {
        val local = withContext(Dispatchers.IO) { resolve(context, number) }
        bind(context, root, number, local)
        if (local.known) return

        val remote = withContext(Dispatchers.IO) { lookupOnline(context, number) } ?: return
        val name = remote.name?.trim()?.takeIf { it.isNotBlank() }
        val spam = remote.is_spam || remote.is_user_spam
        if (name == null && !spam) return

        bind(
            context, root, number,
            local.copy(
                name = name ?: local.name,
                known = name != null,
                fromApi = name != null,
                spam = spam,
            )
        )
    }

    /**
     * The search screen's `api/similar-phone-number` call, for one number.
     *
     * Returns the first match, which is the API's own best guess; the alternate names it
     * lists below that are for the search result page, not a card the user reads mid-ring.
     * Any failure — no credentials, no network, a slow server — is a null, and the card
     * keeps whatever the device could tell it.
     */
    private suspend fun lookupOnline(context: Context, number: String): LookupPayload? =
        runCatching {
            if (!ApiCredentials.isConfigured) {
                Log.w(TAG, "lookup skipped: API credentials are placeholders")
                return@runCatching null
            }
            val response = withTimeout(LOOKUP_TIMEOUT_MS) {
                HttpClientFactory.api.checkPhoneNumber(
                    id = ApiCredentials.API_ID,
                    phone = e164(context, number),
                    hashKey = ApiCredentials.API_HASH,
                    token = ApiCredentials.API_TOKEN,
                )
            }
            if (!response.isSuccessful) {
                Log.w(TAG, "lookup failed (${response.code()})")
                return@runCatching null
            }
            response.body()?.data?.firstOrNull()
        }.onFailure { Log.w(TAG, "lookup error: ${it.message}") }.getOrNull()

    /**
     * The API matches on the international form, but an incoming number often arrives in the
     * local one ("9876543210"). Prefixed with the dial code of the country the splash
     * resolved from IP, falling back to the SIM's, and left alone when neither is known —
     * sending a local number is better than sending one with the wrong country on it.
     */
    private fun e164(context: Context, number: String): String {
        val normalized = NumberInfo.normalize(number)
        if (normalized.startsWith("+")) return normalized

        val iso = StorageRegistry(context).homeCountryIso
            .ifBlank {
                runCatching {
                    (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                        ?.simCountryIso.orEmpty()
                }.getOrDefault("")
            }
        val dial = iso.takeIf { it.length == 2 }?.let { DialCountries.dialOf(it) } ?: return normalized
        return "+" + dial + normalized.filter { it.isDigit() }.takeLast(10)
    }

    fun bind(context: Context, root: View, number: String, info: Info) {
        val displayName = info.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.incall_unknown)

        root.findViewById<TextView>(R.id.lblIncallAvatar).text =
            CallFormatter.initials(info.name, number)
        root.findViewById<TextView>(R.id.lblIncallName).text = displayName
        root.findViewById<TextView>(R.id.lblIncallNumber).text = number

        bindStatusPill(context, root.findViewById(R.id.lblIncallStatus), info)

        root.findViewById<TextView>(R.id.lblIncallWhen).text =
            context.getString(R.string.incall_now)
        root.findViewById<TextView>(R.id.lblIncallCalls).text =
            context.getString(R.string.incall_calls, info.callCount)
        root.findViewById<TextView>(R.id.lblIncallNetwork).text =
            info.network?.takeIf { it.isNotBlank() } ?: "—"
    }

    private fun bindStatusPill(context: Context, pill: TextView, info: Info) {
        
        val known = info.known
        val textRes = when {
            info.spam -> R.string.incall_spam
            known -> R.string.incall_known
            else -> R.string.incall_unknown
        }
        val fgRes = when {
            info.spam -> R.color.danger
            known -> R.color.success
            else -> R.color.on_surface_variant
        }
        val bgRes = when {
            info.spam -> R.color.danger_soft
            known -> R.color.success_soft
            else -> R.color.neutral_soft
        }
        val iconRes = when {
            info.spam -> R.drawable.sym_block
            known -> R.drawable.sym_verified
            else -> R.drawable.sym_info
        }

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
