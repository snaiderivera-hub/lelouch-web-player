package com.lelouch.core.data.sync

import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.database.entity.CategoryEntity
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.network.NetworkClient
import com.lelouch.core.network.StreamUrlResolver
import com.lelouch.core.network.XtreamUrlBuilder
import com.lelouch.core.network.XtreamStreamingParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SyncState {
    data object Idle : SyncState
    data object Authenticating : SyncState
    data class SyncingCategories(val step: String) : SyncState
    data class SyncingLive(val count: Int) : SyncState
    data class SyncingMovies(val count: Int) : SyncState
    data class SyncingSeries(val count: Int) : SyncState
    data class Completed(val channelsCount: Int, val moviesCount: Int, val seriesCount: Int) : SyncState
    data class Error(val message: String) : SyncState
}

class XtreamCatalogSyncManager(
    private val database: LelouchDatabase
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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

    suspend fun syncAll(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String,
        sourceType: com.lelouch.core.model.SourceType = com.lelouch.core.model.SourceType.XTREAM
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // ── FASE 33: Autodetección de M3U ────────────────────────────────────
        // Varios llamadores (onLogin / onAddSource en MainActivity) invocan syncAll sin
        // sourceType, lo que cae en el default XTREAM y dispara player_api.php contra
        // https://lelouch-web-player.vercel.app -> HTTP 404 silencioso -> Room queda vacío
        // y la UI no muestra ningún canal. Si la base no es un servidor Xtream legítimo,
        // se fuerza la rama M3U sin importar lo que el llamador haya declarado.
        val effectiveType = if (
            sourceType == com.lelouch.core.model.SourceType.XTREAM &&
            !StreamUrlResolver.isXtreamBase(serverUrl, user)
        ) {
            com.lelouch.core.model.SourceType.M3U
        } else {
            sourceType
        }

        // ── FASE 32: Listas M3U/Custom resueltas en Supabase / Vercel ──────────
        // Para playlists personalizadas M3U, resuelve items directamente desde Supabase
        // sin llamar al endpoint Xtream player_api.php (evitando errores HTTP 403).
        if (effectiveType == com.lelouch.core.model.SourceType.M3U) {
            try {
                _syncState.value = SyncState.SyncingLive(0)

                val tokenFromUrl = when {
                    serverUrl.contains("/api/playlist/") -> serverUrl.substringAfter("/api/playlist/").substringBefore("?").trim()
                    serverUrl.contains("token=") -> serverUrl.substringAfter("token=").substringBefore("&").trim()
                    else -> ""
                }

                val channelEntities = mutableListOf<ChannelEntity>()
                val movieEntities = mutableListOf<MovieEntity>()
                val seriesEntities = mutableListOf<SeriesEntity>()
                val liveCategories = mutableSetOf<String>()
                val vodCategories = mutableSetOf<String>()
                val seriesCategories = mutableSetOf<String>()

                val json = Json { ignoreUnknownKeys = true }
                // FASE 33: las 3 estrategias registran aquí su fallo real. Antes se tragaban
                // con `catch (_: Exception) {}` y el usuario nunca sabía por qué no había nada.
                val strategyErrors = mutableListOf<String>()
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"

                // ── ESTRATEGIA 1: Vercel JSON Manifest (Entrega nativa y estructurada de Lelouch) ──
                if (tokenFromUrl.isNotBlank() || serverUrl.contains("/api/playlist")) {
                    val targetToken = tokenFromUrl.ifBlank { "pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R" }
                    val vercelManifestUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=$targetToken"
                    try {
                        val vReq = Request.Builder()
                            .url(vercelManifestUrl)
                            .addHeader("Accept", "application/json")
                            .build()
                        val vRes = httpClient.newCall(vReq).execute()
                        if (vRes.isSuccessful) {
                            val vBody = vRes.body?.string() ?: ""
                            if (vBody.trimStart().startsWith("{")) {
                                val root = json.parseToJsonElement(vBody).jsonObject
                                val items = root["items"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
                                var idx = 0
                                for (it in items) {
                                    val obj = it.jsonObject
                                    val id = obj["id"]?.jsonPrimitive?.content ?: "$idx"
                                    val name = obj["name"]?.jsonPrimitive?.content ?: "Elemento"
                                    val group = obj["group"]?.jsonPrimitive?.content ?: "General"
                                    val logo = obj["logo"]?.jsonPrimitive?.content
                                    val streamUrl = obj["streamUrl"]?.jsonPrimitive?.content ?: ""
                                    val mediaType = (obj["mediaType"]?.jsonPrimitive?.content ?: "").lowercase()
                                    if (streamUrl.isBlank() || streamUrl.contains("undefined")) continue

                                    val streamId = (name + streamUrl).hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
                                    val resolvedType = detectMediaType(mediaType, name, group, streamUrl)

                                    if (resolvedType == "movie") {
                                        vodCategories.add(group)
                                        movieEntities.add(
                                            MovieEntity(
                                                id = "$sourceId-$id",
                                                streamId = streamId,
                                                num = idx,
                                                name = name,
                                                title = name,
                                                streamIcon = logo,
                                                categoryId = "$sourceId-${group.hashCode()}",
                                                categoryName = group,
                                                containerExtension = "mp4",
                                                streamUrl = streamUrl,
                                                sourceId = sourceId
                                            )
                                        )
                                    } else if (resolvedType == "series") {
                                        seriesCategories.add(group)
                                        seriesEntities.add(
                                            SeriesEntity(
                                                id = "$sourceId-$id",
                                                seriesId = streamId,
                                                num = idx,
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
                                        channelEntities.add(
                                            ChannelEntity(
                                                id = "$sourceId-$id",
                                                streamId = streamId,
                                                num = idx,
                                                name = name,
                                                streamType = "live",
                                                streamIcon = logo,
                                                categoryId = "$sourceId-${group.hashCode()}",
                                                categoryName = group,
                                                streamUrl = streamUrl,
                                                containerExtension = "m3u8",
                                                sourceId = sourceId
                                            )
                                        )
                                    }
                                    idx++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        strategyErrors.add("Manifiesto Vercel: ${e.message ?: e.javaClass.simpleName}")
                    }
                }

                // ── ESTRATEGIA 2: Supabase vista v_resolved_playlist_items (si aún no hay items) ──
                if (channelEntities.isEmpty() && movieEntities.isEmpty()) {
                    try {
                        val cleanPlaylistId = if (sourceId == "custom_lelouch" || sourceId.startsWith("custom_")) {
                            val cpReq = Request.Builder()
                                .url("https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?select=id&order=updated_at.desc&limit=1")
                                .addHeader("apikey", supabaseKey)
                                .addHeader("Authorization", "Bearer $supabaseKey")
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

                        while (hasMore && offset < 30000) {
                            val itemsUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?playlist_id=eq.$cleanPlaylistId&enabled=eq.true&order=position.asc&limit=$pageSize&offset=$offset"
                            val req = Request.Builder()
                                .url(itemsUrl)
                                .addHeader("apikey", supabaseKey)
                                .addHeader("Authorization", "Bearer $supabaseKey")
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
                                    val mediaType = (obj["media_type"]?.jsonPrimitive?.content ?: "").lowercase()
                                    val pos = obj["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: count
                                    val streamId = (name + directUrl).hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }

                                    val resolvedType = detectMediaType(mediaType, name, group, directUrl)

                                    if (resolvedType == "movie") {
                                        vodCategories.add(group)
                                        movieEntities.add(
                                            MovieEntity(
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
                                        seriesEntities.add(
                                            SeriesEntity(
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
                                        channelEntities.add(
                                            ChannelEntity(
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

                // ── ESTRATEGIA 3: Fallback M3U / #EXTINF Directo ──
                if (channelEntities.isEmpty() && movieEntities.isEmpty() && serverUrl.startsWith("http")) {
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
                                        movieEntities.add(
                                            MovieEntity(
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
                                        seriesEntities.add(
                                            SeriesEntity(
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
                                        channelEntities.add(
                                            ChannelEntity(
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
                                }
                            }
                        }
                    } catch (e: Exception) {
                        strategyErrors.add("M3U directo: ${e.message ?: e.javaClass.simpleName}")
                    }
                }

                // ── FASE 33: GUARDADO ATÓMICO — no se borra Room sin tener datos nuevos ──
                // Antes los deleteBySource corrían siempre dentro del try: si las 3 estrategias
                // fallaban, se vaciaba la base y se devolvía Result.success con 0 canales,
                // dejando la UI en silencio. Ahora: o hay datos y se reemplaza, o no se toca
                // nada y se reporta el error real.
                val hasAnyItem = channelEntities.isNotEmpty() ||
                    movieEntities.isNotEmpty() ||
                    seriesEntities.isNotEmpty()

                if (!hasAnyItem) {
                    val detail = if (strategyErrors.isEmpty()) {
                        "el proveedor no devolvió ningún elemento para esta lista"
                    } else {
                        strategyErrors.joinToString(" | ")
                    }
                    val errorMsg = "No se pudo cargar el catálogo M3U: $detail"
                    android.util.Log.e("XtreamCatalogSync", errorMsg)
                    _syncState.value = SyncState.Error(errorMsg)
                    return@withContext Result.failure(IllegalStateException(errorMsg))
                }

                database.channelDao().deleteChannelsBySource(sourceId)
                database.movieDao().deleteMoviesBySource(sourceId)
                database.seriesDao().deleteSeriesBySource(sourceId)
                database.categoryDao().deleteCategoriesByType(sourceId, "LIVE")
                database.categoryDao().deleteCategoriesByType(sourceId, "VOD")
                database.categoryDao().deleteCategoriesByType(sourceId, "SERIES")

                val allCategories = mutableListOf<CategoryEntity>()
                allCategories.addAll(liveCategories.map { grp ->
                    CategoryEntity(
                        id = "$sourceId-LIVE-${grp.hashCode()}",
                        categoryId = "$sourceId-${grp.hashCode()}",
                        categoryName = grp,
                        type = "LIVE",
                        isAdult = grp.contains("adult", ignoreCase = true) || grp.contains("+18"),
                        sourceId = sourceId
                    )
                })
                allCategories.addAll(vodCategories.map { grp ->
                    CategoryEntity(
                        id = "$sourceId-VOD-${grp.hashCode()}",
                        categoryId = "$sourceId-${grp.hashCode()}",
                        categoryName = grp,
                        type = "VOD",
                        isAdult = grp.contains("adult", ignoreCase = true) || grp.contains("+18"),
                        sourceId = sourceId
                    )
                })
                allCategories.addAll(seriesCategories.map { grp ->
                    CategoryEntity(
                        id = "$sourceId-SERIES-${grp.hashCode()}",
                        categoryId = "$sourceId-${grp.hashCode()}",
                        categoryName = grp,
                        type = "SERIES",
                        isAdult = grp.contains("adult", ignoreCase = true) || grp.contains("+18"),
                        sourceId = sourceId
                    )
                })

                if (allCategories.isNotEmpty()) database.categoryDao().insertCategories(allCategories)
                if (channelEntities.isNotEmpty()) database.channelDao().insertChannels(channelEntities)
                if (movieEntities.isNotEmpty()) database.movieDao().insertMovies(movieEntities)
                if (seriesEntities.isNotEmpty()) database.seriesDao().insertSeries(seriesEntities)

                _syncState.value = SyncState.Completed(
                    channelsCount = channelEntities.size,
                    moviesCount = movieEntities.size,
                    seriesCount = seriesEntities.size
                )
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                // FASE 33: un fallo inesperado es un error, nunca un éxito con 0 elementos.
                val errorMsg = "Sincronización M3U interrumpida: ${e.message ?: e.javaClass.simpleName}"
                android.util.Log.e("XtreamCatalogSync", errorMsg, e)
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext Result.failure(e)
            }
        }

        try {
            _syncState.value = SyncState.Authenticating
            val api = NetworkClient.createXtreamApiService(serverUrl)


            // 1. Validar credenciales y cuenta
            val auth = api.authenticate(user, pass)
            val userInfo = auth.userInfo ?: throw IllegalStateException("Respuesta de autenticación vacía del servidor IPTV")

            if (userInfo.status != "Active") {
                val errorMsg = "La cuenta IPTV no está activa (Estado: ${userInfo.status})"
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            // 2. Sincronizar Categorías (Live, VOD, Series)
            _syncState.value = SyncState.SyncingCategories("Descargando categorías...")
            val liveCategories = try { api.getLiveCategories(user, pass) } catch (_: Exception) { emptyList() }
            val vodCategories = try { api.getVodCategories(user, pass) } catch (_: Exception) { emptyList() }
            val seriesCategories = try { api.getSeriesCategories(user, pass) } catch (_: Exception) { emptyList() }

            val categoryEntities = mutableListOf<CategoryEntity>()
            liveCategories.forEach {
                categoryEntities.add(
                    CategoryEntity(
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
                categoryEntities.add(
                    CategoryEntity(
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
                categoryEntities.add(
                    CategoryEntity(
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
            database.categoryDao().insertCategories(categoryEntities)

            // 3. Sincronizar Canales en Vivo
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
                            database.channelDao().insertChannels(batch)
                            liveCount += batch.size
                            _syncState.value = SyncState.SyncingLive(liveCount)
                        }
                    )
                }
            }

            // 4. Sincronizar Películas VOD
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
                            database.movieDao().insertMovies(batch)
                            movieCount += batch.size
                            _syncState.value = SyncState.SyncingMovies(movieCount)
                        }
                    )
                }
            }

            // 5. Sincronizar Series
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
                            database.seriesDao().insertSeries(batch)
                            seriesCount += batch.size
                            _syncState.value = SyncState.SyncingSeries(seriesCount)
                        }
                    )
                }
            }

            // Completado con éxito
            _syncState.value = SyncState.Completed(
                channelsCount = liveCount,
                moviesCount = movieCount,
                seriesCount = seriesCount
            )
            Result.success(Unit)

        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Error de sincronización con el servidor IPTV"
            _syncState.value = SyncState.Error(errorMsg)
            Result.failure(e)
        }
    }
}
