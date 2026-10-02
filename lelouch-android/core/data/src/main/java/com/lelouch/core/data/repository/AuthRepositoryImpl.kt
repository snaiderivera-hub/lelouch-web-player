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
        withContext(Dispatchers.IO) {
            try {
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
                val deleteUrl = if (sourceId.startsWith("custom_")) {
                    val cleanId = sourceId.removePrefix("custom_")
                    "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?id=eq.$cleanId"
                } else {
                    "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlists?id=eq.$sourceId"
                }
                val delReq = Request.Builder()
                    .url(deleteUrl)
                    .delete()
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer $supabaseKey")
                    .build()
                httpClient.newCall(delReq).execute()
            } catch (_: Exception) {}
        }
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

                val isM3u = url.contains("/api/playlist") || url.endsWith(".m3u") || url.endsWith(".m3u8") ||
                        serverUrl.contains("vercel.app") || (!url.contains("username=") && !url.contains("password=") && url.startsWith("http"))

                // Extraer token de URL si viene como /api/playlist/:token o ?token=:token
                val tokenFromUrl = when {
                    url.contains("/api/playlist/") -> url.substringAfter("/api/playlist/").substringBefore("?").trim()
                    url.contains("token=") -> url.substringAfter("token=").substringBefore("&").trim()
                    else -> null
                }

                // Normalizar URL para evitar errores 404 en Vercel
                val normalizedM3uUrl = if (url.contains("/api/playlist/") && !url.contains("?token=")) {
                    val tok = url.substringAfter("/api/playlist/").substringBefore("?").trim()
                    "https://lelouch-web-player.vercel.app/api/playlist?token=$tok"
                } else url

                val finalType = if (isM3u) SourceType.M3U else SourceType.XTREAM
                val finalServerUrl = if (isM3u && normalizedM3uUrl.isNotBlank()) normalizedM3uUrl else cleanServer.trimEnd('/')

                if (finalServerUrl.isNotBlank()) {
                    cloudSources.add(
                        SourceConfig(
                            id = id,
                            name = name,
                            serverUrl = finalServerUrl,
                            username = userMatch,
                            password = passMatch,
                            type = finalType,
                            isActive = isActive,
                            accessToken = tokenFromUrl
                        )
                    )
                }
            }

            // Sincronizar listas personalizadas (FASE 30 / FASE 32)
            // Consulta también playlist_access_tokens para obtener el token de acceso de cada playlist
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

                    // Obtener TODOS los tokens activos de una sola llamada
                    val tokensUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_access_tokens?select=playlist_id,token&enabled=eq.true&order=created_at.asc"
                    val tokensReq = Request.Builder()
                        .url(tokensUrl)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .build()
                    val tokensRes = httpClient.newCall(tokensReq).execute()
                    val tokenMap = mutableMapOf<String, String>()
                    if (tokensRes.isSuccessful) {
                        val tokensBody = tokensRes.body?.string() ?: ""
                        try {
                            val tokensArray = json.parseToJsonElement(tokensBody).jsonArray
                            for (t in tokensArray) {
                                val tObj = t.jsonObject
                                val pid = tObj["playlist_id"]?.jsonPrimitive?.content ?: continue
                                val tok = tObj["token"]?.jsonPrimitive?.content ?: continue
                                if (!tokenMap.containsKey(pid)) {
                                    tokenMap[pid] = tok
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    for (el in customJsonArray) {
                        val cObj = el.jsonObject
                        val cId = cObj["id"]?.jsonPrimitive?.content ?: continue
                        val cName = cObj["name"]?.jsonPrimitive?.content ?: "Mi Lista Personalizada"
                        val cIsActive = cObj["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        val cToken = tokenMap[cId]

                        val cleanName = if (cName.startsWith("⭐")) cName else "⭐ $cName"
                        val customUrl = if (!cToken.isNullOrBlank()) {
                            "https://lelouch-web-player.vercel.app/api/playlist?token=$cToken"
                        } else {
                            "https://lelouch-web-player.vercel.app/api/playlist?token=pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R"
                        }
                        cloudSources.add(
                            SourceConfig(
                                id = "custom_lelouch",
                                name = cleanName,
                                serverUrl = customUrl,
                                username = "",
                                password = "",
                                type = SourceType.M3U,
                                isActive = cIsActive,
                                accessToken = cToken ?: "pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R"
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            val currentSources = preferencesDataSource.allSources.first()
            val currentPasswordMap = currentSources.associate { (it.serverUrl + it.username) to it.password }
            val currentActiveId = currentSources.firstOrNull { it.isActive }?.id

            // ── CONSOLIDACIÓN ESTRICTA (FASE 32) ──
            // Colapsar cualquier entrada Vercel o Custom en EXACTAMENTE UNA lista personalizada limpia
            val isCustomSource = { s: SourceConfig ->
                s.serverUrl.contains("vercel.app") ||
                s.username.equals("LELOUCH", ignoreCase = true) ||
                s.name.contains("Personalizada", ignoreCase = true) ||
                s.name.contains("Mi Lista", ignoreCase = true) ||
                s.id.startsWith("custom_")
            }

            val customList = cloudSources.filter { isCustomSource(it) }
            val normalList = cloudSources.filterNot { isCustomSource(it) }

            val consolidatedSources = mutableListOf<SourceConfig>()

            if (customList.isNotEmpty()) {
                val bestToken = customList.mapNotNull { it.accessToken }.firstOrNull { it.isNotBlank() }
                    ?: "pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R"
                val canonicalCustomUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=$bestToken"
                val wasCustomActive = customList.any { it.id == currentActiveId || it.isActive }
                consolidatedSources.add(
                    SourceConfig(
                        id = "custom_lelouch",
                        name = "⭐ Mi Lista Personalizada LELOUCH",
                        serverUrl = canonicalCustomUrl,
                        username = "",
                        password = "",
                        type = SourceType.M3U,
                        isActive = wasCustomActive,
                        accessToken = bestToken
                    )
                )
            }

            // Desduplicar fuentes normales por (servidor + usuario) y restaurar contraseñas
            val seenKeys = mutableSetOf<String>()
            for (src in normalList) {
                val cleanUrl = src.serverUrl.trimEnd('/')
                val cleanUser = src.username.trim().lowercase()
                val key = "$cleanUrl|$cleanUser"
                if (!seenKeys.contains(key)) {
                    seenKeys.add(key)
                    val existingPass = currentPasswordMap[src.serverUrl + src.username]
                    val resolvedPass = if (src.password.isNotBlank()) src.password else (existingPass ?: "")
                    consolidatedSources.add(
                        src.copy(
                            password = resolvedPass,
                            isActive = (src.id == currentActiveId)
                        )
                    )
                }
            }

            // Si ninguna fuente está activa pero hay fuentes disponibles, activar la primera
            val finalSources = if (consolidatedSources.isNotEmpty() && consolidatedSources.none { it.isActive }) {
                consolidatedSources.mapIndexed { idx, s -> s.copy(isActive = idx == 0) }
            } else {
                consolidatedSources
            }

            preferencesDataSource.saveAllSources(finalSources)

            if (finalSources.isNotEmpty()) {
                val activeExists = finalSources.any { it.id == currentActiveId }
                if (!activeExists) {
                    preferencesDataSource.setActiveSource(finalSources.first().id)
                }
            }
            return@withContext finalSources
        } catch (e: Exception) {
            preferencesDataSource.allSources.first()
        }
    }

    override suspend fun logout() {
        preferencesDataSource.clearSession()
    }
}
