package com.lelouch.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lelouch.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY num ASC, name ASC")
    fun getChannelsByCategory(categoryId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY num ASC, name ASC")
    fun getChannelsByCategoryPaging(categoryId: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE streamId = :streamId LIMIT 1")
    suspend fun getChannelByStreamId(streamId: Int): ChannelEntity?

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun updateFavoriteStatus(streamId: Int, isFavorite: Boolean)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteChannelsBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun getChannelCount(sourceId: String): Int
}
