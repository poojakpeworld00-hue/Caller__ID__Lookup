package com.callerid.number.lookup.home.store.identify

import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage-3a rewrote this resolver: the per-field runCatching chain became a single
 * `probe` helper, region selection moved into `parseRegion`, and the line-type
 * `when` became a lookup table.
 *
 * [reference] is the pre-rewrite algorithm, kept verbatim, and
 * [matches the pre-rewrite implementation exactly] runs both over a corpus and
 * compares every field. That is the actual proof the twist preserved behaviour —
 * the assertions below only document what that behaviour is.
 */
class LocalDigitResolverTest {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
    private val geocoder: PhoneNumberOfflineGeocoder = PhoneNumberOfflineGeocoder.getInstance()
    private val carrierMapper: PhoneNumberToCarrierMapper = PhoneNumberToCarrierMapper.getInstance()

    /** The original implementation, unchanged, as the oracle. */
    private fun reference(normalized: String, fallbackRegion: String?): LocalDigitInfo? {
        val parsed = runCatching {
            val region = if (normalized.startsWith("+")) null
            else (fallbackRegion?.takeIf { it.isNotBlank() } ?: "US").uppercase(Locale.ROOT)
            phoneUtil.parse(normalized, region)
        }.getOrNull() ?: return null

        val valid = runCatching { phoneUtil.isValidNumber(parsed) }.getOrDefault(false)
        val location = runCatching { geocoder.getDescriptionForNumber(parsed, Locale.ENGLISH) }
            .getOrNull()?.ifBlank { null }
        val carrier = runCatching { carrierMapper.getNameForNumber(parsed, Locale.ENGLISH) }
            .getOrNull()?.ifBlank { null }
        val lineType = runCatching { referenceType(phoneUtil.getNumberType(parsed)) }.getOrNull()
        val regionName = runCatching {
            phoneUtil.getRegionCodeForNumber(parsed)
                ?.let { Locale("", it).getDisplayCountry(Locale.ENGLISH).ifBlank { null } }
        }.getOrNull()

        return LocalDigitInfo(valid, regionName, location, carrier, lineType)
    }

    private fun referenceType(type: PhoneNumberType): String? = when (type) {
        PhoneNumberType.MOBILE -> "Mobile"
        PhoneNumberType.FIXED_LINE -> "Landline"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Mobile / Landline"
        PhoneNumberType.VOIP -> "VoIP"
        PhoneNumberType.TOLL_FREE -> "Toll-free"
        PhoneNumberType.PREMIUM_RATE -> "Premium rate"
        PhoneNumberType.SHARED_COST -> "Shared cost"
        PhoneNumberType.PAGER -> "Pager"
        PhoneNumberType.PERSONAL_NUMBER -> "Personal"
        PhoneNumberType.UAN -> "UAN"
        PhoneNumberType.VOICEMAIL -> "Voicemail"
        else -> null
    }

    private val corpus: List<Pair<String, String?>> = listOf(
        "+919876543210" to null, "+12125551234" to null, "+18005551212" to null,
        "+442071838750" to null, "+4915112345678" to null, "+81312345678" to null,
        "+911" to null, "+" to null, "" to null, "not-a-number" to "IN",
        "9876543210" to "IN", "2125551234" to "", "07911123456" to "GB",
        "9876543210" to null, "5551234" to "US", "000" to "IN",
        "+19005551212" to null, "+441212345678" to null, "12345678901234567890" to "IN",
        "+33612345678" to null, "+61412345678" to "AU", "  " to "IN",
    )

    @Test
    fun `matches the pre-rewrite implementation exactly`() {
        for ((number, region) in corpus) {
            val expected = reference(number, region)
            val actual = LocalDigitResolver.lookup(number, region)
            assertEquals("lookup(\"$number\", $region)", expected, actual)
        }
    }

    @Test
    fun `E164 number resolves without a fallback region`() {
        val info = LocalDigitResolver.lookup("+919876543210", null)
        assertNotNull(info)
        assertTrue(info!!.valid)
        assertEquals("India", info.regionName)
        assertEquals("Mobile", info.lineType)
    }

    @Test
    fun `bare number uses the fallback region`() {
        assertEquals("India", LocalDigitResolver.lookup("9876543210", "IN")?.regionName)
    }

    /** A blank region must not null out every field — it falls back to US. */
    @Test
    fun `blank fallback region still parses`() {
        assertEquals("United States", LocalDigitResolver.lookup("2125551234", "")?.regionName)
    }

    @Test
    fun `line type labels come from the table`() {
        // 212-555-1234 is FIXED_LINE_OR_MOBILE in libphonenumber's US metadata.
        assertEquals("Mobile / Landline", LocalDigitResolver.lookup("+12125551234", null)?.lineType)
        assertEquals("Toll-free", LocalDigitResolver.lookup("+18005551212", null)?.lineType)
    }

    @Test
    fun `unparseable input yields null rather than a blank record`() {
        assertNull(LocalDigitResolver.lookup("not-a-number", "IN"))
        assertNull(LocalDigitResolver.lookup("", "IN"))
        assertNull(LocalDigitResolver.lookup("+911", null))
    }
}
