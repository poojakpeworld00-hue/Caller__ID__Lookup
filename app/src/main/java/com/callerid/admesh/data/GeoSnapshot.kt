package com.callerid.admesh.data

data class GeoSnapshot(
    val country: String?,
    val countryCode: String?,   // ISO 3166-1 alpha-2, e.g. "IN"
    val regionName: String?,
    val city: String?
)