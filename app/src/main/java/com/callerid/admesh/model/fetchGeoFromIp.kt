package com.callerid.admesh.model

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.io.use

private val locationClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
}

fun fetchGeoFromIp(): GeoSnapshot? {
    return try {
        val client = locationClient
        val request = Request.Builder()
            .url("http://ip-api.com/json/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body!!.string())
            GeoSnapshot(
                country = json.optString("country"),
                countryCode = json.optString("countryCode"),
                regionName = json.optString("regionName"),
                city = json.optString("city")
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}