package com.lelouch.core.data.sync

import com.lelouch.core.database.LelouchDatabase
import com.lelouch.core.database.entity.CategoryEntity
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.network.NetworkClient
import com.lelouch.core.network.XtreamUrlBuilder
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
            val liveStreams = api.getLiveStreams(user, pass)
            val channelEntities = liveStreams.map { dto ->
                ChannelEntity(
                    id = "$sourceId-live-${dto.streamId}",
                    streamId = dto.streamId,
                    num = dto.num,
                    name = dto.name,
                    streamType = dto.streamType,
                    streamIcon = dto.streamIcon,
                    categoryId = dto.categoryId,
                    epgChannelId = dto.epgChannelId,
                    isAdult = dto.name.contains("adult", ignoreCase = true) || dto.name.contains("+18"),
                    isFavorite = false,
                    streamUrl = XtreamUrlBuilder.buildLiveStreamUrl(serverUrl, user, pass, dto.streamId, "ts"),
                    sourceId = sourceId
                )
            }
            database.channelDao().insertChannels(channelEntities)
            _syncState.value = SyncState.SyncingLive(channelEntities.size)

            // 4. Sincronizar Películas VOD
            _syncState.value = SyncState.SyncingMovies(0)
            val vodStreams = api.getVodStreams(user, pass)
            val movieEntities = vodStreams.map { dto ->
                MovieEntity(
                    id = "$sourceId-vod-${dto.streamId}",
                    streamId = dto.streamId,
                    num = dto.num,
                    name = dto.name,
                    title = dto.title ?: dto.name,
                    year = dto.year,
                    streamIcon = dto.streamIcon,
                    rating = dto.rating?.toDoubleOrNull(),
                    rating5based = dto.rating5based,
                    added = dto.added,
                    categoryId = dto.categoryId,
                    containerExtension = dto.containerExtension,
                    streamUrl = XtreamUrlBuilder.buildVodStreamUrl(serverUrl, user, pass, dto.streamId, dto.containerExtension),
                    isFavorite = false,
                    sourceId = sourceId
                )
            }
            database.movieDao().insertMovies(movieEntities)
            _syncState.value = SyncState.SyncingMovies(movieEntities.size)

            // 5. Sincronizar Series
            _syncState.value = SyncState.SyncingSeries(0)
            val seriesList = api.getSeries(user, pass)
            val seriesEntities = seriesList.map { dto ->
                SeriesEntity(
                    id = "$sourceId-series-${dto.seriesId}",
                    seriesId = dto.seriesId,
                    num = dto.num,
                    name = dto.name,
                    title = dto.title ?: dto.name,
                    cover = dto.cover,
                    plot = dto.plot,
                    cast = dto.cast,
                    director = dto.director,
                    genre = dto.genre,
                    releaseDate = dto.releaseDate,
                    rating = dto.rating?.toDoubleOrNull(),
                    rating5based = dto.rating5based,
                    categoryId = dto.categoryId,
                    isFavorite = false,
                    sourceId = sourceId
                )
            }
            database.seriesDao().insertSeries(seriesEntities)
            _syncState.value = SyncState.SyncingSeries(seriesEntities.size)

            // Completado con éxito
            _syncState.value = SyncState.Completed(
                channelsCount = channelEntities.size,
                moviesCount = movieEntities.size,
                seriesCount = seriesEntities.size
            )
            Result.success(Unit)

        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Error de sincronización con el servidor IPTV"
            _syncState.value = SyncState.Error(errorMsg)
            Result.failure(e)
        }
    }
}
