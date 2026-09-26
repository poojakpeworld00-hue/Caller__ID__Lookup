package com.callerid.admesh.engine

import com.callerid.number.lookup.home.BuildConfig
import com.google.android.gms.tasks.Task
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

    /**
     * The `setConfigSettingsAsync` task, kept so [withSettings] can wait for it.
     *
     * Not a bare boolean: the call is asynchronous, and firing `fetchAndActivate` before it
     * lands leaves the SDK on its DEFAULT 12-hour minimum fetch interval — which answers from
     * cache and still reports success, so a freshly published template silently never arrives.
     */
    @Volatile
    private var settingsTask: Task<Void>? = null

    /** Applies the settings once per process; later calls reuse the first task. */
    fun applyTo(remoteConfig: FirebaseRemoteConfig): Task<Void>? {
        if (settingsTask == null) {
            synchronized(this) {
                if (settingsTask == null) {
                    settingsTask = remoteConfig.setConfigSettingsAsync(settings())
                }
            }
        }
        return settingsTask
    }

    /**
     * Runs [block] with the settings guaranteed to be in effect.
     *
     * Every fetch has to go through here. Already-complete is the steady state, so this costs
     * one branch on every fetch after the first — and buys the one case that matters: the very
     * first fetch of a cold start, which is exactly the fetch that decides whether a template
     * published while the app was closed arrives at all.
     */
    fun withSettings(remoteConfig: FirebaseRemoteConfig, block: () -> Unit) {
        val task = applyTo(remoteConfig)
        if (task == null || task.isComplete) block() else task.addOnCompleteListener { block() }
    }
}
