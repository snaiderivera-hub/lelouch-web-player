package com.lelouch.core.data.repository

import com.lelouch.core.data.sync.XtreamCatalogSyncManager
import com.lelouch.core.database.dao.CategoryDao
import com.lelouch.core.database.dao.MovieDao
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.domain.repository.VodRepository
import com.lelouch.core.model.Category
import com.lelouch.core.model.ContentType
import com.lelouch.core.model.VodMovie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VodRepositoryImpl(
    private val movieDao: MovieDao,
    private val categoryDao: CategoryDao,
    private val syncManager: XtreamCatalogSyncManager
) : VodRepository {

    override fun getCategories(sourceId: String): Flow<List<Category>> {
        return categoryDao.getCategoriesByType("VOD", sourceId).map { entities ->
            entities.map {
                Category(
                    categoryId = it.categoryId,
                    categoryName = it.categoryName,
                    parentId = it.parentId,
                    type = ContentType.VOD,
                    itemCount = it.itemCount,
                    isAdult = it.isAdult
                )
            }
        }
    }

    override fun getMoviesForRail(categoryId: String, limit: Int): Flow<List<VodMovie>> {
        return movieDao.getMoviesForRail(categoryId, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecentlyAddedMovies(limit: Int): Flow<List<VodMovie>> {
        return movieDao.getRecentlyAddedMovies(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavoriteMovies(): Flow<List<VodMovie>> {
        return movieDao.getFavoriteMovies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllMovies(sourceId: String?): Flow<List<VodMovie>> {
        val flow = if (sourceId != null) movieDao.getAllMoviesBySource(sourceId) else movieDao.getAllMovies()
        return flow.map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMovieDetail(streamId: Int): VodMovie? {
        return movieDao.getMovieByStreamId(streamId)?.toDomain()
    }

    override suspend fun toggleFavorite(streamId: Int, isFavorite: Boolean) {
        movieDao.updateFavoriteStatus(streamId, isFavorite)
    }

    override suspend fun syncMovies(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String
    ): Result<Unit> {
        return syncManager.syncAll(sourceId, serverUrl, user, pass)
    }

    private fun MovieEntity.toDomain(): VodMovie {
        return VodMovie(
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
            isFavorite = isFavorite
        )
    }
}
