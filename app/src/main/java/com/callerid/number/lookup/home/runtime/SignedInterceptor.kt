package com.callerid.number.lookup.home.runtime

import okhttp3.Interceptor

class SignedInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()

            .build()
        return chain.proceed(request)
    }
}
