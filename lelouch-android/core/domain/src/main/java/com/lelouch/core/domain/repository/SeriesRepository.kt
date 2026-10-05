package com.lelouch.core.domain.repository

import androidx.paging.PagingData
import com.lelouch.core.database.entity.SeriesEntity
import com.lelouch.core.model.Category
import com.lelouch.core.model.Episode
import com.lelouch.core.model.Series
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {
    fun getCategories(sourceId: String): Flow<List<Category>>
    fun getSeriesForRail(categoryId: String, limit: Int = 20): Flow<List<Series>>
    fun getFeaturedSeries(limit: Int = 30): Flow<List<Series>>
    fun getFavoriteSeries(): Flow<List<Series>>
    fun getAllSeries(sourceId: String? = null): Flow<List<Series>>
    fun getSeriesPaging(
        sourceId: String,
        categoryId: String? = null,
        hiddenCategoryIds: List<String> = emptyList()
    ): Flow<PagingData<SeriesEntity>>

    suspend fun getSeriesDetail(seriesId: Int): Series?
    suspend fun getEpisodes(seriesId: Int, seasonNumber: Int): List<Episode>
    suspend fun getSeriesDetailAndEpisodes(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: Int
    ): Pair<List<Int>, List<Episode>>
    suspend fun toggleFavorite(seriesId: Int, isFavorite: Boolean)
    suspend fun searchSeries(
        sourceId: String,
        query: String,
        hiddenCategoryIds: List<String> = emptyList(),
        limit: Int = 20
    ): List<Series>
    suspend fun syncSeries(sourceId: String, serverUrl: String, user: String, pass: String): Result<Unit>
}
