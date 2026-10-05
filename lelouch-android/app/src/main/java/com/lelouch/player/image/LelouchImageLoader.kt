package com.lelouch.player.image

import android.content.Context
import android.util.Log
import coil.EventListener
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult
import coil.size.Size
import com.lelouch.core.network.NetworkClient
import com.lelouch.core.network.UrlSanitizer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Metric collector for Coil operations on Android TV.
 */
object LelouchImageMetrics {
    val networkRequests = AtomicInteger(0)
    val memoryCacheHits = AtomicInteger(0)
    val diskCacheHits = AtomicInteger(0)
    val decodeCount = AtomicInteger(0)
    val errorCount = AtomicInteger(0)

    fun reset() {
        networkRequests.set(0)
        memoryCacheHits.set(0)
        diskCacheHits.set(0)
        decodeCount.set(0)
        errorCount.set(0)
    }
}

/**
 * Diagnostic and credential-safe EventListener for Coil requests.
 */
class LelouchImageEventListener : EventListener {
    private var startNs: Long = 0L
    private var resolvedSize: Size? = null

    override fun onStart(request: ImageRequest) {
        startNs = System.nanoTime()
    }

    override fun resolveSizeEnd(request: ImageRequest, size: Size) {
        resolvedSize = size
    }

    override fun decodeEnd(
        request: ImageRequest,
        decoder: Decoder,
        options: Options,
        result: DecodeResult?
    ) {
        if (result != null) {
            LelouchImageMetrics.decodeCount.incrementAndGet()
        }
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        val durationMs = (System.nanoTime() - startNs) / 1_000_000
        when (result.dataSource) {
            DataSource.MEMORY_CACHE, DataSource.MEMORY -> LelouchImageMetrics.memoryCacheHits.incrementAndGet()
            DataSource.DISK -> LelouchImageMetrics.diskCacheHits.incrementAndGet()
            DataSource.NETWORK -> LelouchImageMetrics.networkRequests.incrementAndGet()
        }

        val rawUrl = request.data?.toString()
        val sanitized = UrlSanitizer.sanitizeUrl(rawUrl)
        val drawable = result.drawable
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight

        Log.d(
            "LelouchCoil",
            "[SUCCESS] src=${result.dataSource} | req=$resolvedSize | decoded=${w}x${h} | took=${durationMs}ms | url=$sanitized"
        )
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        val durationMs = (System.nanoTime() - startNs) / 1_000_000
        LelouchImageMetrics.errorCount.incrementAndGet()
        val rawUrl = request.data?.toString()
        val sanitized = UrlSanitizer.sanitizeUrl(rawUrl)
        Log.e(
            "LelouchCoil",
            "[ERROR] req=$resolvedSize | took=${durationMs}ms | err=${result.throwable.message} | url=$sanitized"
        )
    }
}

/**
 * Singleton factory creating the centralized ImageLoader for Android TV.
 */
object LelouchImageLoader {

    const val MEMORY_CACHE_PERCENT = 0.20
    const val DISK_CACHE_MAX_BYTES = 150L * 1024 * 1024 // 150 MB

    fun create(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .okHttpClient {
                NetworkClient.createOkHttpClient()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(false)
            .eventListenerFactory {
                LelouchImageEventListener()
            }
            .build()
    }
}
