package com.callerid.number.lookup.home.runtime

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor

class SignedInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val url = request.url
        val key = ApiCredentials.API_KEY
        val apiHost = HttpClientFactory.resolvedBaseUrl().toHttpUrlOrNull()?.host

        // Only over HTTPS to the configured API host: the base URL is Remote Config driven.
        val send = key.isNotBlank() &&
            url.isHttps &&
            apiHost != null &&
            url.host.equals(apiHost, ignoreCase = true)

        if (!send) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER_API_KEY, key).build())
    }

    companion object {
        const val HEADER_API_KEY = "x-api-key"
    }
}
