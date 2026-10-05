package com.lelouch.core.domain.repository

import com.lelouch.core.model.Category
import com.lelouch.core.model.LiveStream
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData
import com.lelouch.core.database.entity.ChannelEntity

interface ChannelRepository {
    fun getCategories(sourceId: String): Flow<List<Category>>
    fun getChannelsByCategory(categoryId: String): Flow<List<LiveStream>>
    fun getFavoriteChannels(): Flow<List<LiveStream>>
    fun getFeaturedChannels(limit: Int = 30): Flow<List<LiveStream>>
    fun getAllChannels(sourceId: String? = null): Flow<List<LiveStream>>
    fun getLiveChannelsPaging(
        sourceId: String,
        categoryId: String? = null,
        hiddenCategoryIds: List<String> = emptyList()
    ): Flow<PagingData<ChannelEntity>>
    suspend fun toggleFavorite(streamId: Int, isFavorite: Boolean)
    suspend fun toggleFavorite(sourceId: String, streamId: Int, isFavorite: Boolean)
    suspend fun searchChannels(
        sourceId: String,
        query: String,
        hiddenCategoryIds: List<String> = emptyList(),
        limit: Int = 20
    ): List<LiveStream>
    suspend fun syncChannels(sourceId: String, serverUrl: String, user: String, pass: String): Result<Unit>
}
