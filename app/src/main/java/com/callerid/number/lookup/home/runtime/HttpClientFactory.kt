package com.callerid.number.lookup.home.runtime

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.LookupCoreApp
import com.callerid.number.lookup.home.kit.LogRail
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object HttpClientFactory {

    private const val TAG = "HttpClientFactory"

    const val BASE_URL = "https://callerid.kpeworld.com/"

    private const val RC_KEY = "api_base_url"

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor(SignedInterceptor())

            .addInterceptor(ChuckerInterceptor.Builder(LookupCoreApp.appContext).build())

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor { message ->
                android.util.Log.d("OkHttp", message)
            }.apply { level = HttpLoggingInterceptor.Level.BODY }
            builder.addInterceptor(logging)
        }

        builder.build()
    }

    fun resolvedBaseUrl(): String {
        val raw = runCatching {
            PromoVault.getInstance(LookupCoreApp.appContext).getString(RC_KEY, "").orEmpty()
        }.getOrDefault("").trim()

        if (raw.isBlank()) return BASE_URL

        val normalized = if (raw.endsWith("/")) raw else "$raw/"

        if (normalized.toHttpUrlOrNull() == null) {
            LogRail.error(TAG, "ignoring malformed $RC_KEY='$raw' — using $BASE_URL")
            return BASE_URL
        }

        return normalized
    }

    @Volatile
    private var cached: Pair<String, LookupApi>? = null

    val api: LookupApi
        get() {
            val url = resolvedBaseUrl()
            cached?.let { (built, service) -> if (built == url) return service }

            return synchronized(this) {
                cached?.let { (built, service) -> if (built == url) return@synchronized service }

                LogRail.log(TAG, "building API client for $url")
                val service = Retrofit.Builder()
                    .baseUrl(url)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(LookupApi::class.java)

                cached = url to service
                service
            }
        }
}
