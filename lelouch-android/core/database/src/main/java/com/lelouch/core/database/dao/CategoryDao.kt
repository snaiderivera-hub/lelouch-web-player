package com.lelouch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE type = :type AND sourceId = :sourceId ORDER BY categoryName ASC")
    fun getCategoriesByType(type: String, sourceId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND isAdult = 0 AND sourceId = :sourceId ORDER BY categoryName ASC")
    fun getSafeCategoriesByType(type: String, sourceId: String): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE sourceId = :sourceId AND type = :type")
    suspend fun deleteCategoriesByType(sourceId: String, type: String)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteCategoriesBySource(sourceId: String)
}
