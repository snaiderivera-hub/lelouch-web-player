package com.lelouch.core.domain.usecase

import com.lelouch.core.domain.repository.VodRepository
import com.lelouch.core.model.VodMovie
import kotlinx.coroutines.flow.Flow

class GetMoviesForRailUseCase(
    private val vodRepository: VodRepository
) {
    operator fun invoke(categoryId: String, limit: Int = 20): Flow<List<VodMovie>> {
        return vodRepository.getMoviesForRail(categoryId, limit)
    }

    fun getRecentlyAdded(limit: Int = 20): Flow<List<VodMovie>> {
        return vodRepository.getRecentlyAddedMovies(limit)
    }

    fun getFavorites(): Flow<List<VodMovie>> {
        return vodRepository.getFavoriteMovies()
    }
}
