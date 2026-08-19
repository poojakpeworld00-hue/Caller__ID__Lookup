package com.callerid.admesh.engine

import com.callerid.number.lookup.home.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object RemoteConfigRules {

    private val MIN_FETCH_INTERVAL_SECONDS = if (BuildConfig.DEBUG) 0L else 1L

    private const val FETCH_TIMEOUT_SECONDS = 10L

    fun settings(): FirebaseRemoteConfigSettings =
        FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(MIN_FETCH_INTERVAL_SECONDS)
            .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
            .build()

    fun applyTo(remoteConfig: FirebaseRemoteConfig) {
        remoteConfig.setConfigSettingsAsync(settings())
    }
}
