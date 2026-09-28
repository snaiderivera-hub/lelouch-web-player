package com.lelouch.core.data.sync

import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.database.entity.CategoryEntity
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.network.NetworkClient
import com.lelouch.core.network.XtreamUrlBuilder
import com.lelouch.core.network.XtreamStreamingParser
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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

    suspend fun syncAll(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
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
