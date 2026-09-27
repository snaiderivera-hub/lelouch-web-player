package com.lelouch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    fun getContinueWatching(sourceId: String, limit: Int = 20): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentId = :contentId AND sourceId = :sourceId LIMIT 1")
    suspend fun getProgressForContent(contentId: String, sourceId: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE contentId = :contentId AND sourceId = :sourceId")
    suspend fun deleteProgress(contentId: String, sourceId: String)

    @Query("DELETE FROM watch_history WHERE sourceId = :sourceId")
    suspend fun clearHistory(sourceId: String)
}
