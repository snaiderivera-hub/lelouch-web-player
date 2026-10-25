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
import com.lelouch.core.network.NetworkClient
import com.lelouch.core.network.StreamUrlResolver
import com.lelouch.core.network.XtreamStreamingParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
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
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
        const val XTREAM_DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L // 24 horas para Xtream
        const val M3U_DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L // 24 horas para M3U genérico
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // PASO 5: Mutex por sourceId para evitar múltiples syncs concurrentes de la misma fuente
    private val sourceSyncMutexes = ConcurrentHashMap<String, Mutex>()

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
        force: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // PASO 5: Prevenir dos syncs simultáneos de la misma fuente
        val mutex = sourceSyncMutexes.computeIfAbsent(sourceId) { Mutex() }
        if (!mutex.tryLock()) {
            val busyMsg = "Sincronización ya en curso para la fuente $sourceId. Solicitud omitida."
            android.util.Log.w("XtreamCatalogSync", busyMsg)
            return@withContext Result.failure(IllegalStateException(busyMsg))
        }

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

            // PASO 2, 5, 6: Si no es forzado y Room tiene datos, comprobar frescura
            var remoteVersionToSave = ""
            var remoteEtagToSave = ""

            if (!force && !isRoomEmpty) {
                _syncState.value = SyncState.Checking(sourceId)
                val metadata = preferencesDataSource?.getSyncMetadata(sourceId)

                val freshness = when {
                    // Tipo B: Xtream Directo
                    effectiveType == com.lelouch.core.model.SourceType.XTREAM -> {
                        checkXtreamFreshness(sourceId, metadata)
                    }
                    // Tipo D: M3U Local
                    effectiveType == com.lelouch.core.model.SourceType.M3U && isLocalFile(serverUrl) -> {
                        checkLocalM3uFreshness(sourceId, serverUrl, metadata)
                    }
                    // Tipo C: M3U Remoto Genérico
                    else -> {
                        checkGenericM3uFreshness(sourceId, serverUrl, metadata)
                    }
                }

                when (freshness) {
                    is FreshnessResult.Unchanged -> {
                        android.util.Log.i("XtreamCatalogSync", "SYNC_SKIPPED_UNCHANGED: source $sourceId al día comprobado (versión=${freshness.version})")
                        _syncState.value = SyncState.UpToDate(sourceId, "Catálogo al día (comprobado)")
                        return@withContext Result.success(Unit)
                    }
                    is FreshnessResult.TtlFresh -> {
                        val hoursRemaining = ((freshness.ttlMs - (System.currentTimeMillis() - freshness.lastSyncAt)) / 3600000).coerceAtLeast(0)
                        android.util.Log.i("XtreamCatalogSync", "SYNC_SKIPPED_TTL_FRESH: source $sourceId dentro de período TTL (~${hoursRemaining}h restantes). Sync omitido.")
                        _syncState.value = SyncState.TtlFresh(sourceId, "Catálogo dentro de TTL (~${hoursRemaining}h restantes)")
                        return@withContext Result.success(Unit)
                    }
                    is FreshnessResult.CheckFailed -> {
                        android.util.Log.w("XtreamCatalogSync", "FRESHNESS_CHECK_FAILED: ${freshness.reason}. Se preserva catálogo local de Room.")
                        _syncState.value = SyncState.OfflineUsingCache(sourceId, "Modo sin conexión - Catálogo local disponible")
                        return@withContext Result.success(Unit)
                    }
                    is FreshnessResult.Changed -> {
                        android.util.Log.i("XtreamCatalogSync", "Cambio detectado para $sourceId (nueva versión=${freshness.newVersion}). Iniciando sync hacia staging.")
                        remoteVersionToSave = freshness.newVersion
                        remoteEtagToSave = freshness.newEtag
                    }
                }
            }

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
                return@withContext syncM3uToStaging(sourceId, syncId, serverUrl, remoteVersionToSave, remoteEtagToSave)
            }

            // ── RAMA XTREAM CODES CON STAGING ────────────────────────────────────
            return@withContext syncXtreamToStaging(sourceId, syncId, serverUrl, user, pass)

        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Error de sincronización con el servidor IPTV"
            _syncState.value = SyncState.Error(errorMsg)
            Result.failure(e)
        } finally {
            mutex.unlock()
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
                        android.util.Log.w("XtreamCatalogSync", "HEAD no soportado (HTTP ${response.code}) por $serverUrl. Aplicando fallback seguro a TTL.")
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
            android.util.Log.w("XtreamCatalogSync", "Error en HEAD request a $serverUrl: ${e.message}")
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
        remoteEtag: String = ""
    ): Result<Unit> {
        try {
            _syncState.value = SyncState.SyncingLive(0)

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

            if (totalInStaging == 0 && (serverUrl.startsWith("http") || isLocalFile(serverUrl))) {
                try {
                    val m3uReq = Request.Builder().url(serverUrl).build()
                    val m3uRes = httpClient.newCall(m3uReq).execute()
                    if (m3uRes.isSuccessful) {
                        val body = m3uRes.body?.string() ?: ""
                        var count = 0
                        val lines = body.lines()
                        var currentName = "Canal"
                        var currentGroup = "General"
                        var currentLogo: String? = null
                        var currentMediaType: String? = null
                        val groupRegex = Regex("group-title=\"([^\"]+)\"")
                        val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")
                        val mediaTypeRegex = Regex("(?:tvg-type|media-type)=\"([^\"]+)\"")

                        for (line in lines) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("#EXTINF:")) {
                                currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1) ?: "General"
                                currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1)
                                currentMediaType = mediaTypeRegex.find(trimmed)?.groupValues?.get(1)
                                currentName = trimmed.substringAfterLast(",").trim().ifEmpty { "Canal" }
                            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
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

                                if (channelStagingBatch.size >= 500) {
                                    database.catalogStagingDao().insertChannelsStaging(channelStagingBatch)
                                    channelStagingBatch.clear()
                                }
                                if (movieStagingBatch.size >= 500) {
                                    database.catalogStagingDao().insertMoviesStaging(movieStagingBatch)
                                    movieStagingBatch.clear()
                                }
                                if (seriesStagingBatch.size >= 500) {
                                    database.catalogStagingDao().insertSeriesStaging(seriesStagingBatch)
                                    seriesStagingBatch.clear()
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
                    }
                } catch (e: Exception) {
                    strategyErrors.add("M3U directo: ${e.message ?: e.javaClass.simpleName}")
                }
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
                    isAdult = grp.contains("adult", ignoreCase = true) || grp.contains("+18"),
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
                    isAdult = grp.contains("adult", ignoreCase = true) || grp.contains("+18"),
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
                    isAdult = grp.contains("adult", ignoreCase = true) || grp.contains("+18"),
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
                _syncState.value = SyncState.Error(errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            // PASO 8 (P0 #4): Swap atómico corto en base de datos Room
            performAtomicSwap(syncId, sourceId)

            // PASO 3 (P0 #5): Guardar metadata de sincronización exitosa SOLO tras swap exitoso
            val versionToSave = remoteVersion.ifBlank { System.currentTimeMillis().toString() }
            preferencesDataSource?.saveSyncMetadata(
                SourceSyncMetadata(
                    sourceId = sourceId,
                    lastSuccessfulSyncAt = System.currentTimeMillis(),
                    lastSuccessfulVersion = versionToSave,
                    lastEtag = remoteEtag,
                    lastSyncResult = "SUCCESS",
                    channelCount = stagingChannels,
                    movieCount = stagingMovies,
                    seriesCount = stagingSeries
                )
            )

            _syncState.value = SyncState.Completed(
                channelsCount = stagingChannels,
                moviesCount = stagingMovies,
                seriesCount = stagingSeries
            )
            return Result.success(Unit)
        } catch (e: Exception) {
            cleanupStaging(syncId)
            val errorMsg = "Sincronización M3U interrumpida: ${e.message ?: e.javaClass.simpleName}"
            android.util.Log.e("XtreamCatalogSync", errorMsg, e)
            _syncState.value = SyncState.Error(errorMsg)
            return Result.failure(e)
        }
    }

    private suspend fun syncXtreamToStaging(
        sourceId: String,
        syncId: String,
        serverUrl: String,
        user: String,
        pass: String
    ): Result<Unit> {
        try {
            _syncState.value = SyncState.Authenticating
            val api = NetworkClient.createXtreamApiService(serverUrl)

            // 1. Validar credenciales y cuenta
            val auth = api.authenticate(user, pass)
            val userInfo = auth.userInfo ?: throw IllegalStateException("Respuesta de autenticación vacía del servidor IPTV")

            if (userInfo.status != "Active") {
                val errorMsg = "La cuenta IPTV no está activa (Estado: ${userInfo.status})"
                _syncState.value = SyncState.Error(errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
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
            _syncState.value = SyncState.SyncingCategories("Descargando categorías...")
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
                        isAdult = it.categoryName.contains("adult", ignoreCase = true) || it.categoryName.contains("+18"),
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
                        isAdult = it.categoryName.contains("adult", ignoreCase = true) || it.categoryName.contains("+18"),
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
                        isAdult = it.categoryName.contains("adult", ignoreCase = true) || it.categoryName.contains("+18"),
                        sourceId = sourceId
                    )
                )
            }
            if (categoryStagingEntities.isNotEmpty()) {
                database.catalogStagingDao().insertCategoriesStaging(categoryStagingEntities)
            }

            // 3. Sincronizar Canales en Vivo hacia Staging
            _syncState.value = SyncState.SyncingLive(0)
            val okHttpClient = NetworkClient.createOkHttpClient()

            val liveUrl = "${if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"}player_api.php?username=$user&password=$pass&action=get_live_streams"
            val liveRequest = Request.Builder().url(liveUrl).build()
            var liveCount = 0
            okHttpClient.newCall(liveRequest).execute().use { response ->
                response.body?.byteStream()?.let { stream ->
                    XtreamStreamingParser.parseLiveStreams(
                        inputStream = stream,
                        sourceId = sourceId,
                        batchSize = 500,
                        onBatchParsed = { batch ->
                            val stagingBatch = batch.map { it.toStaging(syncId) }
                            database.catalogStagingDao().insertChannelsStaging(stagingBatch)
                            liveCount += batch.size
                            _syncState.value = SyncState.SyncingLive(liveCount)
                        }
                    )
                }
            }

            // 4. Sincronizar Películas VOD hacia Staging
            _syncState.value = SyncState.SyncingMovies(0)
            val vodUrl = "${if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"}player_api.php?username=$user&password=$pass&action=get_vod_streams"
            val vodRequest = Request.Builder().url(vodUrl).build()
            var movieCount = 0
            okHttpClient.newCall(vodRequest).execute().use { response ->
                response.body?.byteStream()?.let { stream ->
                    XtreamStreamingParser.parseVodStreams(
                        inputStream = stream,
                        sourceId = sourceId,
                        batchSize = 500,
                        onBatchParsed = { batch ->
                            val stagingBatch = batch.map { it.toStaging(syncId) }
                            database.catalogStagingDao().insertMoviesStaging(stagingBatch)
                            movieCount += batch.size
                            _syncState.value = SyncState.SyncingMovies(movieCount)
                        }
                    )
                }
            }

            // 5. Sincronizar Series hacia Staging
            _syncState.value = SyncState.SyncingSeries(0)
            val seriesUrl = "${if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"}player_api.php?username=$user&password=$pass&action=get_series"
            val seriesRequest = Request.Builder().url(seriesUrl).build()
            var seriesCount = 0
            okHttpClient.newCall(seriesRequest).execute().use { response ->
                response.body?.byteStream()?.let { stream ->
                    XtreamStreamingParser.parseSeriesStreams(
                        inputStream = stream,
                        sourceId = sourceId,
                        batchSize = 500,
                        onBatchParsed = { batch ->
                            val stagingBatch = batch.map { it.toStaging(syncId) }
                            database.catalogStagingDao().insertSeriesStaging(stagingBatch)
                            seriesCount += batch.size
                            _syncState.value = SyncState.SyncingSeries(seriesCount)
                        }
                    )
                }
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
                "Validación Staging Xtream [$sourceId] (syncId: $syncId): " +
                    "OLD: [channels=$oldChannels, movies=$oldMovies, series=$oldSeries, total=$totalOld] -> " +
                    "STAGING: [channels=$stagingChannels, movies=$stagingMovies, series=$stagingSeries, categories=$stagingCategoriesCount, total=$totalNew]"
            )

            if (totalNew == 0) {
                cleanupStaging(syncId)
                val errorMsg = "El servidor Xtream devolvió 0 elementos en total. Se preserva el catálogo activo."
                android.util.Log.w("XtreamCatalogSync", errorMsg)
                _syncState.value = SyncState.Error(errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            // PASO 8 (P0 #4): Swap atómico corto en base de datos Room
            performAtomicSwap(syncId, sourceId)

            // PASO 3 (P0 #5): Guardar metadata de sincronización exitosa para Xtream
            preferencesDataSource?.saveSyncMetadata(
                SourceSyncMetadata(
                    sourceId = sourceId,
                    lastSuccessfulSyncAt = System.currentTimeMillis(),
                    lastSuccessfulVersion = System.currentTimeMillis().toString(),
                    lastEtag = "",
                    lastSyncResult = "SUCCESS",
                    channelCount = stagingChannels,
                    movieCount = stagingMovies,
                    seriesCount = stagingSeries
                )
            )

            _syncState.value = SyncState.Completed(
                channelsCount = stagingChannels,
                moviesCount = stagingMovies,
                seriesCount = stagingSeries
            )
            return Result.success(Unit)

        } catch (e: Exception) {
            cleanupStaging(syncId)
            val errorMsg = e.localizedMessage ?: "Error de sincronización con el servidor IPTV"
            _syncState.value = SyncState.Error(errorMsg)
            return Result.failure(e)
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

    /**
     * FASE 33 — Extrae canales de un proveedor hacia "Mi Lista Personalizada LELOUCH".
     * Extrae los canales de las categorías indicadas y los publica en la playlist del
     * reproductor web (Supabase `playlist_items`). Ya no crea una fuente local: la lista
     * personalizada fue retirada de la APK en la FASE 35.
     */
    suspend fun extractChannelsToCustomPlaylist(
        sourceId: String,
        categoryNames: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        try {
            val allChannels = database.channelDao().getAllChannelsBySource(sourceId).first()
            val toExtract = allChannels.filter { categoryNames.contains(it.categoryName) }
            if (toExtract.isEmpty()) return@withContext 0

            // FASE 35: ya no se escribe una copia local bajo `custom_lelouch`. Esa fuente está
            // retirada y esas filas solo alimentaban los 3.594 ítems fantasma que se purgaron.
            // La extracción conserva su único destino útil: la playlist del reproductor web.

            // Sincronizar en segundo plano a Supabase playlist_items
            try {
                val cleanPlaylistId = "c9da4a22-534c-41f9-af70-42ee9f6682df"
                val batchSize = 200
                for (i in toExtract.indices step batchSize) {
                    val chunk = toExtract.subList(i, minOf(i + batchSize, toExtract.size))
                    val jsonRows = chunk.mapIndexed { idx, ch ->
                        """{"playlist_id":"$cleanPlaylistId","item_type":"direct","media_type":"live","direct_name":"${ch.name.replace("\"", "\\\"")}","direct_url":"${ch.streamUrl.replace("\"", "\\\"")}","direct_group":"${ch.categoryName.replace("\"", "\\\"")}","direct_logo":"${ch.streamIcon ?: ""}","position":${i + idx},"enabled":true}"""
                    }.joinToString(",", "[", "]")

                    val req = Request.Builder()
                        .url("https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items")
                        .post(jsonRows.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .build()
                    httpClient.newCall(req).execute().close()
                }
            } catch (_: Exception) {}

            return@withContext toExtract.size
        } catch (e: Exception) {
            return@withContext 0
        }
    }
}
