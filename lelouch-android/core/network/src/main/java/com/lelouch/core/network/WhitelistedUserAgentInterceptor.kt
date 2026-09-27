package com.lelouch.core.network

import okhttp3.Interceptor
import okhttp3.Response

class WhitelistedUserAgentInterceptor(
    private val userAgent: String = "IPTVSmartersPlayer / VLC 3.0.18"
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestWithUserAgent = originalRequest.newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .build()
        return chain.proceed(requestWithUserAgent)
    }
}
