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

    @Query("SELECT * FROM channels ORDER BY num ASC, name ASC LIMIT :limit")
    fun getFeaturedChannels(limit: Int = 30): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY num ASC, name ASC")
    fun getAllChannelsBySource(sourceId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY num ASC, name ASC, id ASC")
    fun getAllChannelsBySourcePaging(sourceId: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY num ASC, name ASC, id ASC")
    fun getPagingChannelsBySource(sourceId: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND categoryId = :categoryId ORDER BY num ASC, name ASC, id ASC")
    fun getPagingChannelsBySourceAndCategory(sourceId: String, categoryId: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND categoryId NOT IN (:hiddenCategoryIds) ORDER BY num ASC, name ASC, id ASC")
    fun getPagingChannelsExcludingCategories(sourceId: String, hiddenCategoryIds: List<String>): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY num ASC, name ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY num ASC, name ASC, id ASC")
    fun getAllChannelsPaging(): PagingSource<Int, ChannelEntity>


    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND streamId = :streamId LIMIT 1")
    suspend fun getChannelByStreamId(sourceId: String, streamId: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE streamId = :streamId LIMIT 1")
    suspend fun getChannelByStreamId(streamId: Int): ChannelEntity?

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE sourceId = :sourceId AND streamId = :streamId")
    suspend fun updateFavoriteStatus(sourceId: String, streamId: Int, isFavorite: Boolean)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun updateFavoriteStatus(streamId: Int, isFavorite: Boolean)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteChannelsBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun getChannelCount(sourceId: String): Int
}
