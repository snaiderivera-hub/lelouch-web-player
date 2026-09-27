package com.lelouch.core.domain.usecase

import com.lelouch.core.domain.repository.ChannelRepository
import com.lelouch.core.model.LiveStream
import kotlinx.coroutines.flow.Flow

class GetFeaturedChannelsUseCase(
    private val channelRepository: ChannelRepository
) {
    operator fun invoke(limit: Int = 30): Flow<List<LiveStream>> {
        return channelRepository.getFeaturedChannels(limit)
    }
}
