package com.lelouch.core.domain.repository

import com.lelouch.core.model.SourceConfig
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getActiveSource(): Flow<SourceConfig?>
    fun getAllSources(): Flow<List<SourceConfig>>
    suspend fun saveSource(source: SourceConfig)
    suspend fun activateSource(sourceId: String): SourceConfig?
    suspend fun removeSource(sourceId: String)
    suspend fun validateXtream(serverUrl: String, user: String, pass: String, customName: String? = null): Result<SourceConfig>
    suspend fun addSourceFromUrl(rawUrl: String, customName: String? = null): Result<SourceConfig>
    suspend fun syncCloudSources(): List<SourceConfig>
    suspend fun refreshAllSourcesStatus(): List<SourceConfig> = emptyList()
    suspend fun logout()
    val isLastSyncFromCache: Boolean get() = false
}
