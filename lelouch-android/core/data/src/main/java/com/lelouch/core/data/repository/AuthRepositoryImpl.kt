package com.lelouch.core.data.repository

import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.domain.repository.AuthRepository
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import com.lelouch.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AuthRepositoryImpl(
    private val preferencesDataSource: UserPreferencesDataSource
) : AuthRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override fun getActiveSource(): Flow<SourceConfig?> {
        return preferencesDataSource.activeSource
    }

    override fun getAllSources(): Flow<List<SourceConfig>> {
        return preferencesDataSource.allSources
    }

    override suspend fun saveSource(source: SourceConfig) {
        preferencesDataSource.saveActiveSource(source)
    }

    override suspend fun activateSource(sourceId: String): SourceConfig? {
        return preferencesDataSource.setActiveSource(sourceId)
    }

    override suspend fun removeSource(sourceId: String) {
        preferencesDataSource.removeSource(sourceId)
    }

    override suspend fun validateXtream(
        serverUrl: String,
        user: String,
        pass: String,
        customName: String?
    ): Result<SourceConfig> = withContext(Dispatchers.IO) {
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

            val listName = customName.takeIf { !it.isNullOrBlank() } ?: "Xtream: $user (${serverUrl.replace("http://", "").replace("https://", "").take(16)})"

            val source = SourceConfig(
                id = "xtream_${user.hashCode()}_${serverUrl.hashCode()}",
                name = listName,
                serverUrl = serverUrl.trimEnd('/'),
                username = user.trim(),
                password = pass.trim(),
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

    override suspend fun syncCloudSources(): List<SourceConfig> = withContext(Dispatchers.IO) {
        try {
            val supabaseUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlists?select=*&order=updated_at.desc"
            val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"

            val request = Request.Builder()
                .url(supabaseUrl)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext preferencesDataSource.allSources.first()
            }

            val body = response.body?.string() ?: return@withContext preferencesDataSource.allSources.first()
            val jsonArray = json.parseToJsonElement(body).jsonArray

            val cloudSources = mutableListOf<SourceConfig>()
            for (element in jsonArray) {
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val name = obj["name"]?.jsonPrimitive?.content ?: "Lista IPTV"
                val url = obj["url"]?.jsonPrimitive?.content ?: ""
                val serverUrl = obj["server_url"]?.jsonPrimitive?.content ?: ""
                val username = obj["username"]?.jsonPrimitive?.content ?: ""
                val isActive = obj["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false

                // Extraer usuario y password de la URL si están presentes
                val userMatch = Regex("[?&]username=([^&]+)").find(url)?.groupValues?.get(1) ?: username
                val passMatch = Regex("[?&]password=([^&]+)").find(url)?.groupValues?.get(1) ?: ""

                val cleanServer = if (serverUrl.isNotBlank()) {
                    if (!serverUrl.startsWith("http")) "http://$serverUrl" else serverUrl
                } else {
                    val serverFromUrl = Regex("^(https?://[^/]+)").find(url)?.groupValues?.get(1) ?: ""
                    serverFromUrl
                }

                if (cleanServer.isNotBlank()) {
                    cloudSources.add(
                        SourceConfig(
                            id = id,
                            name = name,
                            serverUrl = cleanServer.trimEnd('/'),
                            username = userMatch,
                            password = passMatch,
                            type = SourceType.XTREAM,
                            isActive = isActive
                        )
                    )
                }
            }

            // Sincronizar listas personalizadas (FASE 30 / FASE 32)
            try {
                val customSupabaseUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?select=*&order=updated_at.desc"
                val customReq = Request.Builder()
                    .url(customSupabaseUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer $supabaseKey")
                    .build()
                val customRes = httpClient.newCall(customReq).execute()
                if (customRes.isSuccessful) {
                    val customBody = customRes.body?.string() ?: ""
                    val customJsonArray = json.parseToJsonElement(customBody).jsonArray
                    for (el in customJsonArray) {
                        val cObj = el.jsonObject
                        val cId = cObj["id"]?.jsonPrimitive?.content ?: continue
                        val cName = cObj["name"]?.jsonPrimitive?.content ?: "Mi Lista Personalizada"
                        val cIsActive = cObj["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        cloudSources.add(
                            SourceConfig(
                                id = "custom_$cId",
                                name = "⭐ $cName",
                                serverUrl = "https://lelouch-web-player.vercel.app/api/playlist",
                                username = "custom",
                                password = "",
                                type = SourceType.M3U,
                                isActive = cIsActive
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            if (cloudSources.isNotEmpty()) {
                val current = preferencesDataSource.allSources.first().toMutableList()
                cloudSources.forEach { cloud ->
                    val existingIdx = current.indexOfFirst { it.id == cloud.id || (it.serverUrl == cloud.serverUrl && it.username == cloud.username) }
                    if (existingIdx >= 0) {
                        current[existingIdx] = cloud.copy(
                            password = if (cloud.password.isNotBlank()) cloud.password else current[existingIdx].password
                        )
                    } else {
                        current.add(cloud)
                    }
                }
                preferencesDataSource.saveAllSources(current)
                return@withContext current
            }

            preferencesDataSource.allSources.first()
        } catch (e: Exception) {
            preferencesDataSource.allSources.first()
        }
    }

    override suspend fun logout() {
        preferencesDataSource.clearSession()
    }
}
