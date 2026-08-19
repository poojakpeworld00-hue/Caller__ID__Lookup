package com.callerid.number.lookup.home.permit

data class PermitRule(

    val key: String,

    val enabled: Boolean,

    val activities: List<String>,

    val delayMs: Long,

    val priority: Int,

    val showOnce: Boolean,
)

data class PermitSpec(

    val key: String,

    val androidPermission: String,

    val minSdk: Int,

    val enabledPrefGate: String? = null,
)
