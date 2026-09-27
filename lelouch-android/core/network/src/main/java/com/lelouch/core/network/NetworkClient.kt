package com.lelouch.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(WhitelistedUserAgentInterceptor())
            .addInterceptor(SanitizedLoggingInterceptor())
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createXtreamApiService(baseUrl: String, okHttpClient: OkHttpClient = createOkHttpClient()): XtreamApiService {
        val sanitizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(sanitizedBase)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(XtreamApiService::class.java)
    }
}
