package com.lelouch.core.domain.repository

import com.lelouch.core.model.Category
import com.lelouch.core.model.VodMovie
import kotlinx.coroutines.flow.Flow

interface VodRepository {
    fun getCategories(sourceId: String): Flow<List<Category>>
    fun getMoviesForRail(categoryId: String, limit: Int = 20): Flow<List<VodMovie>>
    fun getRecentlyAddedMovies(limit: Int = 20): Flow<List<VodMovie>>
    fun getFavoriteMovies(): Flow<List<VodMovie>>
    fun getAllMovies(sourceId: String? = null): Flow<List<VodMovie>>
    suspend fun getMovieDetail(streamId: Int): VodMovie?
    suspend fun toggleFavorite(streamId: Int, isFavorite: Boolean)
    suspend fun syncMovies(sourceId: String, serverUrl: String, user: String, pass: String): Result<Unit>
}
