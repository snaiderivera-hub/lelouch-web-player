package com.lelouch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.SourceHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceHealthDao {

    @Query("SELECT * FROM source_health ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SourceHealthEntity>>

    @Query("SELECT * FROM source_health WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(sourceId: String): SourceHealthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SourceHealthEntity)
}
