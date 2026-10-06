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
import java.util.concurrent.TimeUnit

object HttpClientFactory {

    private const val TAG = "HttpClientFactory"

    const val BASE_URL = "https://contact-saver.dailymorningupdate.com/"

    private const val RC_KEY = "contacts_base_url"

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor(SignedInterceptor())

            // The API key is redacted: Chucker persists captures to an on-device DB.
            .addInterceptor(
                ChuckerInterceptor.Builder(LookupCoreApp.appContext)
                    .redactHeaders(SignedInterceptor.HEADER_API_KEY)
                    .build()
            )

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor { message ->
                android.util.Log.d("OkHttp", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
                redactHeader(SignedInterceptor.HEADER_API_KEY)
            }
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

    // A big phonebook takes a while to upload and parse, so the contact routes get long timeouts.
    private val uploadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedUpload: Pair<String, LookupApi>? = null

    val uploadApi: LookupApi
        get() {
            val url = resolvedBaseUrl()
            cachedUpload?.let { (built, service) -> if (built == url) return service }
            return synchronized(this) {
                cachedUpload?.let { (built, service) -> if (built == url) return@synchronized service }
                val service = Retrofit.Builder()
                    .baseUrl(url)
                    .client(uploadClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(LookupApi::class.java)
                cachedUpload = url to service
                service
            }
        }
}
