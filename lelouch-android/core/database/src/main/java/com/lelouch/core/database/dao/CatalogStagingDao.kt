package com.lelouch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.CategoryStagingEntity
import com.lelouch.core.database.entity.ChannelStagingEntity
import com.lelouch.core.database.entity.MovieStagingEntity
import com.lelouch.core.database.entity.SeriesStagingEntity

@Dao
interface CatalogStagingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelsStaging(channels: List<ChannelStagingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoviesStaging(movies: List<MovieStagingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeriesStaging(series: List<SeriesStagingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoriesStaging(categories: List<CategoryStagingEntity>)

    @Query("SELECT COUNT(*) FROM channels_staging WHERE syncId = :syncId")
    suspend fun getStagingChannelCount(syncId: String): Int

    @Query("SELECT COUNT(*) FROM movies_staging WHERE syncId = :syncId")
    suspend fun getStagingMovieCount(syncId: String): Int

    @Query("SELECT COUNT(*) FROM series_staging WHERE syncId = :syncId")
    suspend fun getStagingSeriesCount(syncId: String): Int

    @Query("SELECT COUNT(*) FROM categories_staging WHERE syncId = :syncId")
    suspend fun getStagingCategoryCount(syncId: String): Int

    @Query("DELETE FROM channels_staging WHERE syncId = :syncId")
    suspend fun deleteStagingChannels(syncId: String)

    @Query("DELETE FROM movies_staging WHERE syncId = :syncId")
    suspend fun deleteStagingMovies(syncId: String)

    @Query("DELETE FROM series_staging WHERE syncId = :syncId")
    suspend fun deleteStagingSeries(syncId: String)

    @Query("DELETE FROM categories_staging WHERE syncId = :syncId")
    suspend fun deleteStagingCategories(syncId: String)

    @Query("DELETE FROM channels_staging WHERE sourceId = :sourceId AND syncId != :activeSyncId")
    suspend fun cleanupOrphanChannelsStaging(sourceId: String, activeSyncId: String = "")

    @Query("DELETE FROM movies_staging WHERE sourceId = :sourceId AND syncId != :activeSyncId")
    suspend fun cleanupOrphanMoviesStaging(sourceId: String, activeSyncId: String = "")

    @Query("DELETE FROM series_staging WHERE sourceId = :sourceId AND syncId != :activeSyncId")
    suspend fun cleanupOrphanSeriesStaging(sourceId: String, activeSyncId: String = "")

    @Query("DELETE FROM categories_staging WHERE sourceId = :sourceId AND syncId != :activeSyncId")
    suspend fun cleanupOrphanCategoriesStaging(sourceId: String, activeSyncId: String = "")

    @Query("""
        UPDATE channels_staging 
        SET isFavorite = 1 
        WHERE syncId = :syncId 
          AND sourceId = :sourceId 
          AND (
              id IN (SELECT id FROM channels WHERE sourceId = :sourceId AND isFavorite = 1)
              OR streamId IN (SELECT streamId FROM channels WHERE sourceId = :sourceId AND isFavorite = 1)
              OR streamId IN (SELECT CAST(contentId AS INTEGER) FROM favorites WHERE sourceId = :sourceId AND contentType = 'LIVE')
          )
    """)
    suspend fun preserveChannelFavorites(syncId: String, sourceId: String)

    @Query("""
        UPDATE movies_staging 
        SET isFavorite = 1 
        WHERE syncId = :syncId 
          AND sourceId = :sourceId 
          AND (
              id IN (SELECT id FROM movies WHERE sourceId = :sourceId AND isFavorite = 1)
              OR streamId IN (SELECT streamId FROM movies WHERE sourceId = :sourceId AND isFavorite = 1)
              OR streamId IN (SELECT CAST(contentId AS INTEGER) FROM favorites WHERE sourceId = :sourceId AND contentType = 'VOD')
          )
    """)
    suspend fun preserveMovieFavorites(syncId: String, sourceId: String)

    @Query("""
        UPDATE series_staging 
        SET isFavorite = 1 
        WHERE syncId = :syncId 
          AND sourceId = :sourceId 
          AND (
              id IN (SELECT id FROM series WHERE sourceId = :sourceId AND isFavorite = 1)
              OR seriesId IN (SELECT seriesId FROM series WHERE sourceId = :sourceId AND isFavorite = 1)
              OR seriesId IN (SELECT CAST(contentId AS INTEGER) FROM favorites WHERE sourceId = :sourceId AND contentType = 'SERIES')
          )
    """)
    suspend fun preserveSeriesFavorites(syncId: String, sourceId: String)

    @Query("""
        INSERT INTO channels (id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId)
        SELECT id, streamId, num, name, streamType, streamIcon, categoryId, categoryName, epgChannelId, isAdult, isFavorite, streamUrl, containerExtension, sourceId
        FROM channels_staging WHERE syncId = :syncId AND sourceId = :sourceId
    """)
    suspend fun copyChannelsFromStaging(syncId: String, sourceId: String)

    @Query("""
        INSERT INTO movies (`id`, `streamId`, `num`, `name`, `title`, `year`, `streamIcon`, `backdropPath`, `rating`, `rating5based`, `added`, `categoryId`, `categoryName`, `containerExtension`, `plot`, `cast`, `director`, `genre`, `durationSecs`, `streamUrl`, `isFavorite`, `sourceId`)
        SELECT `id`, `streamId`, `num`, `name`, `title`, `year`, `streamIcon`, `backdropPath`, `rating`, `rating5based`, `added`, `categoryId`, `categoryName`, `containerExtension`, `plot`, `cast`, `director`, `genre`, `durationSecs`, `streamUrl`, `isFavorite`, `sourceId`
        FROM movies_staging WHERE syncId = :syncId AND sourceId = :sourceId
    """)
    suspend fun copyMoviesFromStaging(syncId: String, sourceId: String)

    @Query("""
        INSERT INTO series (`id`, `seriesId`, `num`, `name`, `title`, `cover`, `backdropPath`, `plot`, `cast`, `director`, `genre`, `releaseDate`, `rating`, `rating5based`, `categoryId`, `categoryName`, `isFavorite`, `sourceId`)
        SELECT `id`, `seriesId`, `num`, `name`, `title`, `cover`, `backdropPath`, `plot`, `cast`, `director`, `genre`, `releaseDate`, `rating`, `rating5based`, `categoryId`, `categoryName`, `isFavorite`, `sourceId`
        FROM series_staging WHERE syncId = :syncId AND sourceId = :sourceId
    """)
    suspend fun copySeriesFromStaging(syncId: String, sourceId: String)

    @Query("""
        INSERT INTO categories (id, categoryId, categoryName, parentId, type, itemCount, isAdult, sourceId)
        SELECT id, categoryId, categoryName, parentId, type, itemCount, isAdult, sourceId
        FROM categories_staging WHERE syncId = :syncId AND sourceId = :sourceId
    """)
    suspend fun copyCategoriesFromStaging(syncId: String, sourceId: String)
}
