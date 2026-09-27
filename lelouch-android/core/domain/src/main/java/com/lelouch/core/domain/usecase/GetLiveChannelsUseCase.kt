package com.lelouch.core.domain.usecase

import com.lelouch.core.domain.repository.ChannelRepository
import com.lelouch.core.model.LiveStream
import kotlinx.coroutines.flow.Flow

class GetLiveChannelsUseCase(
    private val channelRepository: ChannelRepository
) {
    operator fun invoke(categoryId: String): Flow<List<LiveStream>> {
        return channelRepository.getChannelsByCategory(categoryId)
    }

    fun getFavorites(): Flow<List<LiveStream>> {
        return channelRepository.getFavoriteChannels()
    }
}
