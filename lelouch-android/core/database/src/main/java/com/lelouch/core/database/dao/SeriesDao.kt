package com.lelouch.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(seriesList: List<SeriesEntity>)

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY rating DESC LIMIT :limit")
    fun getSeriesForRail(categoryId: String, limit: Int = 20): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY rating DESC")
    fun getSeriesByCategoryPaging(categoryId: String): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteSeries(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getSeriesById(seriesId: Int): SeriesEntity?

    @Query("UPDATE series SET isFavorite = :isFavorite WHERE seriesId = :seriesId")
    suspend fun updateFavoriteStatus(seriesId: Int, isFavorite: Boolean)

    @Query("DELETE FROM series WHERE sourceId = :sourceId")
    suspend fun deleteSeriesBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM series WHERE sourceId = :sourceId")
    suspend fun getSeriesCount(sourceId: String): Int
}
