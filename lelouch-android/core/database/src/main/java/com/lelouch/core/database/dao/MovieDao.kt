package com.lelouch.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.MovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY rating DESC, added DESC LIMIT :limit")
    fun getMoviesForRail(categoryId: String, limit: Int = 20): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY rating DESC, added DESC")
    fun getMoviesByCategoryPaging(categoryId: String): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId ORDER BY name ASC, id ASC")
    fun getPagingMoviesBySource(sourceId: String): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND categoryId = :categoryId ORDER BY name ASC, id ASC")
    fun getPagingMoviesBySourceAndCategory(sourceId: String, categoryId: String): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND categoryId NOT IN (:hiddenCategoryIds) ORDER BY name ASC, id ASC")
    fun getPagingMoviesExcludingCategories(sourceId: String, hiddenCategoryIds: List<String>): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies ORDER BY added DESC LIMIT :limit")
    fun getRecentlyAddedMovies(limit: Int = 20): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE rating >= 7.5 ORDER BY rating DESC LIMIT :limit")
    fun getTopRatedMovies(limit: Int = 20): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE isFavorite = 1 ORDER BY added DESC")
    fun getFavoriteMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND streamId = :streamId LIMIT 1")
    suspend fun getMovieByStreamId(sourceId: String, streamId: Int): MovieEntity?

    @Query("SELECT * FROM movies WHERE streamId = :streamId LIMIT 1")
    suspend fun getMovieByStreamId(streamId: Int): MovieEntity?

    @Query("UPDATE movies SET isFavorite = :isFavorite WHERE sourceId = :sourceId AND streamId = :streamId")
    suspend fun updateFavoriteStatus(sourceId: String, streamId: Int, isFavorite: Boolean)

    @Query("UPDATE movies SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun updateFavoriteStatus(streamId: Int, isFavorite: Boolean)

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId ORDER BY name ASC")
    fun getAllMoviesBySource(sourceId: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY name ASC")
    fun getAllMovies(): Flow<List<MovieEntity>>

    @Query("DELETE FROM movies WHERE sourceId = :sourceId")
    suspend fun deleteMoviesBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM movies WHERE sourceId = :sourceId")
    suspend fun getMovieCount(sourceId: String): Int
}
