package com.callerid.number.lookup.home.store.identify

import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

/** Number metadata resolved fully offline via libphonenumber (no network). */
data class LocalDigitInfo(
    val valid: Boolean,
    val regionName: String?,  // country, e.g. "India"
    val location: String?,    // geocoded area, e.g. "California" / "Bengaluru"
    val carrier: String?,     // carrier name, e.g. "Airtel"
    val lineType: String?     // Mobile / Landline / VoIP / ...
)

/**
 * Offline number lookup using Google's libphonenumber:
 *  - [PhoneNumberOfflineGeocoder] → location description
 *  - [PhoneNumberToCarrierMapper] → carrier name
 *  - [PhoneNumberUtil] → validity, line type, country.
 *
 * Replaces the old numverify (apilayer.net) online call. Safe to run off the
 * main thread; instances are reused (the metadata loads lazily).
 */
object LocalDigitResolver {

    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder: PhoneNumberOfflineGeocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }
    private val carrierMapper: PhoneNumberToCarrierMapper by lazy { PhoneNumberToCarrierMapper.getInstance() }

    /** Line-type labels, keyed rather than branched, so the table reads as data. */
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

    /**
     * Every metadata probe is independent: a missing geocoder or carrier metadata
     * file must not take the other fields down with it. [probe] is that guarantee
     * in one place instead of a runCatching at each call site.
     */
    private inline fun <T : Any> probe(block: () -> T?): T? =
        try { block() } catch (_: Throwable) { null }

    /** Blank strings carry no more information than null, so collapse them. */
    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    /**
     * Chooses the parse region. An E.164 number carries its own country code, so it
     * needs none; a bare number without a usable hint falls back to "US" rather than
     * passing blank through, which would fail the parse and null out every field.
     */
    private fun parseRegion(normalized: String, fallbackRegion: String?): String? = when {
        normalized.startsWith("+") -> null
        else -> fallbackRegion.orNullIfBlank()?.uppercase(Locale.ROOT) ?: "US"
    }

    /**
     * @param normalized     the number, ideally E.164 ("+…"); bare numbers use [fallbackRegion].
     * @param fallbackRegion ISO-3166 alpha-2 region (e.g. "IN") for bare numbers.
     */
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
