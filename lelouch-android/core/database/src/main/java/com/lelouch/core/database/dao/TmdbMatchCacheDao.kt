package com.lelouch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.TmdbMatchCacheEntity

@Dao
interface TmdbMatchCacheDao {

    @Query("SELECT * FROM tmdb_match_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): TmdbMatchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TmdbMatchCacheEntity)

    @Query("DELETE FROM tmdb_match_cache WHERE updatedAt < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}
