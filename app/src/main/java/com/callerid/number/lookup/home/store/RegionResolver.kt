package com.callerid.number.lookup.home.store

import android.content.Context
import com.callerid.admesh.model.fetchGeoFromIp
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.screen.identify.DialCountries
import com.callerid.number.lookup.home.kit.LogRail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RegionResolver {

    private const val TAG = "RegionResolver"

    private val DEBUG_FORCE_ISO: String? = "JP"

    data class GeoCountry(val iso: String, val dial: String)

    suspend fun detectCountry(context: Context): GeoCountry? {

        if (BuildConfig.DEBUG) {
            validCountry(DEBUG_FORCE_ISO)?.let { forced ->
                LogRail.log(TAG, "DEBUG force country=$forced (cache/network bypassed)")
                return GeoCountry(forced, DialCountries.dialOf(forced).orEmpty())
            }
        }

        val prefs = StorageRegistry(context)

        val cached = validCountry(prefs.homeCountryIso) ?: validCountry(prefs.geoCountryIso)
        if (cached != null) {
            LogRail.log(TAG, "reuse cached country=$cached (no IP call)")
            return GeoCountry(cached, DialCountries.dialOf(cached).orEmpty())
        }

        val geo = detectCountryFromIp() ?: return null

        prefs.geoCountryIso = geo.iso
        LogRail.log(TAG, "cached detected country=${geo.iso} for app-wide reuse")
        return geo
    }

    private fun validCountry(raw: String?): String? {
        val iso = raw?.takeIf { it.length == 2 }?.uppercase() ?: return null
        return iso.takeIf { DialCountries.dialOf(it) != null }
    }

    private suspend fun detectCountryFromIp(): GeoCountry? = withContext(Dispatchers.IO) {
        LogRail.log(TAG, "no cache → fetchGeoFromIp()")
        val location = fetchGeoFromIp()
        if (location == null) {
            LogRail.log(TAG, "fetchGeoFromIp() returned null → null")
            return@withContext null
        }
        LogRail.log(TAG, "location: country=${location.country} code=${location.countryCode}")

        val iso = location.countryCode?.takeIf { it.length == 2 }?.uppercase()
        if (iso == null) {
            LogRail.log(TAG, "no valid countryCode → null")
            return@withContext null
        }
        val dial = DialCountries.dialOf(iso).orEmpty()
        LogRail.log(TAG, "resolved country=$iso dial=$dial")
        GeoCountry(iso, dial)
    }
}
