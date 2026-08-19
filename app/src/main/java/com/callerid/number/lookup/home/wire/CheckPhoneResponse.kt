package com.callerid.number.lookup.home.wire

data class LookupResponse(
    val success: Boolean,
    val count: Int = 0,
    val data: List<LookupPayload>?
)

data class LookupPayload(
    val is_spam: Boolean = false,
    val is_user_spam: Boolean = false,
    val spamReportCounter: Int = 0,
    val spamType: String? = null,
    val name: String? = null,
    val profile: String? = null,
    val city: String? = null,
    val country: String? = null,
    val carrier: String? = null,

    val line_type: String? = null,
    val lineType: String? = null,
    val type: String? = null
) {

    val carrierOrNull: String? get() = carrier?.takeIf { it.isNotBlank() }

    val lineTypeOrNull: String?
        get() = (line_type ?: lineType ?: type)?.takeIf { it.isNotBlank() }
}
