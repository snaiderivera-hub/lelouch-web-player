package com.lelouch.core.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = try {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Interceptor de resiliencia TLS para proveedores IPTV:
     * Si el servidor responde con error TLS (ej: "Unable to parse TLS packet header" porque el puerto
     * es HTTP plano en lugar de HTTPS, o problemas de handshake), reintenta automáticamente con HTTP.
     */
    class TlsFallbackInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            return try {
                chain.proceed(request)
            } catch (e: SSLException) {
                if (request.url.isHttps) {
                    val fallbackUrl = request.url.newBuilder().scheme("http").build()
                    val fallbackRequest = request.newBuilder().url(fallbackUrl).build()
                    chain.proceed(fallbackRequest)
                } else {
                    throw e
                }
            }
        }
    }

    fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(com.lelouch.core.network.dns.LelouchDnsManager)
            .connectionSpecs(listOf(okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.COMPATIBLE_TLS, okhttp3.ConnectionSpec.CLEARTEXT))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(WhitelistedUserAgentInterceptor())
            .addInterceptor(TlsFallbackInterceptor())
            .addInterceptor(SanitizedLoggingInterceptor())
            .retryOnConnectionFailure(true)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })

        if (sslContext != null) {
            try {
                val trustManager = trustAllCerts[0] as X509TrustManager
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            } catch (_: Exception) {}
        }

        return builder.build()
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
