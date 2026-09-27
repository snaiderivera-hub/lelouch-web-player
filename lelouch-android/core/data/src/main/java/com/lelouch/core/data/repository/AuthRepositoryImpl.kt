package com.lelouch.core.data.repository

import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.domain.repository.AuthRepository
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import com.lelouch.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val preferencesDataSource: UserPreferencesDataSource
) : AuthRepository {

    override fun getActiveSource(): Flow<SourceConfig?> {
        return preferencesDataSource.activeSource
    }

    override suspend fun saveSource(source: SourceConfig) {
        preferencesDataSource.saveActiveSource(source)
    }

    override suspend fun validateXtream(serverUrl: String, user: String, pass: String): Result<SourceConfig> =
        withContext(Dispatchers.IO) {
            try {
                val api = NetworkClient.createXtreamApiService(serverUrl)
                val response = api.authenticate(user, pass)
                val userInfo = response.userInfo ?: return@withContext Result.failure(
                    IllegalArgumentException("Respuesta inválida del servidor IPTV")
                )

                if (userInfo.status != "Active") {
                    return@withContext Result.failure(
                        IllegalStateException("La cuenta no está activa (Estado: ${userInfo.status})")
                    )
                }

                val activeCons = userInfo.activeCons.toIntOrNull() ?: 0
                val maxCons = userInfo.maxConnections.toIntOrNull() ?: 1
                if (activeCons >= maxCons && maxCons > 0) {
                    return@withContext Result.failure(
                        IllegalStateException("Límite de conexiones activas alcanzado ($activeCons/$maxCons)")
                    )
                }

                val source = SourceConfig(
                    id = "xtream_${user.hashCode()}",
                    name = "Xtream: $user",
                    serverUrl = serverUrl,
                    username = user,
                    password = pass,
                    type = SourceType.XTREAM,
                    maxConnections = maxCons,
                    activeConnections = activeCons,
                    expireDate = userInfo.expDate,
                    isTrial = userInfo.isTrial == "1",
                    isActive = true
                )

                saveSource(source)
                Result.success(source)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun logout() {
        preferencesDataSource.clearSession()
    }
}
