package com.lelouch.core.data.sync

import androidx.room.withTransaction
import com.lelouch.core.data.preferences.SourceSyncMetadata
import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.database.entity.CategoryEntity
import com.lelouch.core.database.entity.CategoryStagingEntity
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.ChannelStagingEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.MovieStagingEntity
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.database.entity.SeriesStagingEntity
import com.lelouch.core.model.CatalogDownloadProgress
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.network.NetworkClient
import com.lelouch.core.network.StreamUrlResolver
import com.lelouch.core.network.UrlSanitizer
import com.lelouch.core.network.XtreamStreamingParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface SyncState {
    data object Idle : SyncState
    data class Checking(val sourceId: String) : SyncState
    data class UpToDate(val sourceId: String, val message: String = "Catálogo al día (comprobado)") : SyncState
    data class TtlFresh(val sourceId: String, val message: String = "Catálogo dentro de TTL") : SyncState
    data class OfflineUsingCache(val sourceId: String, val message: String = "Modo sin conexión - Catálogo local disponible") : SyncState
    data object Authenticating : SyncState
    data class SyncingCategories(val step: String) : SyncState
    data class SyncingLive(val count: Int) : SyncState
    data class SyncingMovies(val count: Int) : SyncState
    data class SyncingSeries(val count: Int) : SyncState
    data class Completed(val channelsCount: Int, val moviesCount: Int, val seriesCount: Int) : SyncState
    data class Error(val message: String) : SyncState
}

sealed interface FreshnessResult {
    data class Unchanged(val version: String, val etag: String = "") : FreshnessResult
    data class TtlFresh(val lastSyncAt: Long, val ttlMs: Long) : FreshnessResult
    data class Changed(val newVersion: String, val newEtag: String = "") : FreshnessResult
    data class CheckFailed(val reason: String) : FreshnessResult
}

class XtreamCatalogSyncManager private constructor(
    database: LelouchDatabase?,
    private val preferencesDataSource: UserPreferencesDataSource?,
    private val httpClient: OkHttpClient
) {
    constructor(
        database: LelouchDatabase,
        preferencesDataSource: UserPreferencesDataSource? = null
    ) : this(
        database = database,
        preferencesDataSource = preferencesDataSource,
        httpClient = defaultHttpClient
    )

    constructor(
        httpClient: OkHttpClient
    ) : this(
        database = null,
        preferencesDataSource = null,
        httpClient = httpClient
    )

    constructor() : this(
        database = null,
        preferencesDataSource = null,
        httpClient = defaultHttpClient
    )

    private val database: LelouchDatabase by lazy {
        database ?: error("LelouchDatabase is required for database operations")
    }
    companion object {
        val defaultHttpClient: OkHttpClient by lazy {
            NetworkClient.createOkHttpClient()
        }
        const val XTREAM_DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L // 24 horas para Live
        const val VOD_DEFAULT_TTL_MS: Long = 3L * 24 * 60 * 60 * 1000L // 3 días para VOD y Series
        const val M3U_DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L // 24 horas para M3U genérico
        private const val HOST_COOLDOWN_MS: Long = 12 * 60 * 1000L // 12 min circuit breaker por host caído
    }

    private class TransientHttpException(val code: Int, message: String) : IOException(message)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _downloadConsole = MutableStateFlow(CatalogDownloadProgress())
    val downloadConsole: StateFlow<CatalogDownloadProgress> = _downloadConsole.asStateFlow()

    private fun sanitizeConsoleText(raw: String): String {
        return raw.replace(Regex("https?://\\S+")) { m -> UrlSanitizer.sanitizeUrl(m.value) }
    }

    fun beginDownloadConsole(step: String = "Analizando URL...", percent: Int = 5) {
        val cleanStep = sanitizeConsoleText(step)
        val clamped = percent.coerceIn(0, 100)
        _downloadConsole.value = CatalogDownloadProgress(
            isActive = true,
            percent = clamped,
            currentStep = cleanStep,
            logs = listOf("[$clamped%] $cleanStep"),
            isError = false,
            isCompleted = false
        )
    }

    fun appendDownloadConsoleStep(percent: Int, step: String) {
        val prev = _downloadConsole.value
        if (!prev.isActive && prev.logs.isEmpty()) return
        val cleanStep = sanitizeConsoleText(step)
        val clamped = percent.coerceIn(prev.percent, 100)
        val line = "[$clamped%] $cleanStep"
        val nextLogs = if (prev.logs.lastOrNull() == line) prev.logs else (prev.logs + line).takeLast(16)
        _downloadConsole.value = prev.copy(
            isActive = true,
            percent = clamped,
            currentStep = cleanStep,
            logs = nextLogs,
            isError = false,
            isCompleted = false
        )
    }

    fun completeDownloadConsole(step: String) {
        val prev = _downloadConsole.value
        if (!prev.isActive && prev.logs.isEmpty()) return
        val cleanStep = sanitizeConsoleText(step)
        val line = "[100%] $cleanStep"
        val nextLogs = if (prev.logs.lastOrNull() == line) prev.logs else (prev.logs + line).takeLast(16)
        _downloadConsole.value = prev.copy(
            isActive = false,
            percent = 100,
            currentStep = cleanStep,
            logs = nextLogs,
            isError = false,
            isCompleted = true
        )
    }

    fun failDownloadConsole(reason: String) {
        val prev = _downloadConsole.value
        val cleanReason = sanitizeConsoleText(reason).ifBlank { "Error de conexión con el servidor" }
        val line = "[Error] $cleanReason"
        val nextLogs = if (prev.logs.lastOrNull() == line) prev.logs else (prev.logs + line).takeLast(16)
        _downloadConsole.value = prev.copy(
            isActive = false,
            currentStep = cleanReason,
            logs = nextLogs,
            isError = true,
            isCompleted = false
        )
    }

    fun resetDownloadConsole() {
        _downloadConsole.value = CatalogDownloadProgress()
    }

    private fun formatConsoleExpireDate(rawExpire: String?): String {
        val epochSecs = rawExpire?.trim()?.toLongOrNull() ?: return "Activa"
        if (epochSecs <= 0L) return "Ilimitada"
        return try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(epochSecs * 1000L))
        } catch (_: Exception) {
            "Activa"
        }
    }

    // PASO 5: Mutex por sourceId para evitar múltiples syncs concurrentes de la misma fuente
    private val sourceSyncMutexes = ConcurrentHashMap<String, Mutex>()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preCacheMutex = Mutex()
    private val downloadGate = Semaphore(1)
    private val vodJobMutex = Mutex()
    private val deadHosts = ConcurrentHashMap<String, Long>()
    private val fastPreCacheClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    @Volatile private var foregroundSourceId: String? = null
    private var activeVodJob: Job? = null

    private fun emitUi(emit: Boolean, sourceId: String, state: SyncState) {
        if (emit && (foregroundSourceId == null || foregroundSourceId == sourceId)) {
            _syncState.value = state
        }
    }

    suspend fun awaitActiveVodSync() {
        try {
            activeVodJob?.join()
        } catch (_: Exception) {}
    }

    private suspend fun awaitIdleForPreCache() {
        var polls = 0
        while ((downloadGate.availablePermits == 0 || activeVodJob?.isActive == true) && polls < 240) {
            awaitActiveVodSync()
            if (downloadGate.availablePermits == 0) {
                delay(500L)
            }
            polls++
        }
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url.trim()).host?.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun isHostBlocked(url: String): Boolean {
        val host = hostOf(url) ?: return false
        val failedAt = deadHosts[host] ?: return false
        if (System.currentTimeMillis() - failedAt < HOST_COOLDOWN_MS) return true
        deadHosts.remove(host)
        return false
    }

    private fun markHostDeadIfNetworkDown(url: String, error: Throwable) {
        val isDead = error is ConnectException ||
            error is UnknownHostException ||
            error is NoRouteToHostException ||
            (error is SocketTimeoutException && (error.message?.contains("failed to connect", ignoreCase = true) == true))
        if (isDead) {
            hostOf(url)?.let { host ->
                deadHosts[host] = System.currentTimeMillis()
                android.util.Log.w("XtreamCatalogSync", "Circuit breaker activado (12 min) para host caído: $host")
            }
        }
    }

    private fun markHostAlive(url: String) {
        hostOf(url)?.let { deadHosts.remove(it) }
    }

    private suspend fun <T> retryIo(
        enabled: Boolean,
        times: Int = 3,
        block: suspend () -> T
    ): T {
        if (!enabled) return block()
        var delayMs = 2_000L
        repeat(times - 1) {
            try {
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                val nonRetryable = e is ConnectException ||
                    e is UnknownHostException ||
                    e is NoRouteToHostException ||
                    (e is SocketTimeoutException && (e.message?.contains("failed to connect", ignoreCase = true) == true))
                if (nonRetryable) throw e
                delay(delayMs + Random.nextLong(0L, 500L))
                delayMs *= 2
            }
        }
        return block()
    }

    private fun MessageDigest.toHexString(): String =
        digest().joinToString("") { "%02x".format(it) }

    private fun detectMediaType(mediaType: String?, name: String, group: String, url: String): String {
        val mt = mediaType?.lowercase()?.trim().orEmpty()
        if (mt == "series" || mt == "movie" || mt == "vod") return if (mt == "vod") "movie" else mt

        val normUrl = url.lowercase()
        val normGroup = group.lowercase()
        val normName = name.lowercase()

        val isSeriesUrl = normUrl.contains("/series/") || normUrl.contains("action=get_series") || normUrl.contains("type=series")
        val isSeriesGroup = normGroup.contains("serie") || normGroup.contains("temporada") || normGroup.contains("season") ||
            normGroup.contains("capitulo") || normGroup.contains("episodio") || normGroup.contains("novela") ||
            normGroup.contains("dorama") || normGroup.contains("anime")
        val hasEpisodePattern = Regex("(?i)\\b(s\\d{1,2}|t\\d{1,2}|cap\\.?\\s*\\d+|ep\\.?\\s*\\d+|temporada\\s*\\d+)\\b").containsMatchIn(normName)

        if (isSeriesUrl || isSeriesGroup || hasEpisodePattern) {
            return "series"
        }

        val isMovieUrl = normUrl.contains("/movie/") || normUrl.contains("action=get_vod_streams") || normUrl.contains("type=movie")
        val isMovieGroup = normGroup.contains("película") || normGroup.contains("pelicula") || normGroup.contains("movie") ||
            normGroup.contains("cine") || normGroup.contains("estrenos") || normGroup.contains("vod") || normGroup.contains("4k cinema")
        val hasMovieExtension = (normUrl.endsWith(".mp4") || normUrl.endsWith(".mkv") || normUrl.endsWith(".avi")) && !normUrl.contains(".m3u8")

        if (isMovieUrl || isMovieGroup || hasMovieExtension) {
            return "movie"
        }

        return "live"
    }

    private suspend fun cleanupStaging(syncId: String) {
        try {
            database.catalogStagingDao().deleteStagingChannels(syncId)
            database.catalogStagingDao().deleteStagingMovies(syncId)
            database.catalogStagingDao().deleteStagingSeries(syncId)
            database.catalogStagingDao().deleteStagingCategories(syncId)
        } catch (e: Exception) {
            android.util.Log.w("XtreamCatalogSync", "Error descartando staging para syncId $syncId: ${e.message}")
        }
    }

    private suspend fun performAtomicSwap(syncId: String, sourceId: String) {
        database.withTransaction {
            // PASO 10 (P0 #4): Preservar favoritos del usuario antes del swap
            database.catalogStagingDao().preserveChannelFavorites(syncId, sourceId)
            database.catalogStagingDao().preserveMovieFavorites(syncId, sourceId)
            database.catalogStagingDao().preserveSeriesFavorites(syncId, sourceId)

            // PASO 8 (P0 #4): Eliminar catálogo ACTIVO únicamente para este sourceId
            database.channelDao().deleteChannelsBySource(sourceId)
            database.movieDao().deleteMoviesBySource(sourceId)
            database.seriesDao().deleteSeriesBySource(sourceId)
            database.categoryDao().deleteCategoriesBySource(sourceId)

            // Copiar desde staging del syncId correspondiente a tablas activas
            database.catalogStagingDao().copyChannelsFromStaging(syncId, sourceId)
            database.catalogStagingDao().copyMoviesFromStaging(syncId, sourceId)
            database.catalogStagingDao().copySeriesFromStaging(syncId, sourceId)
            database.catalogStagingDao().copyCategoriesFromStaging(syncId, sourceId)

            // Actualizar contadores reales para que la UI muestre los totales verdaderos (no solo de LIMIT 20)
            database.categoryDao().updateLiveCategoryCounts(sourceId)
            database.categoryDao().updateMovieCategoryCounts(sourceId)
            database.categoryDao().updateSeriesCategoryCounts(sourceId)

            // Eliminar staging consumido
            database.catalogStagingDao().deleteStagingChannels(syncId)
            database.catalogStagingDao().deleteStagingMovies(syncId)
            database.catalogStagingDao().deleteStagingSeries(syncId)
            database.catalogStagingDao().deleteStagingCategories(syncId)
        }
    }

    private suspend fun performLiveAtomicSwap(syncId: String, sourceId: String) {
        database.withTransaction {
            database.catalogStagingDao().preserveChannelFavorites(syncId, sourceId)
            database.channelDao().deleteChannelsBySource(sourceId)
            database.categoryDao().deleteCategoriesBySource(sourceId)

            database.catalogStagingDao().copyChannelsFromStaging(syncId, sourceId)
            database.catalogStagingDao().copyCategoriesFromStaging(syncId, sourceId)

            database.categoryDao().updateLiveCategoryCounts(sourceId)
            database.categoryDao().updateMovieCategoryCounts(sourceId)
            database.categoryDao().updateSeriesCategoryCounts(sourceId)

            database.catalogStagingDao().deleteStagingChannels(syncId)
            database.catalogStagingDao().deleteStagingCategories(syncId)
        }
    }

    private suspend fun performVodAtomicSwap(syncId: String, sourceId: String) {
        database.withTransaction {
            database.catalogStagingDao().preserveMovieFavorites(syncId, sourceId)
            database.catalogStagingDao().preserveSeriesFavorites(syncId, sourceId)

            database.movieDao().deleteMoviesBySource(sourceId)
            database.seriesDao().deleteSeriesBySource(sourceId)

            database.catalogStagingDao().copyMoviesFromStaging(syncId, sourceId)
            database.catalogStagingDao().copySeriesFromStaging(syncId, sourceId)

            database.categoryDao().updateMovieCategoryCounts(sourceId)
            database.categoryDao().updateSeriesCategoryCounts(sourceId)

            database.catalogStagingDao().deleteStagingMovies(syncId)
            database.catalogStagingDao().deleteStagingSeries(syncId)
        }
    }

    /**
     * Sincronización inteligente de arranque (P0 #5).
     * Muestra Room inmediatamente y solo descarga de la red si el catálogo ha cambiado.
     */
    suspend fun syncIfNeeded(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String,
        sourceType: com.lelouch.core.model.SourceType = com.lelouch.core.model.SourceType.XTREAM
    ): Result<Unit> = syncAll(
        sourceId = sourceId,
        serverUrl = serverUrl,
        user = user,
        pass = pass,
        sourceType = sourceType,
        force = false
    )

    suspend fun syncAll(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String,
        sourceType: com.lelouch.core.model.SourceType = com.lelouch.core.model.SourceType.XTREAM,
        force: Boolean = true,
        emitUiState: Boolean = true,
        syncVodInBackground: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (emitUiState) {
            foregroundSourceId = sourceId
            if (force) deadHosts.remove(hostOf(serverUrl) ?: "")
        } else if (isHostBlocked(serverUrl)) {
            return@withContext Result.failure(IOException("Host en cooldown por fallo reciente: ${hostOf(serverUrl)}"))
        }

        // PASO 5: Prevenir dos syncs simultáneos de la misma fuente
        val mutex = sourceSyncMutexes.computeIfAbsent(sourceId) { Mutex() }
        if (!mutex.tryLock()) {
            val busyMsg = "Sincronización ya en curso para la fuente $sourceId. Solicitud omitida."
            android.util.Log.w("XtreamCatalogSync", busyMsg)
            return@withContext Result.failure(IllegalStateException(busyMsg))
        }

        var gateAcquired = false
        var scheduleDeferredTtlRefresh = false
        var scheduleDeferredVodOnly = false
        var existingLiveCountForVod = 0
        var deferredVodForce = false

        try {
            // ── FASE 33: Autodetección de M3U ────────────────────────────────────
            val effectiveType = if (
                sourceType == com.lelouch.core.model.SourceType.XTREAM &&
                !StreamUrlResolver.isXtreamBase(serverUrl, user)
            ) {
                com.lelouch.core.model.SourceType.M3U
            } else {
                sourceType
            }

            // PASO 10: Verificar si Room ya contiene catálogo activo para este sourceId
            val chCount = database.channelDao().getChannelCount(sourceId)
            val movCount = database.movieDao().getMovieCount(sourceId)
            val serCount = database.seriesDao().getSeriesCount(sourceId)
            val totalLocal = chCount + movCount + serCount
            val isRoomEmpty = (totalLocal == 0)

            // Si la lista ya está guardada localmente en Room y NO es una recarga forzada,
            // activar Room de inmediato y verificar TTL real en metadata (24h Live / 3 días VOD).
            if (!force && !isRoomEmpty) {
                val meta = preferencesDataSource?.getSyncMetadata(sourceId)
                val now = System.currentTimeMillis()
                val liveTimestamp = (meta?.lastLiveSyncAt ?: 0L).takeIf { it > 0L } ?: (meta?.lastSuccessfulSyncAt ?: 0L)
                val vodTimestamp = (meta?.lastVodSyncAt ?: 0L).takeIf { it > 0L } ?: (meta?.lastSuccessfulSyncAt ?: 0L)
                val liveFresh = liveTimestamp > 0L && (now - liveTimestamp) < XTREAM_DEFAULT_TTL_MS
                val vodPresent = movCount > 0 || serCount > 0
                val vodFresh = vodPresent && vodTimestamp > 0L && (now - vodTimestamp) < VOD_DEFAULT_TTL_MS

                android.util.Log.i(
                    "XtreamCatalogSync",
                    "CACHE_HIT: Fuente $sourceId ($totalLocal items en Room, liveFresh=$liveFresh, vodFresh=$vodFresh). Activación instantánea."
                )
                emitUi(
                    emitUiState,
                    sourceId,
                    if (liveFresh) {
                        SyncState.TtlFresh(sourceId, "Catálogo al día ($totalLocal items)")
                    } else {
                        SyncState.UpToDate(sourceId, "Catálogo local listo ($totalLocal items)")
                    }
                )

                if (emitUiState && !isHostBlocked(serverUrl)) {
                    if (!liveFresh) {
                        scheduleDeferredTtlRefresh = true
                        deferredVodForce = !vodFresh && effectiveType == com.lelouch.core.model.SourceType.XTREAM
                    } else if (syncVodInBackground && !vodFresh && chCount > 0 && effectiveType == com.lelouch.core.model.SourceType.XTREAM) {
                        scheduleDeferredVodOnly = true
                        existingLiveCountForVod = chCount
                    }
                }
                return@withContext Result.success(Unit)
            }

            // Semáforo global: solo 1 descarga+swap pesada a la vez. Prioridad para fuente activa.
            if (emitUiState) {
                downloadGate.acquire()
                gateAcquired = true
            } else {
                gateAcquired = downloadGate.tryAcquire()
                if (!gateAcquired) {
                    return@withContext Result.failure(IllegalStateException("downloadGate ocupado; descarga diferida."))
                }
            }

            val existingMeta = preferencesDataSource?.getSyncMetadata(sourceId)
            val remoteVersionToSave = existingMeta?.lastSuccessfulVersion.orEmpty()
            val remoteEtagToSave = existingMeta?.lastEtag.orEmpty()

            // PASO 4 (P0 #4): Generar syncId único para aislar completamente esta ejecución en staging
            val syncId = UUID.randomUUID().toString()

            // PASO 13 (P0 #4): Limpiar staging huérfano abandonado por crash previo para este sourceId
            try {
                database.catalogStagingDao().cleanupOrphanChannelsStaging(sourceId, syncId)
                database.catalogStagingDao().cleanupOrphanMoviesStaging(sourceId, syncId)
                database.catalogStagingDao().cleanupOrphanSeriesStaging(sourceId, syncId)
                database.catalogStagingDao().cleanupOrphanCategoriesStaging(sourceId, syncId)
            } catch (e: Exception) {
                android.util.Log.w("XtreamCatalogSync", "Limpieza de huérfanos omitida: ${e.message}")
            }

            // ── Listas M3U (#EXTINF) directas ──────────────────────────────────────
            if (effectiveType == com.lelouch.core.model.SourceType.M3U) {
                return@withContext syncM3uToStaging(sourceId, syncId, serverUrl, remoteVersionToSave, remoteEtagToSave, emitUiState)
            }

            // ── RAMA XTREAM CODES CON STAGING ────────────────────────────────────
            return@withContext syncXtreamToStaging(sourceId, syncId, serverUrl, user, pass, emitUiState, syncVodInBackground)

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            markHostDeadIfNetworkDown(serverUrl, e)
            val errorMsg = e.localizedMessage ?: "Error de sincronización con el servidor IPTV"
            emitUi(emitUiState, sourceId, SyncState.Error(errorMsg))
            if (emitUiState) failDownloadConsole(errorMsg)
            Result.failure(e)
        } finally {
            if (gateAcquired) downloadGate.release()
            mutex.unlock()
            if (scheduleDeferredTtlRefresh) {
                syncScope.launch {
                    delay(1_500L)
                    syncAll(
                        sourceId = sourceId,
                        serverUrl = serverUrl,
                        user = user,
                        pass = pass,
                        sourceType = sourceType,
                        force = true,
                        emitUiState = false,
                        syncVodInBackground = deferredVodForce
                    )
                }
            } else if (scheduleDeferredVodOnly) {
                launchBackgroundVodSync(sourceId, serverUrl, user, pass, existingLiveCountForVod, emitUiState = true)
            }
        }
    }

    /**
     * Pre-descarga y almacena en caché local Room las listas secundarias de forma silenciosa
     * en segundo plano sin emitir errores rojos ni progreso cruzado a la UI activa.
     */
    suspend fun preCacheSources(
        activeSourceId: String?,
        sources: List<SourceConfig>
    ) = withContext(Dispatchers.IO) {
        if (!preCacheMutex.tryLock()) return@withContext
        try {
            awaitIdleForPreCache()
            val pendingSources = sources.filter { it.id != activeSourceId }
            for (source in pendingSources) {
                if (source.id == foregroundSourceId) continue
                if (isHostBlocked(source.serverUrl)) {
                    android.util.Log.d("XtreamCatalogSync", "PRE_CACHE: Host en cooldown omitido (${source.name})")
                    continue
                }
                awaitIdleForPreCache()
                if (source.id == foregroundSourceId) continue
                try {
                    val chCount = database.channelDao().getChannelCount(source.id)
                    if (chCount == 0) {
                        val safeUrl = UrlSanitizer.sanitizeUrl(source.serverUrl)
                        android.util.Log.i("XtreamCatalogSync", "PRE_CACHE: Descargando lista en segundo plano: ${source.name} ($safeUrl)")
                        syncAll(
                            sourceId = source.id,
                            serverUrl = source.serverUrl,
                            user = source.username,
                            pass = source.password,
                            sourceType = source.type,
                            force = false,
                            emitUiState = false,
                            syncVodInBackground = false
                        )
                        delay(750L)
                    } else {
                        android.util.Log.d("XtreamCatalogSync", "PRE_CACHE: ${source.name} ya tiene $chCount canales en Room. Omitiendo.")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w("XtreamCatalogSync", "PRE_CACHE omitido para ${source.name}: ${e.message}")
                }
            }
        } finally {
            preCacheMutex.unlock()
        }
    }

    private fun isLocalFile(serverUrl: String): Boolean {
        return serverUrl.startsWith("file://") || serverUrl.startsWith("/") || serverUrl.contains("content://")
    }

    /**
     * PASO 5: Verificación de frescura para Xtream Directo mediante TTL.
     */
    fun checkXtreamFreshness(
        sourceId: String,
        metadata: SourceSyncMetadata?
    ): FreshnessResult {
        if (metadata == null || metadata.lastSuccessfulSyncAt == 0L) {
            return FreshnessResult.Changed(newVersion = "initial")
        }
        val age = System.currentTimeMillis() - metadata.lastSuccessfulSyncAt
        return if (age < XTREAM_DEFAULT_TTL_MS) {
            FreshnessResult.TtlFresh(lastSyncAt = metadata.lastSuccessfulSyncAt, ttlMs = XTREAM_DEFAULT_TTL_MS)
        } else {
            FreshnessResult.Changed(newVersion = "ttl_expired_${System.currentTimeMillis()}")
        }
    }

    /**
     * PASO 6: Verificación de frescura para M3U Remoto genérico vía HEAD o TTL.
     */
    suspend fun checkGenericM3uFreshness(
        sourceId: String,
        serverUrl: String,
        metadata: SourceSyncMetadata?
    ): FreshnessResult = withContext(Dispatchers.IO) {
        if (metadata == null || metadata.lastSuccessfulSyncAt == 0L) {
            return@withContext FreshnessResult.Changed(newVersion = "initial")
        }
        val age = System.currentTimeMillis() - metadata.lastSuccessfulSyncAt

        try {
            val headReq = Request.Builder()
                .url(serverUrl)
                .head()

            if (metadata.lastEtag.isNotBlank()) {
                val formattedEtag = if (metadata.lastEtag.startsWith("\"")) metadata.lastEtag else "\"${metadata.lastEtag}\""
                headReq.addHeader("If-None-Match", formattedEtag)
            }

            val resp = httpClient.newCall(headReq.build()).execute()
            resp.use { response ->
                when (response.code) {
                    304 -> {
                        // Confirmado sin cambios por el servidor vía 304 Not Modified
                        FreshnessResult.Unchanged(metadata.lastSuccessfulVersion, metadata.lastEtag)
                    }
                    405, 501 -> {
                        // Servidor IPTV no implementa o rechaza HEAD (Method Not Allowed / Not Implemented)
                        // Fallback seguro a TTL: NO considerar que cambió ni forzar descarga innecesaria
                        android.util.Log.w("XtreamCatalogSync", "HEAD no soportado (HTTP ${response.code}) por ${UrlSanitizer.sanitizeUrl(serverUrl)}. Aplicando fallback seguro a TTL.")
                        if (age < M3U_DEFAULT_TTL_MS) {
                            FreshnessResult.TtlFresh(lastSyncAt = metadata.lastSuccessfulSyncAt, ttlMs = M3U_DEFAULT_TTL_MS)
                        } else {
                            FreshnessResult.Changed(newVersion = "ttl_expired_${System.currentTimeMillis()}")
                        }
                    }
                    in 200..299 -> {
                        val newEtag = response.header("ETag")?.trim('"')
                        if (!newEtag.isNullOrBlank()) {
                            val cleanLocalEtag = metadata.lastEtag.trim('"')
                            if (newEtag == cleanLocalEtag) {
                                FreshnessResult.Unchanged(metadata.lastSuccessfulVersion, metadata.lastEtag)
                            } else {
                                FreshnessResult.Changed(newVersion = newEtag, newEtag = newEtag)
                            }
                        } else {
                            // 200 sin ETag ni Last-Modified comprobables -> fallback a TTL
                            if (age < M3U_DEFAULT_TTL_MS) {
                                FreshnessResult.TtlFresh(lastSyncAt = metadata.lastSuccessfulSyncAt, ttlMs = M3U_DEFAULT_TTL_MS)
                            } else {
                                FreshnessResult.Changed(newVersion = "ttl_expired_${System.currentTimeMillis()}")
                            }
                        }
                    }
                    else -> {
                        FreshnessResult.CheckFailed("HTTP ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("XtreamCatalogSync", "Error en HEAD request a ${UrlSanitizer.sanitizeUrl(serverUrl)}: ${e.message}")
            FreshnessResult.CheckFailed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * PASO 7: Verificación de frescura para M3U Local mediante huella de archivo.
     */
    fun checkLocalM3uFreshness(
        sourceId: String,
        serverUrl: String,
        metadata: SourceSyncMetadata?
    ): FreshnessResult {
        if (metadata == null || metadata.lastSuccessfulSyncAt == 0L) {
            return FreshnessResult.Changed(newVersion = "initial")
        }
        try {
            val path = if (serverUrl.startsWith("file://")) serverUrl.removePrefix("file://") else serverUrl
            val file = File(path)
            if (file.exists()) {
                val fileFingerprint = "${file.length()}_${file.lastModified()}"
                return if (fileFingerprint == metadata.lastSuccessfulVersion) {
                    FreshnessResult.Unchanged(version = fileFingerprint)
                } else {
                    FreshnessResult.Changed(newVersion = fileFingerprint)
                }
            }
        } catch (_: Exception) {}
        return FreshnessResult.Changed(newVersion = "unknown")
    }

    private suspend fun syncM3uToStaging(
        sourceId: String,
        syncId: String,
        serverUrl: String,
        remoteVersion: String = "",
        remoteEtag: String = "",
        emitUiState: Boolean = true
    ): Result<Unit> {
        try {
            emitUi(emitUiState, sourceId, SyncState.SyncingLive(0))
            if (emitUiState) appendDownloadConsoleStep(35, "Descargando lista M3U...")

            val channelStagingBatch = mutableListOf<ChannelStagingEntity>()
            val movieStagingBatch = mutableListOf<MovieStagingEntity>()
            val seriesStagingBatch = mutableListOf<SeriesStagingEntity>()
            val liveCategories = mutableSetOf<String>()
            val vodCategories = mutableSetOf<String>()
            val seriesCategories = mutableSetOf<String>()

            val strategyErrors = mutableListOf<String>()

            // ── ESTRATEGIA ÚNICA: M3U / #EXTINF Directo ──
            // FASE 35: la consulta directa a `v_resolved_playlist_items` (hasta 50.000 ítems)
            // se retiró junto con la lista personalizada. Bloqueaba la sincronización de las
            // listas reales y solo servía a la fuente retirada.
            val totalInStaging = database.catalogStagingDao().getStagingChannelCount(syncId) +
                database.catalogStagingDao().getStagingMovieCount(syncId) +
                database.catalogStagingDao().getStagingSeriesCount(syncId)

            var m3uHash = ""
            var gotNotModified304 = false
            var responseEtag = remoteEtag

            if (totalInStaging == 0 && (serverUrl.startsWith("http") || isLocalFile(serverUrl))) {
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    suspend fun consumeM3uStream(rawStream: java.io.InputStream) {
                        digest.reset()
                        channelStagingBatch.clear()
                        movieStagingBatch.clear()
                        seriesStagingBatch.clear()
                        val digestStream = DigestInputStream(rawStream, digest)
                        val bufferedReader = java.io.BufferedReader(java.io.InputStreamReader(digestStream, Charsets.UTF_8), 65536)
                        var count = 0
                        var currentName = "Canal"
                        var currentGroup = "General"
                        var currentLogo: String? = null
                        var currentMediaType: String? = null

                        bufferedReader.useLines { lines ->
                            for (line in lines) {
                                val trimmed = line.trim()
                                if (trimmed.isEmpty()) continue

                                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                                    currentGroup = extractFastAttribute(trimmed, "group-title") ?: "General"
                                    currentLogo = extractFastAttribute(trimmed, "tvg-logo")
                                    currentMediaType = extractFastAttribute(trimmed, "tvg-type") ?: extractFastAttribute(trimmed, "media-type")
                                    val commaIdx = trimmed.lastIndexOf(',')
                                    currentName = if (commaIdx in 0 until trimmed.length - 1) {
                                        trimmed.substring(commaIdx + 1).trim().ifEmpty { "Canal" }
                                    } else {
                                        "Canal"
                                    }
                                } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("rtmp://", ignoreCase = true)) {
                                    val streamId = (currentName + trimmed).hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
                                    val resolvedType = detectMediaType(currentMediaType, currentName, currentGroup, trimmed)

                                    if (resolvedType == "movie") {
                                        vodCategories.add(currentGroup)
                                        movieStagingBatch.add(
                                            MovieStagingEntity(
                                                syncId = syncId,
                                                id = "$sourceId-$count",
                                                streamId = streamId,
                                                num = count,
                                                name = currentName,
                                                title = currentName,
                                                streamIcon = currentLogo,
                                                categoryId = "$sourceId-${currentGroup.hashCode()}",
                                                categoryName = currentGroup,
                                                containerExtension = "mp4",
                                                streamUrl = trimmed,
                                                sourceId = sourceId
                                            )
                                        )
                                    } else if (resolvedType == "series") {
                                        seriesCategories.add(currentGroup)
                                        seriesStagingBatch.add(
                                            SeriesStagingEntity(
                                                syncId = syncId,
                                                id = "$sourceId-$count",
                                                seriesId = streamId,
                                                num = count,
                                                name = currentName,
                                                title = currentName,
                                                cover = currentLogo,
                                                categoryId = "$sourceId-${currentGroup.hashCode()}",
                                                categoryName = currentGroup,
                                                sourceId = sourceId
                                            )
                                        )
                                    } else {
                                        liveCategories.add(currentGroup)
                                        channelStagingBatch.add(
                                            ChannelStagingEntity(
                                                syncId = syncId,
                                                id = "$sourceId-$count",
                                                streamId = streamId,
                                                num = count,
                                                name = currentName,
                                                streamType = "live",
                                                streamIcon = currentLogo,
                                                categoryId = "$sourceId-${currentGroup.hashCode()}",
                                                categoryName = currentGroup,
                                                streamUrl = trimmed,
                                                containerExtension = "m3u8",
                                                sourceId = sourceId
                                            )
                                        )
                                    }
                                    count++

                                    if (channelStagingBatch.size >= 2500) {
                                        database.catalogStagingDao().insertChannelsStaging(channelStagingBatch)
                                        channelStagingBatch.clear()
                                        emitUi(emitUiState, sourceId, SyncState.SyncingLive(count))
                                    }
                                    if (movieStagingBatch.size >= 2500) {
                                        database.catalogStagingDao().insertMoviesStaging(movieStagingBatch)
                                        movieStagingBatch.clear()
                                    }
                                    if (seriesStagingBatch.size >= 2500) {
                                        database.catalogStagingDao().insertSeriesStaging(seriesStagingBatch)
                                        seriesStagingBatch.clear()
                                    }
                                }
                            }
                        }

                        if (channelStagingBatch.isNotEmpty()) {
                            database.catalogStagingDao().insertChannelsStaging(channelStagingBatch)
                            channelStagingBatch.clear()
                        }
                        if (movieStagingBatch.isNotEmpty()) {
                            database.catalogStagingDao().insertMoviesStaging(movieStagingBatch)
                            movieStagingBatch.clear()
                        }
                        if (seriesStagingBatch.isNotEmpty()) {
                            database.catalogStagingDao().insertSeriesStaging(seriesStagingBatch)
                            seriesStagingBatch.clear()
                        }
                        m3uHash = digest.toHexString()
                    }

                    if (isLocalFile(serverUrl)) {
                        val cleanPath = serverUrl.removePrefix("file://")
                        val file = java.io.File(cleanPath)
                        if (file.exists()) {
                            file.inputStream().use { consumeM3uStream(it) }
                        }
                    } else {
                        val client = if (emitUiState) httpClient else fastPreCacheClient
                        retryIo(enabled = emitUiState) {
                            val reqBuilder = Request.Builder()
                                .url(serverUrl)
                                .header("User-Agent", "IPTVSmartersPlayer")
                            if (remoteEtag.isNotBlank()) {
                                val formattedEtag = if (remoteEtag.startsWith("\"")) remoteEtag else "\"$remoteEtag\""
                                reqBuilder.header("If-None-Match", formattedEtag)
                            }
                            client.newCall(reqBuilder.build()).execute().use { res ->
                                if (res.code == 304) {
                                    gotNotModified304 = true
                                    return@use
                                }
                                if (res.code == 429 || res.code in 500..599) {
                                    throw TransientHttpException(res.code, "HTTP ${res.code}")
                                }
                                if (!res.isSuccessful) {
                                    throw IOException("HTTP ${res.code}")
                                }
                                markHostAlive(serverUrl)
                                res.header("ETag")?.trim('"')?.takeIf { it.isNotBlank() }?.let { responseEtag = it }
                                res.body?.byteStream()?.use { consumeM3uStream(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    markHostDeadIfNetworkDown(serverUrl, e)
                    strategyErrors.add("M3U directo: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            if (gotNotModified304) {
                cleanupStaging(syncId)
                val prevMeta = preferencesDataSource?.getSyncMetadata(sourceId)
                val now = System.currentTimeMillis()
                preferencesDataSource?.saveSyncMetadata(
                    (prevMeta ?: SourceSyncMetadata(sourceId = sourceId)).copy(
                        lastSuccessfulSyncAt = now,
                        lastLiveSyncAt = now,
                        lastVodSyncAt = now,
                        lastSyncResult = "SUCCESS"
                    )
                )
                emitUi(emitUiState, sourceId, SyncState.UpToDate(sourceId, "Catálogo al día (304 Not Modified)"))
                return Result.success(Unit)
            }

            // Insertar categorías en staging
            val allStagingCategories = mutableListOf<CategoryStagingEntity>()
            allStagingCategories.addAll(liveCategories.map { grp ->
                CategoryStagingEntity(
                    syncId = syncId,
                    id = "$sourceId-LIVE-${grp.hashCode()}",
                    categoryId = "$sourceId-${grp.hashCode()}",
                    categoryName = grp,
                    type = "LIVE",
                    isAdult = com.lelouch.core.model.AdultContentFilter.isAdult(grp, grp),
                    sourceId = sourceId
                )
            })
            allStagingCategories.addAll(vodCategories.map { grp ->
                CategoryStagingEntity(
                    syncId = syncId,
                    id = "$sourceId-VOD-${grp.hashCode()}",
                    categoryId = "$sourceId-${grp.hashCode()}",
                    categoryName = grp,
                    type = "VOD",
                    isAdult = com.lelouch.core.model.AdultContentFilter.isAdult(grp, grp),
                    sourceId = sourceId
                )
            })
            allStagingCategories.addAll(seriesCategories.map { grp ->
                CategoryStagingEntity(
                    syncId = syncId,
                    id = "$sourceId-SERIES-${grp.hashCode()}",
                    categoryId = "$sourceId-${grp.hashCode()}",
                    categoryName = grp,
                    type = "SERIES",
                    isAdult = com.lelouch.core.model.AdultContentFilter.isAdult(grp, grp),
                    sourceId = sourceId
                )
            })
            if (allStagingCategories.isNotEmpty()) {
                database.catalogStagingDao().insertCategoriesStaging(allStagingCategories)
            }

            // PASO 7 (P0 #4): Validar staging antes del swap
            val stagingChannels = database.catalogStagingDao().getStagingChannelCount(syncId)
            val stagingMovies = database.catalogStagingDao().getStagingMovieCount(syncId)
            val stagingSeries = database.catalogStagingDao().getStagingSeriesCount(syncId)
            val stagingCategoriesCount = database.catalogStagingDao().getStagingCategoryCount(syncId)
            val totalNew = stagingChannels + stagingMovies + stagingSeries

            val oldChannels = database.channelDao().getChannelCount(sourceId)
            val oldMovies = database.movieDao().getMovieCount(sourceId)
            val oldSeries = database.seriesDao().getSeriesCount(sourceId)
            val totalOld = oldChannels + oldMovies + oldSeries

            android.util.Log.i(
                "XtreamCatalogSync",
                "Validación Staging M3U [$sourceId] (syncId: $syncId): " +
                    "OLD: [channels=$oldChannels, movies=$oldMovies, series=$oldSeries, total=$totalOld] -> " +
                    "STAGING: [channels=$stagingChannels, movies=$stagingMovies, series=$stagingSeries, categories=$stagingCategoriesCount, total=$totalNew]"
            )

            if (totalNew == 0) {
                cleanupStaging(syncId)
                val detail = if (strategyErrors.isEmpty()) {
                    "el proveedor no devolvió ningún elemento para esta lista"
                } else {
                    strategyErrors.joinToString(" | ")
                }
                val errorMsg = "No se pudo cargar el catálogo M3U: $detail (Catálogo activo preservado)"
                android.util.Log.e("XtreamCatalogSync", errorMsg)
                emitUi(emitUiState, sourceId, SyncState.Error(errorMsg))
                if (emitUiState) failDownloadConsole(errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            if (emitUiState) appendDownloadConsoleStep(85, "Guardando catálogo en el dispositivo ($totalNew elementos)...")

            val prevMeta = preferencesDataSource?.getSyncMetadata(sourceId)
            val now = System.currentTimeMillis()
            val hashUnchanged = m3uHash.isNotEmpty() && m3uHash == prevMeta?.lastLiveHash && totalOld > 0

            if (hashUnchanged) {
                android.util.Log.i("XtreamCatalogSync", "HASH_MATCH M3U [$sourceId]: contenido idéntico; omitiendo swap Room ($totalOld items preservados).")
                cleanupStaging(syncId)
                preferencesDataSource?.saveSyncMetadata(
                    (prevMeta ?: SourceSyncMetadata(sourceId = sourceId)).copy(
                        lastSuccessfulSyncAt = now,
                        lastLiveSyncAt = now,
                        lastVodSyncAt = now,
                        lastEtag = responseEtag,
                        lastSyncResult = "SUCCESS"
                    )
                )
            } else {
                // PASO 8 (P0 #4): Swap atómico corto en base de datos Room
                performAtomicSwap(syncId, sourceId)

                // PASO 3 (P0 #5): Guardar metadata de sincronización exitosa SOLO tras swap exitoso
                val versionToSave = remoteVersion.ifBlank { now.toString() }
                preferencesDataSource?.saveSyncMetadata(
                    SourceSyncMetadata(
                        sourceId = sourceId,
                        lastSuccessfulSyncAt = now,
                        lastLiveSyncAt = now,
                        lastVodSyncAt = now,
                        lastSuccessfulVersion = versionToSave,
                        lastEtag = responseEtag,
                        lastLiveHash = m3uHash,
                        lastVodHash = m3uHash,
                        lastSyncResult = "SUCCESS",
                        channelCount = stagingChannels,
                        movieCount = stagingMovies,
                        seriesCount = stagingSeries
                    )
                )
            }

            emitUi(
                emitUiState,
                sourceId,
                SyncState.Completed(
                    channelsCount = stagingChannels,
                    moviesCount = stagingMovies,
                    seriesCount = stagingSeries
                )
            )
            if (emitUiState) {
                val totalSaved = stagingChannels + stagingMovies + stagingSeries
                completeDownloadConsole("✓ Catálogo guardado ($totalSaved elementos)")
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            cleanupStaging(syncId)
            if (e is kotlinx.coroutines.CancellationException) {
                emitUi(emitUiState, sourceId, SyncState.Idle)
                throw e
            }
            val errorMsg = "Sincronización M3U interrumpida: ${e.message ?: e.javaClass.simpleName}"
            android.util.Log.e("XtreamCatalogSync", errorMsg, e)
            emitUi(emitUiState, sourceId, SyncState.Error(errorMsg))
            if (emitUiState) failDownloadConsole(errorMsg)
            return Result.failure(e)
        }
    }

    private suspend fun syncXtreamToStaging(
        sourceId: String,
        syncId: String,
        serverUrl: String,
        user: String,
        pass: String,
        emitUiState: Boolean = true,
        syncVodInBackground: Boolean = true
    ): Result<Unit> {
        try {
            val client = if (emitUiState) httpClient else fastPreCacheClient
            emitUi(emitUiState, sourceId, SyncState.Authenticating)
            if (emitUiState) appendDownloadConsoleStep(20, "Obteniendo información de cuenta...")
            val api = NetworkClient.createXtreamApiService(serverUrl, client)

            // 1. Validar credenciales y cuenta (con retryIo solo para fuente activa)
            val auth = retryIo(enabled = emitUiState) { api.authenticate(user, pass) }
            markHostAlive(serverUrl)
            val userInfo = auth.userInfo ?: throw IllegalStateException("Respuesta de autenticación vacía del servidor IPTV")

            if (!userInfo.status.trim().equals("Active", ignoreCase = true)) {
                val errorMsg = "La cuenta IPTV no está activa (Estado: ${userInfo.status})"
                emitUi(emitUiState, sourceId, SyncState.Error(errorMsg))
                if (emitUiState) failDownloadConsole(errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            if (emitUiState) {
                val expLabel = formatConsoleExpireDate(userInfo.expDate)
                appendDownloadConsoleStep(25, "✓ Cuenta activa — vence: $expLabel")
            }

            // FASE 35 (1.0.97): persistir los datos reales de la cuenta en cada sincronización
            // (incluido «🔄 Recargar»). Antes solo se guardaban al validar por primera vez.
            try {
                preferencesDataSource?.updateSourceAccountInfo(
                    sourceId = sourceId,
                    activeConnections = userInfo.activeCons.toIntOrNull() ?: 0,
                    maxConnections = userInfo.maxConnections.toIntOrNull() ?: 1,
                    expireDate = userInfo.expDate,
                    isTrial = userInfo.isTrial == "1"
                )
            } catch (e: Exception) {
                android.util.Log.w("XtreamCatalogSync", "No se pudo actualizar la info de cuenta: ${e.message}")
            }

            // 2. Sincronizar Categorías en Staging (Live, VOD, Series)
            emitUi(emitUiState, sourceId, SyncState.SyncingCategories("Descargando categorías..."))
            if (emitUiState) appendDownloadConsoleStep(30, "Obteniendo categorías...")
            val liveCategories = try { api.getLiveCategories(user, pass) } catch (_: Exception) { emptyList() }
            val vodCategories = try { api.getVodCategories(user, pass) } catch (_: Exception) { emptyList() }
            val seriesCategories = try { api.getSeriesCategories(user, pass) } catch (_: Exception) { emptyList() }

            val categoryStagingEntities = mutableListOf<CategoryStagingEntity>()
            liveCategories.forEach {
                categoryStagingEntities.add(
                    CategoryStagingEntity(
                        syncId = syncId,
                        id = "$sourceId-LIVE-${it.categoryId}",
                        categoryId = it.categoryId,
                        categoryName = it.categoryName,
                        parentId = it.parentId,
                        type = "LIVE",
                        isAdult = com.lelouch.core.model.AdultContentFilter.isAdult(it.categoryName, it.categoryName, it.categoryId),
                        sourceId = sourceId
                    )
                )
            }
            vodCategories.forEach {
                categoryStagingEntities.add(
                    CategoryStagingEntity(
                        syncId = syncId,
                        id = "$sourceId-VOD-${it.categoryId}",
                        categoryId = it.categoryId,
                        categoryName = it.categoryName,
                        parentId = it.parentId,
                        type = "VOD",
                        isAdult = com.lelouch.core.model.AdultContentFilter.isAdult(it.categoryName, it.categoryName, it.categoryId),
                        sourceId = sourceId
                    )
                )
            }
            seriesCategories.forEach {
                categoryStagingEntities.add(
                    CategoryStagingEntity(
                        syncId = syncId,
                        id = "$sourceId-SERIES-${it.categoryId}",
                        categoryId = it.categoryId,
                        categoryName = it.categoryName,
                        parentId = it.parentId,
                        type = "SERIES",
                        isAdult = com.lelouch.core.model.AdultContentFilter.isAdult(it.categoryName, it.categoryName, it.categoryId),
                        sourceId = sourceId
                    )
                )
            }
            if (categoryStagingEntities.isNotEmpty()) {
                database.catalogStagingDao().insertCategoriesStaging(categoryStagingEntities)
            }

            // 3. Sincronizar Canales en Vivo hacia Staging (reutilizando pool de httpClient + hash SHA-256)
            val liveCatNameMap = liveCategories.associate { it.categoryId to it.categoryName }
            emitUi(emitUiState, sourceId, SyncState.SyncingLive(0))
            if (emitUiState) appendDownloadConsoleStep(45, "Descargando canales en vivo...")

            val liveUrl = "${if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"}player_api.php?username=$user&password=$pass&action=get_live_streams"
            var liveCount = 0
            var liveHash = ""
            retryIo(enabled = emitUiState) {
                val liveDigest = MessageDigest.getInstance("SHA-256")
                liveCount = 0
                val liveRequest = Request.Builder().url(liveUrl).build()
                client.newCall(liveRequest).execute().use { response ->
                    if (response.code == 429 || response.code in 500..599) {
                        throw TransientHttpException(response.code, "HTTP ${response.code}")
                    }
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body?.byteStream()?.let { rawStream ->
                        val digestStream = DigestInputStream(rawStream, liveDigest)
                        XtreamStreamingParser.parseLiveStreams(
                            inputStream = digestStream,
                            sourceId = sourceId,
                            batchSize = 500,
                            onBatchParsed = { batch ->
                                val stagingBatch = batch.map { ch ->
                                    val resolvedCat = ch.categoryName.ifBlank { liveCatNameMap[ch.categoryId].orEmpty() }
                                    ch.copy(categoryName = resolvedCat).toStaging(syncId)
                                }
                                database.catalogStagingDao().insertChannelsStaging(stagingBatch)
                                liveCount += batch.size
                                emitUi(emitUiState, sourceId, SyncState.SyncingLive(liveCount))
                            }
                        )
                    }
                }
                liveHash = liveDigest.toHexString()
            }

            // ── PASO CLAVE MREA (Arranque Instantáneo TV Box y Móvil): COMMIT INMEDIATO DE CANALES Y CATEGORÍAS ──
            val stagingChannels = database.catalogStagingDao().getStagingChannelCount(syncId)
            val stagingCategoriesCount = database.catalogStagingDao().getStagingCategoryCount(syncId)

            if (stagingChannels == 0 && stagingCategoriesCount == 0) {
                cleanupStaging(syncId)
                val errorMsg = "El servidor Xtream devolvió 0 canales en vivo. Se preserva el catálogo activo."
                android.util.Log.w("XtreamCatalogSync", errorMsg)
                emitUi(emitUiState, sourceId, SyncState.Error(errorMsg))
                if (emitUiState) failDownloadConsole(errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            if (emitUiState) appendDownloadConsoleStep(80, "Guardando canales en el dispositivo ($stagingChannels canales)...")

            val now = System.currentTimeMillis()
            val prevMeta = preferencesDataSource?.getSyncMetadata(sourceId)
            val oldChannels = database.channelDao().getChannelCount(sourceId)
            val liveUnchanged = liveHash.isNotEmpty() && liveHash == prevMeta?.lastLiveHash && oldChannels > 0

            if (liveUnchanged) {
                android.util.Log.i("XtreamCatalogSync", "HASH_MATCH Live [$sourceId]: canales sin cambios; omitiendo swap ($oldChannels preservados).")
                cleanupStaging(syncId)
                preferencesDataSource?.saveSyncMetadata(
                    (prevMeta ?: SourceSyncMetadata(sourceId = sourceId)).copy(
                        lastSuccessfulSyncAt = now,
                        lastLiveSyncAt = now,
                        lastSyncResult = "SUCCESS",
                        channelCount = oldChannels
                    )
                )
            } else {
                // Realizar swap atómico INMEDIATO de canales en vivo y categorías (<3 segundos)
                performLiveAtomicSwap(syncId, sourceId)

                // Guardar metadata de Live preservando TTL/hash previo de VOD/Series
                preferencesDataSource?.saveSyncMetadata(
                    SourceSyncMetadata(
                        sourceId = sourceId,
                        lastSuccessfulSyncAt = now,
                        lastLiveSyncAt = now,
                        lastVodSyncAt = prevMeta?.lastVodSyncAt ?: 0L,
                        lastSuccessfulVersion = now.toString(),
                        lastEtag = prevMeta?.lastEtag.orEmpty(),
                        lastLiveHash = liveHash,
                        lastVodHash = prevMeta?.lastVodHash.orEmpty(),
                        lastSyncResult = "SUCCESS",
                        channelCount = stagingChannels,
                        movieCount = prevMeta?.movieCount ?: 0,
                        seriesCount = prevMeta?.seriesCount ?: 0
                    )
                )
            }

            val effectiveLiveCount = if (liveUnchanged) oldChannels else stagingChannels
            // Notificar estado completado a la UI para desbloquear la pantalla roja de inmediato
            emitUi(
                emitUiState,
                sourceId,
                SyncState.Completed(
                    channelsCount = effectiveLiveCount,
                    moviesCount = prevMeta?.movieCount ?: 0,
                    seriesCount = prevMeta?.seriesCount ?: 0
                )
            )
            if (emitUiState) {
                completeDownloadConsole("✓ Catálogo listo ($effectiveLiveCount canales en vivo)")
            }

            // ── Sincronizar Películas VOD y Series en segundo plano controlado (TTL 3 días) ──
            if (syncVodInBackground) {
                val vodAge = now - (prevMeta?.lastVodSyncAt ?: 0L)
                val existingMovies = database.movieDao().getMovieCount(sourceId)
                val existingSeries = database.seriesDao().getSeriesCount(sourceId)
                val vodFresh = (existingMovies + existingSeries > 0) && (prevMeta?.lastVodSyncAt ?: 0L) > 0L && vodAge < VOD_DEFAULT_TTL_MS
                if (!vodFresh) {
                    launchBackgroundVodSync(sourceId, serverUrl, user, pass, effectiveLiveCount, emitUiState)
                } else {
                    android.util.Log.d("XtreamCatalogSync", "VOD_TTL_FRESH [$sourceId]: VOD/Series dentro de TTL 3d; omitiendo re-descarga.")
                }
            }

            return Result.success(Unit)

        } catch (e: Exception) {
            cleanupStaging(syncId)
            if (e is kotlinx.coroutines.CancellationException) {
                emitUi(emitUiState, sourceId, SyncState.Idle)
                throw e
            }
            markHostDeadIfNetworkDown(serverUrl, e)
            val errorMsg = e.localizedMessage ?: "Error de sincronización con el servidor IPTV"
            emitUi(emitUiState, sourceId, SyncState.Error(errorMsg))
            if (emitUiState) failDownloadConsole(errorMsg)
            return Result.failure(e)
        }
    }

    private suspend fun launchBackgroundVodSync(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String,
        channelsCount: Int,
        emitUiState: Boolean
    ) {
        vodJobMutex.lock()
        try {
            activeVodJob?.cancelAndJoin()
            activeVodJob = syncScope.launch {
                var gateAcquired = false
                val vodSyncId = UUID.randomUUID().toString()
                try {
                    downloadGate.acquire()
                    gateAcquired = true

                    // 4. Sincronizar Películas VOD hacia Staging (reutilizando httpClient + hash combinado)
                    val vodCatNameMap = runCatching {
                        database.categoryDao().getCategoriesByType("VOD", sourceId).first()
                            .associate { it.categoryId to it.categoryName }
                    }.getOrDefault(emptyMap())
                    val seriesCatNameMap = runCatching {
                        database.categoryDao().getCategoriesByType("SERIES", sourceId).first()
                            .associate { it.categoryId to it.categoryName }
                    }.getOrDefault(emptyMap())

                    emitUi(emitUiState, sourceId, SyncState.SyncingMovies(0))
                    val vodUrl = "${if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"}player_api.php?username=$user&password=$pass&action=get_vod_streams"
                    var movieCount = 0
                    var vodPartHash = ""
                    retryIo(enabled = emitUiState) {
                        val vodDigest = MessageDigest.getInstance("SHA-256")
                        movieCount = 0
                        val vodRequest = Request.Builder().url(vodUrl).build()
                        httpClient.newCall(vodRequest).execute().use { response ->
                            if (response.code == 429 || response.code in 500..599) {
                                throw TransientHttpException(response.code, "HTTP ${response.code}")
                            }
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            response.body?.byteStream()?.let { rawStream ->
                                val digestStream = DigestInputStream(rawStream, vodDigest)
                                XtreamStreamingParser.parseVodStreams(
                                    inputStream = digestStream,
                                    sourceId = sourceId,
                                    batchSize = 500,
                                    onBatchParsed = { batch ->
                                        val stagingBatch = batch.map { mov ->
                                            val resolvedCat = mov.categoryName.ifBlank { vodCatNameMap[mov.categoryId].orEmpty() }
                                            mov.copy(categoryName = resolvedCat).toStaging(vodSyncId)
                                        }
                                        database.catalogStagingDao().insertMoviesStaging(stagingBatch)
                                        movieCount += batch.size
                                        emitUi(emitUiState, sourceId, SyncState.SyncingMovies(movieCount))
                                    }
                                )
                            }
                        }
                        vodPartHash = vodDigest.toHexString()
                    }

                    // 5. Sincronizar Series hacia Staging
                    emitUi(emitUiState, sourceId, SyncState.SyncingSeries(0))
                    val seriesUrl = "${if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"}player_api.php?username=$user&password=$pass&action=get_series"
                    var seriesCount = 0
                    var seriesPartHash = ""
                    retryIo(enabled = emitUiState) {
                        val seriesDigest = MessageDigest.getInstance("SHA-256")
                        seriesCount = 0
                        val seriesRequest = Request.Builder().url(seriesUrl).build()
                        httpClient.newCall(seriesRequest).execute().use { response ->
                            if (response.code == 429 || response.code in 500..599) {
                                throw TransientHttpException(response.code, "HTTP ${response.code}")
                            }
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            response.body?.byteStream()?.let { rawStream ->
                                val digestStream = DigestInputStream(rawStream, seriesDigest)
                                XtreamStreamingParser.parseSeriesStreams(
                                    inputStream = digestStream,
                                    sourceId = sourceId,
                                    batchSize = 500,
                                    onBatchParsed = { batch ->
                                        val stagingBatch = batch.map { ser ->
                                            val resolvedCat = ser.categoryName.ifBlank { seriesCatNameMap[ser.categoryId].orEmpty() }
                                            ser.copy(categoryName = resolvedCat).toStaging(vodSyncId)
                                        }
                                        database.catalogStagingDao().insertSeriesStaging(stagingBatch)
                                        seriesCount += batch.size
                                        emitUi(emitUiState, sourceId, SyncState.SyncingSeries(seriesCount))
                                    }
                                )
                            }
                        }
                        seriesPartHash = seriesDigest.toHexString()
                    }

                    val vodHash = "$vodPartHash:$seriesPartHash"
                    val stagingMovies = database.catalogStagingDao().getStagingMovieCount(vodSyncId)
                    val stagingSeries = database.catalogStagingDao().getStagingSeriesCount(vodSyncId)
                    val oldMovies = database.movieDao().getMovieCount(sourceId)
                    val oldSeries = database.seriesDao().getSeriesCount(sourceId)
                    val prevMeta = preferencesDataSource?.getSyncMetadata(sourceId)
                    val now = System.currentTimeMillis()

                    if (vodHash.isNotEmpty() && vodHash == prevMeta?.lastVodHash && (oldMovies + oldSeries) > 0) {
                        android.util.Log.i("XtreamCatalogSync", "HASH_MATCH VOD/Series [$sourceId]: contenido idéntico; omitiendo swap masivo ($oldMovies pelis, $oldSeries series).")
                        cleanupStaging(vodSyncId)
                        database.categoryDao().updateMovieCategoryCounts(sourceId)
                        database.categoryDao().updateSeriesCategoryCounts(sourceId)
                        preferencesDataSource?.saveSyncMetadata(
                            (prevMeta ?: SourceSyncMetadata(sourceId = sourceId)).copy(
                                lastVodSyncAt = now,
                                lastSyncResult = "SUCCESS"
                            )
                        )
                    } else if (stagingMovies > 0 || stagingSeries > 0) {
                        performVodAtomicSwap(vodSyncId, sourceId)
                        preferencesDataSource?.saveSyncMetadata(
                            SourceSyncMetadata(
                                sourceId = sourceId,
                                lastSuccessfulSyncAt = prevMeta?.lastSuccessfulSyncAt?.takeIf { it > 0L } ?: now,
                                lastLiveSyncAt = prevMeta?.lastLiveSyncAt?.takeIf { it > 0L } ?: now,
                                lastVodSyncAt = now,
                                lastSuccessfulVersion = prevMeta?.lastSuccessfulVersion.orEmpty().ifBlank { now.toString() },
                                lastEtag = prevMeta?.lastEtag.orEmpty(),
                                lastLiveHash = prevMeta?.lastLiveHash.orEmpty(),
                                lastVodHash = vodHash,
                                lastSyncResult = "SUCCESS",
                                channelCount = channelsCount,
                                movieCount = stagingMovies,
                                seriesCount = stagingSeries
                            )
                        )
                    } else {
                        cleanupStaging(vodSyncId)
                    }

                    emitUi(
                        emitUiState,
                        sourceId,
                        SyncState.Completed(
                            channelsCount = channelsCount,
                            moviesCount = if (stagingMovies > 0) stagingMovies else oldMovies,
                            seriesCount = if (stagingSeries > 0) stagingSeries else oldSeries
                        )
                    )
                    android.util.Log.i("XtreamCatalogSync", "Sync VOD/Series completado en background [$sourceId]: movies=$stagingMovies, series=$stagingSeries")
                } catch (e: Exception) {
                    cleanupStaging(vodSyncId)
                    markHostDeadIfNetworkDown(serverUrl, e)
                    android.util.Log.w("XtreamCatalogSync", "Sync VOD/Series en background omitido o falló: ${e.message}")
                } finally {
                    if (gateAcquired) downloadGate.release()
                }
            }
        } finally {
            vodJobMutex.unlock()
        }
    }

    private fun ChannelEntity.toStaging(syncId: String): ChannelStagingEntity = ChannelStagingEntity(
        syncId = syncId,
        id = id,
        streamId = streamId,
        num = num,
        name = name,
        streamType = streamType,
        streamIcon = streamIcon,
        categoryId = categoryId,
        categoryName = categoryName,
        epgChannelId = epgChannelId,
        isAdult = isAdult,
        isFavorite = isFavorite,
        streamUrl = streamUrl,
        containerExtension = containerExtension,
        sourceId = sourceId
    )

    private fun MovieEntity.toStaging(syncId: String): MovieStagingEntity = MovieStagingEntity(
        syncId = syncId,
        id = id,
        streamId = streamId,
        num = num,
        name = name,
        title = title,
        year = year,
        streamIcon = streamIcon,
        backdropPath = backdropPath,
        rating = rating,
        rating5based = rating5based,
        added = added,
        categoryId = categoryId,
        categoryName = categoryName,
        containerExtension = containerExtension,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        durationSecs = durationSecs,
        streamUrl = streamUrl,
        isFavorite = isFavorite,
        sourceId = sourceId
    )

    private fun SeriesEntity.toStaging(syncId: String): SeriesStagingEntity = SeriesStagingEntity(
        syncId = syncId,
        id = id,
        seriesId = seriesId,
        num = num,
        name = name,
        title = title,
        cover = cover,
        backdropPath = backdropPath,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        rating = rating,
        rating5based = rating5based,
        categoryId = categoryId,
        categoryName = categoryName,
        isFavorite = isFavorite,
        sourceId = sourceId
    )

    private fun extractFastAttribute(line: String, key: String): String? {
        val keyDq = "$key=\""
        var idx = line.indexOf(keyDq, ignoreCase = true)
        if (idx >= 0) {
            val start = idx + keyDq.length
            val end = line.indexOf('"', start)
            if (end > start) return line.substring(start, end).trim()
        }
        val keySq = "$key='"
        idx = line.indexOf(keySq, ignoreCase = true)
        if (idx >= 0) {
            val start = idx + keySq.length
            val end = line.indexOf('\'', start)
            if (end > start) return line.substring(start, end).trim()
        }
        return null
    }

    /**
     * FASE 35 — La lista personalizada fue retirada; no se suben canales a Supabase.
     */
    suspend fun extractChannelsToCustomPlaylist(
        sourceId: String,
        categoryNames: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        0
    }
}
