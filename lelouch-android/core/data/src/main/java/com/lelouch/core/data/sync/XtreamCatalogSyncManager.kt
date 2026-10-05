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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SyncState {
    data object Idle : SyncState
    data class Checking(val sourceId: String) : SyncState
    data class UpToDate(val sourceId: String, val message: String = "Catálogo actualizado") : SyncState
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
    data class Changed(val newVersion: String, val newEtag: String = "") : FreshnessResult
    data class CheckFailed(val reason: String) : FreshnessResult
}

class XtreamCatalogSyncManager(
    private val database: LelouchDatabase,
    private val preferencesDataSource: UserPreferencesDataSource? = null
) {
    companion object {
        const val XTREAM_DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L // 24 horas para Xtream
        const val M3U_DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L // 24 horas para M3U genérico
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // PASO 5: Mutex por sourceId para evitar múltiples syncs concurrentes de la misma fuente
    private val sourceSyncMutexes = ConcurrentHashMap<String, Mutex>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

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
                    // Tipo A: Lelouch / Supabase
                    effectiveType == com.lelouch.core.model.SourceType.M3U && isLelouchOrSupabase(serverUrl, sourceId) -> {
                        checkLelouchRemoteVersion(sourceId, serverUrl, metadata)
                    }
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
                        android.util.Log.i("XtreamCatalogSync", "SYNC_SKIPPED_UNCHANGED: source $sourceId al día (versión=${freshness.version})")
                        _syncState.value = SyncState.UpToDate(sourceId, "Catálogo al día")
                        return@withContext Result.success(Unit)
                    }
                    is FreshnessResult.CheckFailed -> {
                        android.util.Log.w("XtreamCatalogSync", "FRESHNESS_CHECK_FAILED: ${freshness.reason}. Se preserva catálogo local de Room.")
                        _syncState.value = SyncState.UpToDate(sourceId, "Modo sin conexión - Catálogo local disponible")
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

            // ── FASE 32: Listas M3U/Custom resueltas en Supabase / Vercel ──────────
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

    private fun isLelouchOrSupabase(serverUrl: String, sourceId: String): Boolean {
        return serverUrl.contains("rotupbdeljgfddywryhk.supabase.co") ||
                serverUrl.contains("/api/playlist") ||
                serverUrl.contains("token=") ||
                sourceId == "custom_lelouch" ||
                sourceId.startsWith("custom_")
    }

    private fun isLocalFile(serverUrl: String): Boolean {
        return serverUrl.startsWith("file://") || serverUrl.startsWith("/") || serverUrl.contains("content://")
    }

    /**
     * PASO 2: Verificación de frescura para listas Lelouch/Supabase vía endpoint de version o Supabase.
     */
    suspend fun checkLelouchRemoteVersion(
        sourceId: String,
        serverUrl: String,
        metadata: SourceSyncMetadata?
    ): FreshnessResult = withContext(Dispatchers.IO) {
        try {
            val tokenFromUrl = when {
                serverUrl.contains("/api/playlist/version/") -> serverUrl.substringAfter("/api/playlist/version/").substringBefore("?").trim()
                serverUrl.contains("/api/playlist/") -> serverUrl.substringAfter("/api/playlist/").substringBefore("?").trim()
                serverUrl.contains("token=") -> serverUrl.substringAfter("token=").substringBefore("&").trim()
                else -> ""
            }

            val localVer = metadata?.lastSuccessfulVersion.orEmpty()

            // 1. Intentar endpoint de versión de Vercel (/api/playlist/version/<token>)
            if (tokenFromUrl.isNotBlank()) {
                val verUrl = "https://lelouch-web-player.vercel.app/api/playlist/version/$tokenFromUrl"
                val reqBuilder = Request.Builder().url(verUrl)
                if (localVer.isNotBlank()) {
                    reqBuilder.addHeader("x-local-version", localVer)
                    reqBuilder.addHeader("If-None-Match", "\"v$localVer\"")
                }
                val res = httpClient.newCall(reqBuilder.build()).execute()
                if (res.code == 304) {
                    return@withContext FreshnessResult.Unchanged(version = localVer)
                }
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val obj = json.parseToJsonElement(body).jsonObject
                    val ver = obj["version"]?.jsonPrimitive?.content ?: obj["updatedAt"]?.jsonPrimitive?.content ?: ""
                    val etag = res.header("ETag")?.trim('"') ?: ""
                    if (ver.isNotBlank() && ver == localVer) {
                        return@withContext FreshnessResult.Unchanged(version = ver, etag = etag)
                    } else if (ver.isNotBlank()) {
                        return@withContext FreshnessResult.Changed(newVersion = ver, newEtag = etag)
                    }
                }
            }

            // 2. Consulta directa a Supabase (custom_playlists version y updated_at)
            val cleanPlaylistId = if (sourceId == "custom_lelouch" || sourceId.startsWith("custom_")) {
                val cpReq = Request.Builder()
                    .url("https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?select=id,version,updated_at&order=updated_at.desc&limit=1")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .build()
                val cpRes = httpClient.newCall(cpReq).execute()
                if (cpRes.isSuccessful) {
                    val body = cpRes.body?.string() ?: ""
                    val arr = json.parseToJsonElement(body).jsonArray
                    val first = arr.firstOrNull()?.jsonObject
                    val ver = first?.get("version")?.jsonPrimitive?.content ?: first?.get("updated_at")?.jsonPrimitive?.content ?: ""
                    if (ver.isNotBlank() && ver == localVer) {
                        return@withContext FreshnessResult.Unchanged(version = ver)
                    } else if (ver.isNotBlank()) {
                        return@withContext FreshnessResult.Changed(newVersion = ver)
                    }
                    first?.get("id")?.jsonPrimitive?.content ?: "c9da4a22-534c-41f9-af70-42ee9f6682df"
                } else {
                    "c9da4a22-534c-41f9-af70-42ee9f6682df"
                }
            } else {
                sourceId.removePrefix("custom_")
            }

            val cpUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?select=version,updated_at&id=eq.$cleanPlaylistId"
            val req = Request.Builder()
                .url(cpUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .build()
            val res = httpClient.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: ""
                val arr = json.parseToJsonElement(body).jsonArray
                val first = arr.firstOrNull()?.jsonObject
                val ver = first?.get("version")?.jsonPrimitive?.content ?: first?.get("updated_at")?.jsonPrimitive?.content ?: ""
                if (ver.isNotBlank() && ver == localVer) {
                    FreshnessResult.Unchanged(version = ver)
                } else if (ver.isNotBlank()) {
                    FreshnessResult.Changed(newVersion = ver)
                } else {
                    FreshnessResult.Changed(newVersion = System.currentTimeMillis().toString())
                }
            } else {
                FreshnessResult.CheckFailed("HTTP ${res.code}")
            }
        } catch (e: Exception) {
            FreshnessResult.CheckFailed(e.message ?: e.javaClass.simpleName)
        }
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
            FreshnessResult.Unchanged(version = metadata.lastSuccessfulVersion)
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
        if (metadata.lastEtag.isNotBlank()) {
            try {
                val headReq = Request.Builder()
                    .url(serverUrl)
                    .head()
                    .addHeader("If-None-Match", metadata.lastEtag)
                    .build()
                val resp = httpClient.newCall(headReq).execute()
                if (resp.code == 304) {
                    return@withContext FreshnessResult.Unchanged(metadata.lastSuccessfulVersion, metadata.lastEtag)
                }
                val newEtag = resp.header("ETag")?.trim('"')
                if (resp.isSuccessful && !newEtag.isNullOrBlank() && newEtag == metadata.lastEtag) {
                    return@withContext FreshnessResult.Unchanged(metadata.lastSuccessfulVersion, metadata.lastEtag)
                }
            } catch (_: Exception) {}
        }
        val age = System.currentTimeMillis() - metadata.lastSuccessfulSyncAt
        if (age < M3U_DEFAULT_TTL_MS) {
            FreshnessResult.Unchanged(version = metadata.lastSuccessfulVersion, etag = metadata.lastEtag)
        } else {
            FreshnessResult.Changed(newVersion = "ttl_expired")
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

            // ── ESTRATEGIA 1: Consulta DIRECTA a Supabase v_resolved_playlist_items ──
            val isSupabasePlaylist = isLelouchOrSupabase(serverUrl, sourceId)

            if (isSupabasePlaylist) {
                try {
                    val cleanPlaylistId = if (sourceId == "custom_lelouch" || sourceId.startsWith("custom_")) {
                        val cpReq = Request.Builder()
                            .url("https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?select=id&order=updated_at.desc&limit=1")
                            .addHeader("apikey", SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                            .build()
                        val cpRes = httpClient.newCall(cpReq).execute()
                        val cpBody = if (cpRes.isSuccessful) cpRes.body?.string() ?: "" else ""
                        try {
                            val cpArr = json.parseToJsonElement(cpBody).jsonArray
                            cpArr.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content ?: "c9da4a22-534c-41f9-af70-42ee9f6682df"
                        } catch (_: Exception) {
                            "c9da4a22-534c-41f9-af70-42ee9f6682df"
                        }
                    } else {
                        sourceId.removePrefix("custom_")
                    }

                    var offset = 0
                    val pageSize = 1000
                    var hasMore = true
                    var count = 0

                    while (hasMore && offset < 50000) {
                        _syncState.value = SyncState.SyncingLive(offset)
                        val itemsUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?playlist_id=eq.$cleanPlaylistId&enabled=eq.true&order=position.asc&limit=$pageSize&offset=$offset"
                        val req = Request.Builder()
                            .url(itemsUrl)
                            .addHeader("apikey", SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                            .build()
                        val res = httpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            val body = res.body?.string() ?: ""
                            val jsonArray = try { json.parseToJsonElement(body).jsonArray } catch (_: Exception) { kotlinx.serialization.json.JsonArray(emptyList()) }
                            if (jsonArray.isEmpty()) break

                            for (element in jsonArray) {
                                val obj = element.jsonObject
                                val itemId = obj["id"]?.jsonPrimitive?.content ?: continue
                                val name = obj["name"]?.jsonPrimitive?.content ?: "Elemento"
                                val group = obj["group"]?.jsonPrimitive?.content ?: "General"
                                val logo = obj["logo"]?.jsonPrimitive?.content
                                val directUrl = obj["resolved_stream_url"]?.jsonPrimitive?.content
                                    ?: obj["direct_url"]?.jsonPrimitive?.content ?: ""

                                val isHeaderSeparator = name.matches(Regex("^[\\s\\-=*#_]{3,}.*")) ||
                                    name.matches(Regex(".*[\\s\\-=*#_]{3,}$")) ||
                                    name.trim().startsWith("---") ||
                                    name.trim().startsWith("===")

                                val isValidUrl = directUrl.isNotBlank() &&
                                    (directUrl.startsWith("http://", ignoreCase = true) || directUrl.startsWith("https://", ignoreCase = true)) &&
                                    !directUrl.contains("localhost") &&
                                    !directUrl.contains("undefined")

                                if (!isValidUrl || isHeaderSeparator) {
                                    continue
                                }

                                val mediaType = (obj["media_type"]?.jsonPrimitive?.content ?: "").lowercase()
                                val pos = obj["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: count
                                val streamId = (name + directUrl).hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }

                                val resolvedType = detectMediaType(mediaType, name, group, directUrl)

                                if (resolvedType == "movie") {
                                    vodCategories.add(group)
                                    movieStagingBatch.add(
                                        MovieStagingEntity(
                                            syncId = syncId,
                                            id = "$sourceId-$itemId",
                                            streamId = streamId,
                                            num = pos,
                                            name = name,
                                            title = name,
                                            streamIcon = logo,
                                            categoryId = "$sourceId-${group.hashCode()}",
                                            categoryName = group,
                                            containerExtension = "mp4",
                                            streamUrl = directUrl,
                                            sourceId = sourceId
                                        )
                                    )
                                } else if (resolvedType == "series") {
                                    seriesCategories.add(group)
                                    seriesStagingBatch.add(
                                        SeriesStagingEntity(
                                            syncId = syncId,
                                            id = "$sourceId-$itemId",
                                            seriesId = streamId,
                                            num = pos,
                                            name = name,
                                            title = name,
                                            cover = logo,
                                            categoryId = "$sourceId-${group.hashCode()}",
                                            categoryName = group,
                                            sourceId = sourceId
                                        )
                                    )
                                } else {
                                    liveCategories.add(group)
                                    channelStagingBatch.add(
                                        ChannelStagingEntity(
                                            syncId = syncId,
                                            id = "$sourceId-$itemId",
                                            streamId = streamId,
                                            num = pos,
                                            name = name,
                                            streamType = "live",
                                            streamIcon = logo,
                                            categoryId = "$sourceId-${group.hashCode()}",
                                            categoryName = group,
                                            streamUrl = directUrl,
                                            containerExtension = "m3u8",
                                            sourceId = sourceId
                                        )
                                    )
                                }
                                count++
                            }

                            // Insertar lote en staging
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

                            if (jsonArray.size < pageSize) {
                                hasMore = false
                            } else {
                                offset += pageSize
                            }
                        } else {
                            strategyErrors.add("Vista Supabase: HTTP ${res.code}")
                            hasMore = false
                        }
                    }
                } catch (e: Exception) {
                    strategyErrors.add("Vista Supabase: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            // ── ESTRATEGIA 2: Fallback M3U / #EXTINF Directo ──
            val totalInStaging = database.catalogStagingDao().getStagingChannelCount(syncId) +
                database.catalogStagingDao().getStagingMovieCount(syncId) +
                database.catalogStagingDao().getStagingSeriesCount(syncId)

            if (totalInStaging == 0 && (serverUrl.startsWith("http") || isLocalFile(serverUrl))) {
                try {
                    val m3uUrl = if (serverUrl.contains("/api/playlist/") && !serverUrl.contains("?token=")) {
                        val tok = serverUrl.substringAfterLast("/").substringBefore("?").trim()
                        "https://lelouch-web-player.vercel.app/api/playlist?token=$tok"
                    } else serverUrl

                    val m3uReq = Request.Builder().url(m3uUrl).build()
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
}
