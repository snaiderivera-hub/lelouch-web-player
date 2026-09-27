package com.lelouch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

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

    // Búsqueda por aproximación con LIKE como fallback rápido
    @Query("SELECT * FROM movies WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    fun searchMoviesLike(query: String, limit: Int = 50): Flow<List<MovieEntity>>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    fun searchChannelsLike(query: String, limit: Int = 50): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM series WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    fun searchSeriesLike(query: String, limit: Int = 50): Flow<List<SeriesEntity>>
}
