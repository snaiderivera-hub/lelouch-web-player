package com.lelouch.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

    // ==========================================
    // Consultas Legacy (Preservadas para compatibilidad)
    // ==========================================

    @Query("""
        SELECT movies.* FROM movies
        JOIN movies_fts ON movies.rowid = movies_fts.rowid
        WHERE movies_fts MATCH :query
        LIMIT :limit
    """)
    fun searchMoviesFts(query: String, limit: Int = 50): Flow<List<MovieEntity>>

    @Query("""
        SELECT channels.* FROM channels
        JOIN channels_fts ON channels.rowid = channels_fts.rowid
        WHERE channels_fts MATCH :query
        LIMIT :limit
    """)
    fun searchChannelsFts(query: String, limit: Int = 50): Flow<List<ChannelEntity>>

    @Query("""
        SELECT series.* FROM series
        JOIN series_fts ON series.rowid = series_fts.rowid
        WHERE series_fts MATCH :query
        LIMIT :limit
    """)
    fun searchSeriesFts(query: String, limit: Int = 50): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM movies WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    fun searchMoviesLike(query: String, limit: Int = 50): Flow<List<MovieEntity>>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    fun searchChannelsLike(query: String, limit: Int = 50): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM series WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    fun searchSeriesLike(query: String, limit: Int = 50): Flow<List<SeriesEntity>>

    // ==========================================
    // FASE CONTROLADA — P1 #3: Search Source-Aware & Bounded
    // ==========================================

    // --- 1. CHANNELS ---
    @Query("""
        SELECT channels.* FROM channels
        WHERE channels.sourceId = :sourceId
          AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH :ftsQuery)
        ORDER BY channels.num ASC, channels.name ASC, channels.id ASC
        LIMIT :limit
    """)
    suspend fun searchChannelsFts(sourceId: String, ftsQuery: String, limit: Int = 20): List<ChannelEntity>

    @Query("""
        SELECT channels.* FROM channels
        WHERE channels.sourceId = :sourceId
          AND channels.categoryId NOT IN (:hiddenCategoryIds)
          AND channels.rowid IN (SELECT docid FROM channels_fts WHERE channels_fts MATCH :ftsQuery)
        ORDER BY channels.num ASC, channels.name ASC, channels.id ASC
        LIMIT :limit
    """)
    suspend fun searchChannelsFtsExcludingCategories(
        sourceId: String,
        ftsQuery: String,
        hiddenCategoryIds: List<String>,
        limit: Int = 20
    ): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE sourceId = :sourceId
          AND (name LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%')
        ORDER BY num ASC, name ASC, id ASC
        LIMIT :limit
    """)
    suspend fun searchChannelsLike(
        sourceId: String,
        query: String,
        strippedQuery: String,
        limit: Int = 20
    ): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE sourceId = :sourceId
          AND categoryId NOT IN (:hiddenCategoryIds)
          AND (name LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%')
        ORDER BY num ASC, name ASC, id ASC
        LIMIT :limit
    """)
    suspend fun searchChannelsLikeExcludingCategories(
        sourceId: String,
        query: String,
        strippedQuery: String,
        hiddenCategoryIds: List<String>,
        limit: Int = 20
    ): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE sourceId = :sourceId
          AND (name LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%')
        ORDER BY num ASC, name ASC, id ASC
    """)
    fun searchChannelsPaging(
        sourceId: String,
        query: String,
        strippedQuery: String
    ): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE sourceId = :sourceId
          AND categoryId NOT IN (:hiddenCategoryIds)
          AND (name LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%')
        ORDER BY num ASC, name ASC, id ASC
    """)
    fun searchChannelsPagingExcludingCategories(
        sourceId: String,
        query: String,
        strippedQuery: String,
        hiddenCategoryIds: List<String>
    ): PagingSource<Int, ChannelEntity>

    // --- 2. MOVIES ---
    @Query("""
        SELECT movies.* FROM movies
        WHERE movies.sourceId = :sourceId
          AND movies.rowid IN (SELECT docid FROM movies_fts WHERE movies_fts MATCH :ftsQuery)
        ORDER BY movies.rating DESC, movies.name ASC, movies.id ASC
        LIMIT :limit
    """)
    suspend fun searchMoviesFts(sourceId: String, ftsQuery: String, limit: Int = 20): List<MovieEntity>

    @Query("""
        SELECT movies.* FROM movies
        WHERE movies.sourceId = :sourceId
          AND movies.categoryId NOT IN (:hiddenCategoryIds)
          AND movies.rowid IN (SELECT docid FROM movies_fts WHERE movies_fts MATCH :ftsQuery)
        ORDER BY movies.rating DESC, movies.name ASC, movies.id ASC
        LIMIT :limit
    """)
    suspend fun searchMoviesFtsExcludingCategories(
        sourceId: String,
        ftsQuery: String,
        hiddenCategoryIds: List<String>,
        limit: Int = 20
    ): List<MovieEntity>

    @Query("""
        SELECT * FROM movies
        WHERE sourceId = :sourceId
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
        LIMIT :limit
    """)
    suspend fun searchMoviesLike(
        sourceId: String,
        query: String,
        strippedQuery: String,
        limit: Int = 20
    ): List<MovieEntity>

    @Query("""
        SELECT * FROM movies
        WHERE sourceId = :sourceId
          AND categoryId NOT IN (:hiddenCategoryIds)
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
        LIMIT :limit
    """)
    suspend fun searchMoviesLikeExcludingCategories(
        sourceId: String,
        query: String,
        strippedQuery: String,
        hiddenCategoryIds: List<String>,
        limit: Int = 20
    ): List<MovieEntity>

    @Query("""
        SELECT * FROM movies
        WHERE sourceId = :sourceId
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
    """)
    fun searchMoviesPaging(
        sourceId: String,
        query: String,
        strippedQuery: String
    ): PagingSource<Int, MovieEntity>

    @Query("""
        SELECT * FROM movies
        WHERE sourceId = :sourceId
          AND categoryId NOT IN (:hiddenCategoryIds)
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
    """)
    fun searchMoviesPagingExcludingCategories(
        sourceId: String,
        query: String,
        strippedQuery: String,
        hiddenCategoryIds: List<String>
    ): PagingSource<Int, MovieEntity>

    // --- 3. SERIES ---
    @Query("""
        SELECT series.* FROM series
        WHERE series.sourceId = :sourceId
          AND series.rowid IN (SELECT docid FROM series_fts WHERE series_fts MATCH :ftsQuery)
        ORDER BY series.rating DESC, series.name ASC, series.id ASC
        LIMIT :limit
    """)
    suspend fun searchSeriesFts(sourceId: String, ftsQuery: String, limit: Int = 20): List<SeriesEntity>

    @Query("""
        SELECT series.* FROM series
        WHERE series.sourceId = :sourceId
          AND series.categoryId NOT IN (:hiddenCategoryIds)
          AND series.rowid IN (SELECT docid FROM series_fts WHERE series_fts MATCH :ftsQuery)
        ORDER BY series.rating DESC, series.name ASC, series.id ASC
        LIMIT :limit
    """)
    suspend fun searchSeriesFtsExcludingCategories(
        sourceId: String,
        ftsQuery: String,
        hiddenCategoryIds: List<String>,
        limit: Int = 20
    ): List<SeriesEntity>

    @Query("""
        SELECT * FROM series
        WHERE sourceId = :sourceId
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
        LIMIT :limit
    """)
    suspend fun searchSeriesLike(
        sourceId: String,
        query: String,
        strippedQuery: String,
        limit: Int = 20
    ): List<SeriesEntity>

    @Query("""
        SELECT * FROM series
        WHERE sourceId = :sourceId
          AND categoryId NOT IN (:hiddenCategoryIds)
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
        LIMIT :limit
    """)
    suspend fun searchSeriesLikeExcludingCategories(
        sourceId: String,
        query: String,
        strippedQuery: String,
        hiddenCategoryIds: List<String>,
        limit: Int = 20
    ): List<SeriesEntity>

    @Query("""
        SELECT * FROM series
        WHERE sourceId = :sourceId
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
    """)
    fun searchSeriesPaging(
        sourceId: String,
        query: String,
        strippedQuery: String
    ): PagingSource<Int, SeriesEntity>

    @Query("""
        SELECT * FROM series
        WHERE sourceId = :sourceId
          AND categoryId NOT IN (:hiddenCategoryIds)
          AND (name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR name LIKE '%' || :strippedQuery || '%' OR title LIKE '%' || :strippedQuery || '%')
        ORDER BY rating DESC, name ASC, id ASC
    """)
    fun searchSeriesPagingExcludingCategories(
        sourceId: String,
        query: String,
        strippedQuery: String,
        hiddenCategoryIds: List<String>
    ): PagingSource<Int, SeriesEntity>
}
