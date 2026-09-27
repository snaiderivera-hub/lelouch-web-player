package com.lelouch.core.domain.repository

import com.lelouch.core.model.Category
import com.lelouch.core.model.Episode
import com.lelouch.core.model.Series
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {
    fun getCategories(sourceId: String): Flow<List<Category>>
    fun getSeriesForRail(categoryId: String, limit: Int = 20): Flow<List<Series>>
    fun getFeaturedSeries(limit: Int = 30): Flow<List<Series>>
    fun getFavoriteSeries(): Flow<List<Series>>

    suspend fun getSeriesDetail(seriesId: Int): Series?
    suspend fun getEpisodes(seriesId: Int, seasonNumber: Int): List<Episode>
    suspend fun toggleFavorite(seriesId: Int, isFavorite: Boolean)
    suspend fun syncSeries(sourceId: String, serverUrl: String, user: String, pass: String): Result<Unit>
}
