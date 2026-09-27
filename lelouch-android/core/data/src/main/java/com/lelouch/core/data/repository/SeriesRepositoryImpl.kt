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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SeriesRepositoryImpl(
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val syncManager: XtreamCatalogSyncManager
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

    override fun getFavoriteSeries(): Flow<List<Series>> {
        return seriesDao.getFavoriteSeries().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSeriesDetail(seriesId: Int): Series? {
        return seriesDao.getSeriesById(seriesId)?.toDomain()
    }

    override suspend fun getEpisodes(seriesId: Int, seasonNumber: Int): List<Episode> {
        // En Sprint 5 se conectara al endpoint get_series_info con cache local
        return emptyList()
    }

    override suspend fun toggleFavorite(seriesId: Int, isFavorite: Boolean) {
        seriesDao.updateFavoriteStatus(seriesId, isFavorite)
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
