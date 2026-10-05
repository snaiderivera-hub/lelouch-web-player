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
    private val syncManager: XtreamCatalogSyncManager,
    private val searchDao: com.lelouch.core.database.dao.SearchDao? = null
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

    override fun getMoviesPaging(
        sourceId: String,
        categoryId: String?,
        hiddenCategoryIds: List<String>
    ): Flow<androidx.paging.PagingData<MovieEntity>> {
        return androidx.paging.Pager(
            config = com.lelouch.core.domain.paging.PagingConfigs.movies,
            pagingSourceFactory = {
                when {
                    categoryId != null && categoryId != "all" -> {
                        movieDao.getPagingMoviesBySourceAndCategory(sourceId, categoryId)
                    }
                    hiddenCategoryIds.isNotEmpty() -> {
                        movieDao.getPagingMoviesExcludingCategories(sourceId, hiddenCategoryIds)
                    }
                    else -> {
                        movieDao.getPagingMoviesBySource(sourceId)
                    }
                }
            }
        ).flow
    }

    override suspend fun getMovieDetail(streamId: Int): VodMovie? {
        return movieDao.getMovieByStreamId(streamId)?.toDomain()
    }

    override suspend fun toggleFavorite(streamId: Int, isFavorite: Boolean) {
        movieDao.updateFavoriteStatus(streamId, isFavorite)
    }

    override suspend fun searchMovies(
        sourceId: String,
        query: String,
        hiddenCategoryIds: List<String>,
        limit: Int
    ): List<VodMovie> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val normalized = com.lelouch.core.domain.search.SearchQueryNormalizer.normalize(query)
        if (normalized.length < 2) return@withContext emptyList()

        if (searchDao != null) {
            val ftsQuery = com.lelouch.core.domain.search.SearchQueryNormalizer.buildFtsQuery(normalized)
            val ftsResults = if (ftsQuery.isNotBlank()) {
                try {
                    if (hiddenCategoryIds.isNotEmpty()) {
                        searchDao.searchMoviesFtsExcludingCategories(sourceId, ftsQuery, hiddenCategoryIds, limit)
                    } else {
                        searchDao.searchMoviesFts(sourceId, ftsQuery, limit)
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
                searchDao.searchMoviesLikeExcludingCategories(sourceId, normalized, stripped, hiddenCategoryIds, limit)
            } else {
                searchDao.searchMoviesLike(sourceId, normalized, stripped, limit)
            }
            likeResults.map { it.toDomain() }
        } else {
            emptyList()
        }
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
