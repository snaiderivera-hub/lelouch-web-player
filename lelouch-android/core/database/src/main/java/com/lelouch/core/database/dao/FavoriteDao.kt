package com.lelouch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :contentId AND contentType = :contentType AND sourceId = :sourceId")
    suspend fun removeFavorite(contentId: String, contentType: String, sourceId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId AND contentType = :contentType AND sourceId = :sourceId)")
    fun isFavorite(contentId: String, contentType: String, sourceId: String): Flow<Boolean>

    @Query("SELECT * FROM favorites WHERE contentType = :contentType AND sourceId = :sourceId ORDER BY addedAt DESC")
    fun getFavoritesByType(contentType: String, sourceId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE sourceId = :sourceId ORDER BY addedAt DESC")
    fun getAllFavorites(sourceId: String): Flow<List<FavoriteEntity>>
}
