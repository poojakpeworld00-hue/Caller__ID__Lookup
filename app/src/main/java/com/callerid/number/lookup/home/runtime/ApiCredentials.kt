package com.callerid.number.lookup.home.runtime

object ApiCredentials {

    const val API_ID = "1433"

    const val API_HASH = "o9rRirgwnsAVIivUG3T0OVjpwTE="

    const val API_TOKEN =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxNDMzLCJpYXQiOjE3NzQyNjAwNDF9.PrHzeB_P3hv-FEo87k8yOGVA2YMdNLdrra_ix7uSt0w"

    val isConfigured: Boolean
        get() = listOf(API_ID, API_HASH, API_TOKEN).none { it.startsWith("REPLACE_ME") }
}
