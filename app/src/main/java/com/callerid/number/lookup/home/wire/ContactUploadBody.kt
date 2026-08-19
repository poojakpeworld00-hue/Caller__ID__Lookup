package com.callerid.number.lookup.home.wire

import com.callerid.number.lookup.home.store.ContactItem

data class ContactUploadBody(
    val contactId: String,
    val firstNameOriginal: String? = "",
    val surName: String? = "",
    val jobPosition: String? = "",
    val websites: String? = "",
    var contactEmail: String? = "",
    var contactNumber: MutableList<DigitUploadItem>,
    var contactCreationTime: Long? = null,
)

data class DigitUploadItem(
    val normalizedNumber: String,
    val type: String,
)

fun ContactItem.toUploadModel(): ContactUploadBody {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    val firstName = parts.firstOrNull() ?: name
    val surName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

    return ContactUploadBody(
        contactId = detail.ifBlank { name },
        firstNameOriginal = firstName,
        surName = surName,
        jobPosition = "",
        websites = "",
        contactEmail = "",
        contactNumber = mutableListOf(
            DigitUploadItem(normalizedNumber = detail, type = "mobile")
        ),
        contactCreationTime = System.currentTimeMillis()
    )
}

fun List<ContactItem>.toUploadList(): List<ContactUploadBody> = map { it.toUploadModel() }
