package com.lelouch.core.data.repository

import com.lelouch.core.data.sync.XtreamCatalogSyncManager
import com.lelouch.core.database.dao.CategoryDao
import com.lelouch.core.database.dao.ChannelDao
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.domain.repository.ChannelRepository
import com.lelouch.core.model.Category
import com.lelouch.core.model.ContentType
import com.lelouch.core.model.LiveStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChannelRepositoryImpl(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val syncManager: XtreamCatalogSyncManager
) : ChannelRepository {

    override fun getCategories(sourceId: String): Flow<List<Category>> {
        return categoryDao.getCategoriesByType("LIVE", sourceId).map { entities ->
            entities.map {
                Category(
                    categoryId = it.categoryId,
                    categoryName = it.categoryName,
                    parentId = it.parentId,
                    type = ContentType.LIVE,
                    itemCount = it.itemCount,
                    isAdult = it.isAdult
                )
            }
        }
    }

    override fun getChannelsByCategory(categoryId: String): Flow<List<LiveStream>> {
        return channelDao.getChannelsByCategory(categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavoriteChannels(): Flow<List<LiveStream>> {
        return channelDao.getFavoriteChannels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFeaturedChannels(limit: Int): Flow<List<LiveStream>> {
        return channelDao.getFeaturedChannels(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllChannels(sourceId: String?): Flow<List<LiveStream>> {
        val flow = if (sourceId != null) {
            channelDao.getAllChannelsBySource(sourceId)
        } else {
            channelDao.getAllChannels()
        }
        return flow.map { entities ->
            entities.map { it.toDomain() }
        }
    }


    override suspend fun toggleFavorite(streamId: Int, isFavorite: Boolean) {
        channelDao.updateFavoriteStatus(streamId, isFavorite)
    }

    override suspend fun syncChannels(
        sourceId: String,
        serverUrl: String,
        user: String,
        pass: String
    ): Result<Unit> {
        return syncManager.syncAll(sourceId, serverUrl, user, pass)
    }

    private fun ChannelEntity.toDomain(): LiveStream {
        return LiveStream(
            id = id,
            streamId = streamId,
            num = num,
            name = name,
            streamType = streamType,
            streamIcon = streamIcon,
            categoryId = categoryId,
            categoryName = categoryName,
            epgChannelId = epgChannelId,
            isAdult = isAdult,
            isFavorite = isFavorite,
            streamUrl = streamUrl,
            containerExtension = containerExtension
        )
    }
}
