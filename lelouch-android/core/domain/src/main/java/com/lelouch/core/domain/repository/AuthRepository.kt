package com.lelouch.core.domain.repository

import com.lelouch.core.model.SourceConfig
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getActiveSource(): Flow<SourceConfig?>
    suspend fun saveSource(source: SourceConfig)
    suspend fun validateXtream(serverUrl: String, user: String, pass: String): Result<SourceConfig>
    suspend fun logout()
}
