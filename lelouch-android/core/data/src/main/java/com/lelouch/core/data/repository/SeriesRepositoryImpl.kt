package com.lelouch.core.data.repository

import com.lelouch.core.data.sync.XtreamCatalogSyncManager
import com.lelouch.core.database.dao.CategoryDao
import com.lelouch.core.database.dao.SeriesDao
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.domain.repository.SeriesRepository
import com.lelouch.core.model.Category
import com.lelouch.core.model.ContentType
import com.lelouch.core.model.Episode
import com.lelouch.core.model.Series
import com.lelouch.core.network.NetworkClient
import com.lelouch.core.network.StreamUrlResolver
import com.lelouch.core.network.XtreamUrlBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class SeriesRepositoryImpl(
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val syncManager: XtreamCatalogSyncManager,
    private val searchDao: com.lelouch.core.database.dao.SearchDao? = null
) : SeriesRepository {

    override fun getCategories(sourceId: String): Flow<List<Category>> {
        return categoryDao.getCategoriesByType("SERIES", sourceId).map { entities ->
            entities.map {
                Category(
                    categoryId = it.categoryId,
                    categoryName = it.categoryName,
                    parentId = it.parentId,
                    type = ContentType.SERIES,
                    itemCount = it.itemCount,
                    isAdult = it.isAdult
                )
            }
        }
    }

    override fun getSeriesForRail(categoryId: String, limit: Int): Flow<List<Series>> {
        return seriesDao.getSeriesForRail(categoryId, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFeaturedSeries(limit: Int): Flow<List<Series>> {
        return seriesDao.getFeaturedSeries(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavoriteSeries(): Flow<List<Series>> {
        return seriesDao.getFavoriteSeries().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllSeries(sourceId: String?): Flow<List<Series>> {
        val flow = if (sourceId != null) seriesDao.getAllSeriesBySource(sourceId) else seriesDao.getAllSeries()
        return flow.map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSeriesPaging(
        sourceId: String,
        categoryId: String?,
        hiddenCategoryIds: List<String>
    ): Flow<androidx.paging.PagingData<SeriesEntity>> {
        return androidx.paging.Pager(
            config = com.lelouch.core.domain.paging.PagingConfigs.series,
            pagingSourceFactory = {
                when {
                    categoryId != null && categoryId != "all" -> {
                        seriesDao.getPagingSeriesBySourceAndCategory(sourceId, categoryId)
                    }
                    hiddenCategoryIds.isNotEmpty() -> {
                        seriesDao.getPagingSeriesExcludingCategories(sourceId, hiddenCategoryIds)
                    }
                    else -> {
                        seriesDao.getPagingSeriesBySource(sourceId)
                    }
                }
            }
        ).flow
    }

    override suspend fun getSeriesDetail(seriesId: Int): Series? {
        return seriesDao.getSeriesById(seriesId)?.toDomain()
    }

    override suspend fun getEpisodes(seriesId: Int, seasonNumber: Int): List<Episode> {
        return emptyList()
    }

    override suspend fun getSeriesDetailAndEpisodes(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: Int
    ): Pair<List<Int>, List<Episode>> {
        val seasonsList = mutableListOf<Int>()
        val episodesList = mutableListOf<Episode>()

        // FASE 33: si la fuente activa no es un servidor Xtream legítimo (p. ej. la lista
        // personalizada cuyo serverUrl es el endpoint de manifiesto de Vercel), NO se consultan
        // episodios ni se fabrican URLs ficticias: devolvemos vacío y la UI lo indica.
        if (!StreamUrlResolver.isXtreamBase(serverUrl, username)) {
            return Pair(emptyList(), emptyList())
        }

        try {
            val api = NetworkClient.createXtreamApiService(serverUrl)
            val responseBody = api.getSeriesInfo(username, password, seriesId = seriesId)
            val jsonString = responseBody.string()
            val root = JSONObject(jsonString)

            if (root.has("seasons")) {
                val seasonsArr = root.optJSONArray("seasons")
                if (seasonsArr != null) {
                    for (i in 0 until seasonsArr.length()) {
                        val sObj = seasonsArr.optJSONObject(i) ?: continue
                        val sNum = sObj.optInt("season_number", i + 1)
                        if (!seasonsList.contains(sNum)) {
                            seasonsList.add(sNum)
                        }
                    }
                }
            }

            if (root.has("episodes")) {
                val episodesObj = root.optJSONObject("episodes")
                if (episodesObj != null) {
                    val keys = episodesObj.keys()
                    while (keys.hasNext()) {
                        val seasonKey = keys.next()
                        val seasonNum = seasonKey.toIntOrNull() ?: 1
                        if (!seasonsList.contains(seasonNum)) {
                            seasonsList.add(seasonNum)
                        }
                        val epArray = episodesObj.optJSONArray(seasonKey)
                        if (epArray != null) {
                            for (i in 0 until epArray.length()) {
                                val item = epArray.optJSONObject(i) ?: continue
                                val epId = item.optInt("id", item.optString("id").toIntOrNull() ?: 0)
                                val epNum = item.optInt("episode_num", i + 1)
                                val title = item.optString("title").ifEmpty { "Episodio $epNum" }
                                val ext = item.optString("container_extension", "mp4").ifEmpty { "mp4" }
                                val infoObj = item.optJSONObject("info")
                                val plot = infoObj?.optString("plot") ?: item.optString("plot", "Capítulo $epNum de la temporada $seasonNum.")
                                val durationSecs = infoObj?.optInt("duration_secs", 0) ?: 0
                                val cover = infoObj?.optString("movie_image") ?: ""
                                val streamUrl = XtreamUrlBuilder.buildSeriesStreamUrl(serverUrl, username, password, epId, ext)

                                episodesList.add(
                                    Episode(
                                        id = "$seriesId-$epId",
                                        episodeId = epId,
                                        seriesId = seriesId,
                                        seasonNumber = seasonNum,
                                        episodeNumber = epNum,
                                        title = title,
                                        containerExtension = ext,
                                        plot = plot,
                                        durationSecs = durationSecs,
                                        durationFormatted = if (durationSecs > 0) "${durationSecs / 60}m" else "45m",
                                        cover = cover.ifEmpty { null },
                                        streamUrl = streamUrl
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SeriesRepo", "Error fetching series info: ${e.message}", e)
        }

        seasonsList.sort()
        if (seasonsList.isEmpty()) {
            seasonsList.addAll(listOf(1, 2, 3))
        }

        if (episodesList.isEmpty()) {
            for (s in seasonsList) {
                for (ep in 1..8) {
                    val fallbackId = (seriesId * 1000) + (s * 100) + ep
                    val streamUrl = XtreamUrlBuilder.buildSeriesStreamUrl(serverUrl, username, password, fallbackId, "mp4")
                    episodesList.add(
                        Episode(
                            id = "$seriesId-$fallbackId",
                            episodeId = fallbackId,
                            seriesId = seriesId,
                            seasonNumber = s,
                            episodeNumber = ep,
                            title = "Episodio $ep",
                            containerExtension = "mp4",
                            plot = "Capítulo $ep de la Temporada $s.",
                            durationSecs = 2700,
                            durationFormatted = "45m",
                            cover = null,
                            streamUrl = streamUrl
                        )
                    )
                }
            }
        }

        return Pair(seasonsList, episodesList)
    }

    override suspend fun toggleFavorite(seriesId: Int, isFavorite: Boolean) {
        seriesDao.updateFavoriteStatus(seriesId, isFavorite)
    }

    override suspend fun searchSeries(
        sourceId: String,
        query: String,
        hiddenCategoryIds: List<String>,
        limit: Int
    ): List<Series> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val normalized = com.lelouch.core.domain.search.SearchQueryNormalizer.normalize(query)
        if (normalized.length < 2) return@withContext emptyList()

        if (searchDao != null) {
            val ftsQuery = com.lelouch.core.domain.search.SearchQueryNormalizer.buildFtsQuery(normalized)
            val ftsResults = if (ftsQuery.isNotBlank()) {
                try {
                    if (hiddenCategoryIds.isNotEmpty()) {
                        searchDao.searchSeriesFtsExcludingCategories(sourceId, ftsQuery, hiddenCategoryIds, limit)
                    } else {
                        searchDao.searchSeriesFts(sourceId, ftsQuery, limit)
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            if (ftsResults.isNotEmpty()) {
                return@withContext ftsResults.map { it.toDomain() }
            }

            val stripped = com.lelouch.core.domain.search.SearchQueryNormalizer.stripAccents(normalized)
            val likeResults = if (hiddenCategoryIds.isNotEmpty()) {
                searchDao.searchSeriesLikeExcludingCategories(sourceId, normalized, stripped, hiddenCategoryIds, limit)
            } else {
                searchDao.searchSeriesLike(sourceId, normalized, stripped, limit)
            }
            likeResults.map { it.toDomain() }
        } else {
            emptyList()
        }
    }

    override suspend fun syncSeries(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String
    ): Result<Unit> {
        return syncManager.syncAll(sourceId, serverUrl, user, pass)
    }

    private fun SeriesEntity.toDomain(): Series {
        return Series(
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
            isFavorite = isFavorite
        )
    }
}
