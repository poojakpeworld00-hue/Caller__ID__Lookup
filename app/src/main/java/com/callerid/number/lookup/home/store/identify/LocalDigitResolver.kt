package com.callerid.number.lookup.home.store.identify

import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

data class LocalDigitInfo(
    val valid: Boolean,
    val regionName: String?,
    val location: String?,
    val carrier: String?,
    val lineType: String?
)

object LocalDigitResolver {

    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder: PhoneNumberOfflineGeocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }
    private val carrierMapper: PhoneNumberToCarrierMapper by lazy { PhoneNumberToCarrierMapper.getInstance() }

    private val TYPE_LABELS: Map<PhoneNumberType, String> = mapOf(
        PhoneNumberType.MOBILE to "Mobile",
        PhoneNumberType.FIXED_LINE to "Landline",
        PhoneNumberType.FIXED_LINE_OR_MOBILE to "Mobile / Landline",
        PhoneNumberType.VOIP to "VoIP",
        PhoneNumberType.TOLL_FREE to "Toll-free",
        PhoneNumberType.PREMIUM_RATE to "Premium rate",
        PhoneNumberType.SHARED_COST to "Shared cost",
        PhoneNumberType.PAGER to "Pager",
        PhoneNumberType.PERSONAL_NUMBER to "Personal",
        PhoneNumberType.UAN to "UAN",
        PhoneNumberType.VOICEMAIL to "Voicemail",
    )

    private inline fun <T : Any> probe(block: () -> T?): T? =
        try { block() } catch (_: Throwable) { null }

    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun parseRegion(normalized: String, fallbackRegion: String?): String? = when {
        normalized.startsWith("+") -> null
        else -> fallbackRegion.orNullIfBlank()?.uppercase(Locale.ROOT) ?: "US"
    }

    fun lookup(normalized: String, fallbackRegion: String?): LocalDigitInfo? {
        val parsed = probe { phoneUtil.parse(normalized, parseRegion(normalized, fallbackRegion)) }
            ?: return null

        return LocalDigitInfo(
            valid = probe { phoneUtil.isValidNumber(parsed) } ?: false,
            regionName = probe { phoneUtil.getRegionCodeForNumber(parsed) }
                ?.let { iso -> probe { Locale("", iso).getDisplayCountry(Locale.ENGLISH) } }
                .orNullIfBlank(),
            location = probe { geocoder.getDescriptionForNumber(parsed, Locale.ENGLISH) }.orNullIfBlank(),
            carrier = probe { carrierMapper.getNameForNumber(parsed, Locale.ENGLISH) }.orNullIfBlank(),
            lineType = probe { TYPE_LABELS[phoneUtil.getNumberType(parsed)] },
        )
    }
}
