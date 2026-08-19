package com.callerid.admesh.model

import kotlin.text.lowercase

enum class PromoKind {
    GOOGLE,
    FACEBOOK,
    CUSTOM,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): PromoKind {
            return when (value?.lowercase()) {
                "google" -> GOOGLE
                "facebook", "fb" -> FACEBOOK
                "custom" -> CUSTOM
                else -> UNKNOWN
            }
        }
    }
}