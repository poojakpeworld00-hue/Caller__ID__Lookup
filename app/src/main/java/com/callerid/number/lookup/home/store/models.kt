package com.callerid.number.lookup.home.store

enum class CallFlavor { INCOMING, OUTGOING, MISSED, SPAM }

data class CallCardModel(
    val name: String,
    val time: String,
    val info: String,
    val initials: String,
    val type: CallFlavor,
    val number: String = "",

    val identified: Boolean = true
)

data class ContactItem(
    val name: String,
    val detail: String,
    val initials: String,
    val photoUri: String? = null,
    val starred: Boolean = false,
    val lastContacted: Long = 0L,
    val inGroup: Boolean = false
)

object SampleData {

    val recents: List<CallCardModel> = listOf(
        CallCardModel("Sarah Khan", "9:24", "Incoming · 4m 12s", "SK", CallFlavor.INCOMING),
        CallCardModel("+1 (800) 244-0199", "8:50", "Spam · Telemarketer", "!", CallFlavor.SPAM),
        CallCardModel("Dad Mobile", "7:32", "Missed call", "DM", CallFlavor.MISSED),
        CallCardModel("+44 20 7946 0321", "Tue", "Outgoing · London, UK", "+9", CallFlavor.OUTGOING),
        CallCardModel("Aisha Lawson", "Tue", "Incoming · 1m 03s", "AL", CallFlavor.INCOMING)
    )

    val homeRecent: List<CallCardModel> = recents.take(2)

    val contacts: List<ContactItem> = listOf(
        ContactItem("Aisha Lawson", "+1 (415) 555-0178", "AL"),
        ContactItem("Amir Raza", "Acme Corp", "AR"),
        ContactItem("Dad Mobile", "+1 (415) 555-0143", "DM"),
        ContactItem("Sarah Khan", "Brightline Bank", "SK")
    )
}
