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
    private val preferencesDataSource: UserPreferencesDataSource,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val supabaseBaseUrl: String = "https://rotupbdeljgfddywryhk.supabase.co"
) : AuthRepository {

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
                    "$supabaseBaseUrl/rest/v1/custom_playlists?id=eq.$cleanId"
                } else {
                    "$supabaseBaseUrl/rest/v1/playlists?id=eq.$sourceId"
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
        val currentSources = preferencesDataSource.allSources.first()
        val currentPasswordMap = currentSources.associate { (it.serverUrl + it.username) to it.password }
        val currentActiveId = currentSources.firstOrNull { it.isActive }?.id

        val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
        val cloudSources = mutableListOf<SourceConfig>()
        var atLeastOneEndpointSuccessful = false

        // ── 1. Fuentes LEGACY (playlists) — Opcional ──
        try {
            val legacyUrl = "$supabaseBaseUrl/rest/v1/playlists?select=*&order=updated_at.desc"
            val legacyReq = Request.Builder()
                .url(legacyUrl)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()

            val legacyRes = httpClient.newCall(legacyReq).execute()
            if (legacyRes.isSuccessful) {
                atLeastOneEndpointSuccessful = true
                val body = legacyRes.body?.string() ?: ""
                try {
                    val jsonArray = json.parseToJsonElement(body).jsonArray
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
                            Regex("^(https?://[^/]+)").find(url)?.groupValues?.get(1) ?: ""
                        }

                        val isM3u = url.contains("/api/playlist") || url.endsWith(".m3u") || url.endsWith(".m3u8") ||
                                serverUrl.contains("vercel.app") || (!url.contains("username=") && !url.contains("password=") && url.startsWith("http"))

                        val tokenFromUrl = when {
                            url.contains("/api/playlist/") -> url.substringAfter("/api/playlist/").substringBefore("?").trim()
                            url.contains("token=") -> url.substringAfter("token=").substringBefore("&").trim()
                            else -> null
                        }

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
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "SOURCE_SYNC_LEGACY_FAILED: Parse error")
                }
            } else {
                android.util.Log.w("AuthRepository", "SOURCE_SYNC_LEGACY_FAILED: HTTP ${legacyRes.code}")
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "SOURCE_SYNC_LEGACY_FAILED: ${e.message}")
        }

        // ── 2. Fuentes ACTUALES AUTORIZADAS (custom_playlists) ──
        val revokedPlaylistIds = mutableSetOf<String>()
        try {
            val customSupabaseUrl = "$supabaseBaseUrl/rest/v1/custom_playlists?select=*&order=updated_at.desc"
            val customReq = Request.Builder()
                .url(customSupabaseUrl)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()
            val customRes = httpClient.newCall(customReq).execute()
            if (customRes.isSuccessful) {
                atLeastOneEndpointSuccessful = true
                val customBody = customRes.body?.string() ?: ""
                try {
                    val customJsonArray = json.parseToJsonElement(customBody).jsonArray

                    // Consulta de tokens activos y revocados
                    val validTokenMap = mutableMapOf<String, String>()
                    try {
                        val tokensUrl = "$supabaseBaseUrl/rest/v1/playlist_access_tokens?select=*&order=created_at.asc"
                        val tokensReq = Request.Builder()
                            .url(tokensUrl)
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer $supabaseKey")
                            .build()
                        val tokensRes = httpClient.newCall(tokensReq).execute()
                        if (tokensRes.isSuccessful) {
                            val tokensBody = tokensRes.body?.string() ?: ""
                            val tokensArray = json.parseToJsonElement(tokensBody).jsonArray
                            for (t in tokensArray) {
                                val tObj = t.jsonObject
                                val pid = tObj["playlist_id"]?.jsonPrimitive?.content ?: continue
                                val isTokenActive = tObj["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: true
                                val isTokenEnabled = tObj["enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true
                                if (!isTokenActive || !isTokenEnabled) {
                                    revokedPlaylistIds.add(pid)
                                    continue
                                }
                                val tok = tObj["token"]?.jsonPrimitive?.content
                                    ?: tObj["token_preview"]?.jsonPrimitive?.content
                                    ?: continue
                                if (!validTokenMap.containsKey(pid)) {
                                    validTokenMap[pid] = tok
                                }
                            }
                        }
                    } catch (te: Exception) {
                        android.util.Log.w("AuthRepository", "Token fetch note: ${te.message}")
                    }

                    var addedCustom = 0
                    for (el in customJsonArray) {
                        val cObj = el.jsonObject
                        val cId = cObj["id"]?.jsonPrimitive?.content ?: continue
                        val cName = cObj["name"]?.jsonPrimitive?.content ?: "Mi Lista Personalizada"
                        val cIsEnabled = cObj["enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true
                        if (!cIsEnabled) continue // Playlist deshabilitada por backend

                        val isRevoked = revokedPlaylistIds.contains(cId) && !validTokenMap.containsKey(cId)
                        val cIsActive = if (isRevoked) false else (cObj["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false)
                        val cToken = if (isRevoked) null else validTokenMap[cId]

                        val cleanName = if (cName.startsWith("⭐")) cName else "⭐ $cName"
                        val customUrl = if (!cToken.isNullOrBlank()) {
                            "https://lelouch-web-player.vercel.app/api/playlist?token=$cToken"
                        } else if (!isRevoked) {
                            "https://lelouch-web-player.vercel.app/api/playlist?token=pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R"
                        } else {
                            ""
                        }

                        if (customUrl.isNotBlank()) {
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
                            addedCustom++
                        } else if (isRevoked) {
                            // Se registra fuente revocada como inactiva (TEST E)
                            cloudSources.add(
                                SourceConfig(
                                    id = "custom_lelouch",
                                    name = cleanName,
                                    serverUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=revoked",
                                    username = "",
                                    password = "",
                                    type = SourceType.M3U,
                                    isActive = false,
                                    accessToken = null
                                )
                            )
                        }
                    }
                    android.util.Log.d("AuthRepository", "SOURCE_SYNC_CUSTOM_OK: $addedCustom fuentes agregadas")
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "SOURCE_SYNC_CUSTOM_FAILED: Parse error")
                }
            } else {
                android.util.Log.w("AuthRepository", "SOURCE_SYNC_CUSTOM_FAILED: HTTP ${customRes.code}")
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "SOURCE_SYNC_CUSTOM_FAILED: ${e.message}")
        }

        // ── 3. Manejo de Errores vs Lista Vacía vs Éxito ──
        if (!atLeastOneEndpointSuccessful) {
            if (currentSources.isNotEmpty()) {
                // TEST G: ya existe source local, red falla -> conservar source local usable
                android.util.Log.w("AuthRepository", "SOURCE_SYNC_USING_LOCAL_CACHE: Error de conexión a la nube; preservando caché local (${currentSources.size} fuentes)")
                return@withContext currentSources
            } else {
                // TEST D: custom endpoint timeout/error sin fuentes locales -> ERROR_LOADING_SOURCES
                android.util.Log.e("AuthRepository", "SOURCE_SYNC_CUSTOM_FAILED: No se pudo conectar a la nube y no hay caché local")
                throw java.io.IOException("ERROR_LOADING_SOURCES: Fallo de red al conectar con fuentes en la nube")
            }
        }

        if (cloudSources.isEmpty()) {
            // TEST C: la nube respondió correctamente pero 0 fuentes -> SUCCESS_EMPTY
            android.util.Log.d("AuthRepository", "SOURCE_SYNC_EMPTY: La nube respondió pero no hay fuentes disponibles")
            if (currentSources.isNotEmpty()) {
                android.util.Log.d("AuthRepository", "SOURCE_SYNC_USING_LOCAL_CACHE: Conservando fuentes locales existentes")
                return@withContext currentSources
            } else {
                preferencesDataSource.saveAllSources(emptyList())
                return@withContext emptyList()
            }
        }

        // ── 4. CONSOLIDACIÓN ESTRICTA (FASE 32) ──
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
            val validCustomTokens = customList.mapNotNull { it.accessToken }.filter { it.isNotBlank() && it != "revoked" }
            val hasRevocation = customList.any { it.serverUrl.contains("token=revoked") || it.accessToken == null }
            val bestToken = validCustomTokens.firstOrNull() ?: if (!hasRevocation) "pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R" else null

            val wasCustomActive = if (bestToken == null) false else customList.any { it.id == currentActiveId || it.isActive }
            val canonicalCustomUrl = if (bestToken != null) {
                "https://lelouch-web-player.vercel.app/api/playlist?token=$bestToken"
            } else {
                "https://lelouch-web-player.vercel.app/api/playlist?token=revoked"
            }

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

        // Si ninguna fuente está activa pero hay fuentes disponibles, activar la primera VÁLIDA (TEST E & TEST F)
        val finalSources = if (consolidatedSources.isNotEmpty() && consolidatedSources.none { it.isActive }) {
            val firstValidIdx = consolidatedSources.indexOfFirst { s ->
                !(isCustomSource(s) && (s.accessToken == null || s.serverUrl.contains("token=revoked")))
            }
            if (firstValidIdx >= 0) {
                consolidatedSources.mapIndexed { idx, s -> s.copy(isActive = idx == firstValidIdx) }
            } else {
                consolidatedSources
            }
        } else {
            consolidatedSources
        }

        preferencesDataSource.saveAllSources(finalSources)

        if (finalSources.isNotEmpty()) {
            val activeSource = finalSources.firstOrNull { it.isActive }
            if (activeSource != null) {
                preferencesDataSource.setActiveSource(activeSource.id)
            }
        }
        return@withContext finalSources
    }

    override suspend fun logout() {
        preferencesDataSource.clearSession()
    }
}
