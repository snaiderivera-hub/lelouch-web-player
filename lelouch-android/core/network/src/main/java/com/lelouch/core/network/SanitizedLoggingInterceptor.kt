package com.lelouch.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class SanitizedLoggingInterceptor(
    private val tag: String = "LelouchNetwork"
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url.toString()
        val sanitizedUrl = UrlSanitizer.sanitizeUrl(originalUrl)

        Log.d(tag, "--> ${request.method} $sanitizedUrl")
        val startNs = System.nanoTime()

        val response: Response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(tag, "<-- HTTP ERROR on $sanitizedUrl: ${e.message}")
            throw e
        }

        val tookMs = (System.nanoTime() - startNs) / 1e6
        Log.d(tag, "<-- ${response.code} ${response.message} ($tookMs ms) $sanitizedUrl")

        return response
    }
}
