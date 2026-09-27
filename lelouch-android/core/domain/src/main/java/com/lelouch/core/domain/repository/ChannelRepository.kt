package com.lelouch.core.domain.repository

import com.lelouch.core.model.Category
import com.lelouch.core.model.LiveStream
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun getCategories(sourceId: String): Flow<List<Category>>
    fun getChannelsByCategory(categoryId: String): Flow<List<LiveStream>>
    fun getFavoriteChannels(): Flow<List<LiveStream>>
    suspend fun toggleFavorite(streamId: Int, isFavorite: Boolean)
    suspend fun syncChannels(sourceId: String, serverUrl: String, user: String, pass: String): Result<Unit>
}
