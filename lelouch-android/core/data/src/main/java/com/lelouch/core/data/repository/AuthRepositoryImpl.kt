package com.lelouch.core.data.repository

import com.lelouch.core.data.preferences.UserPreferencesDataSource
import com.lelouch.core.data.preferences.isRetiredCustomSource
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AuthRepositoryImpl(
    private val preferencesDataSource: UserPreferencesDataSource,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val supabaseBaseUrl: String = "https://rotupbdeljgfddywryhk.supabase.co"
) : AuthRepository {

    override var isLastSyncFromCache: Boolean = false
        private set

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
        val source = preferencesDataSource.allSources.first().firstOrNull { it.id == sourceId }
        preferencesDataSource.removeSource(sourceId)
        // La lista personalizada retirada ya no tiene identidad en la nube (su id local no es
        // un UUID). No se emite ningún DELETE: solo se purga el dispositivo.
        if (isRetiredCustomSource(sourceId, "")) return
        withContext(Dispatchers.IO) {
            try {
                val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
                // Las filas de `playlists` usan un UUID, pero el dispositivo conserva el id
                // local del catálogo. Se localiza la fila por identidad, no por id.
                val filter = when {
                    source == null -> "id=eq.${encode(sourceId)}"
                    source.type == SourceType.M3U || source.username.isBlank() ->
                        "url=eq.${encode(source.serverUrl)}"
                    else ->
                        "server_url=eq.${encode(source.serverUrl.trimEnd('/'))}&username=eq.${encode(source.username.trim())}"
                }
                val delReq = Request.Builder()
                    .url("$supabaseBaseUrl/rest/v1/playlists?$filter")
                    .delete()
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer $supabaseKey")
                    .build()
                httpClient.newCall(delReq).execute().close()

                // Si el sourceId también es un UUID de Supabase, asegurar borrado directo por id
                if (Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(sourceId)) {
                    val delByIdReq = Request.Builder()
                        .url("$supabaseBaseUrl/rest/v1/playlists?id=eq.${encode(sourceId)}")
                        .delete()
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .build()
                    httpClient.newCall(delByIdReq).execute().close()
                }
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
            activateSource(source.id)
            saveToCloudPlaylists(source)
            Result.success(source)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addSourceFromUrl(
        rawUrl: String,
        customName: String?
    ): Result<SourceConfig> = withContext(Dispatchers.IO) {
        try {
            val parsed = com.lelouch.core.data.util.SmartUrlParser.parse(rawUrl, customName)
            if (parsed.type == SourceType.XTREAM) {
                return@withContext validateXtream(parsed.serverUrl, parsed.username, parsed.password, parsed.suggestedName)
            } else {
                val source = SourceConfig(
                    id = "m3u_${System.currentTimeMillis()}",
                    name = parsed.suggestedName,
                    serverUrl = parsed.serverUrl,
                    username = parsed.username,
                    password = parsed.password,
                    type = SourceType.M3U,
                    isActive = true,
                    accessToken = parsed.token
                )
                saveSource(source)
                activateSource(source.id)
                saveToCloudPlaylists(source)
                return@withContext Result.success(source)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveToCloudPlaylists(source: SourceConfig) {
        try {
            val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
            val cleanBase = source.serverUrl.trimEnd('/')
            val fullUrl = if (source.type == SourceType.XTREAM && source.username.isNotBlank()) {
                "$cleanBase/get.php?username=${encode(source.username)}&password=${encode(source.password)}&type=m3u_plus&output=ts"
            } else {
                source.serverUrl
            }
            val isoExp = source.expireDate?.trim()?.let { raw ->
                val secs = raw.toLongOrNull()
                if (secs != null && secs > 1000000L) {
                    java.time.Instant.ofEpochSecond(secs).toString()
                } else if (raw.contains("T")) {
                    raw
                } else {
                    null
                }
            }
            val expJson = if (isoExp != null) """"expires_at":"$isoExp",""" else ""
            val payload = """{"name":"${source.name.replace("\"", "\\\"")}","url":"${fullUrl.replace("\"", "\\\"")}","server_url":"${cleanBase.replace("\"", "\\\"")}","username":"${source.username.replace("\"", "\\\"")}","password":"${source.password.replace("\"", "\\\"")}",""" + expJson + """"is_active":true}"""
            val req = Request.Builder()
                .url("$supabaseBaseUrl/rest/v1/playlists?on_conflict=url")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .build()
            httpClient.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    override suspend fun syncCloudSources(): List<SourceConfig> = withContext(Dispatchers.IO) {
        // Las fuentes locales se leen DESPUÉS de la red (no al entrar): así una lista agregada
        // mientras el bootstrap está en vuelo no se pierde al reconciliar con una foto vieja.
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
                        val passwordCol = obj["password"]?.jsonPrimitive?.content ?: ""
                        val isActive = obj["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false

                        // Extraer usuario y password de la URL o de las columnas de Supabase
                        val userFromUrl = Regex("[?&]username=([^&]+)").find(url)?.groupValues?.get(1)
                        val passFromUrl = Regex("[?&]password=([^&]+)").find(url)?.groupValues?.get(1)
                        val userMatch = when {
                            !userFromUrl.isNullOrBlank() -> userFromUrl
                            username.isNotBlank() && !username.contains("*") -> username
                            else -> username
                        }
                        val passMatch = when {
                            !passFromUrl.isNullOrBlank() -> passFromUrl
                            passwordCol.isNotBlank() && passwordCol != "••••••••" -> passwordCol
                            else -> ""
                        }

                        val cleanServer = if (serverUrl.isNotBlank()) {
                            if (!serverUrl.startsWith("http")) "http://$serverUrl" else serverUrl
                        } else {
                            Regex("^(https?://[^/]+)").find(url)?.groupValues?.get(1) ?: ""
                        }

                        val isVercelOrManifest = url.contains("/api/playlist") || serverUrl.contains("vercel.app")
                        val hasXtreamCreds = userMatch.isNotBlank() && (passMatch.isNotBlank() || serverUrl.isNotBlank())
                        val isM3u = isVercelOrManifest || (!hasXtreamCreds && (
                            url.substringBefore('?').endsWith(".m3u", ignoreCase = true) ||
                            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
                            (!url.contains("username=") && !url.contains("password=") && url.startsWith("http"))
                        ))

                        val tokenFromUrl = when {
                            url.contains("/api/playlist/") -> url.substringAfter("/api/playlist/").substringBefore("?").trim()
                            url.contains("token=") -> url.substringAfter("token=").substringBefore("&").trim()
                            else -> null
                        }

                        val finalType = if (isM3u) SourceType.M3U else SourceType.XTREAM
                        val finalServerUrl = if (isM3u && url.isNotBlank()) url else cleanServer.trimEnd('/')

                        // FASE 35: una fila legacy de la lista personalizada retirada no debe
                        // resucitar la fuente en el dispositivo.
                        if (isRetiredCustomSource(id, finalServerUrl)) {
                            android.util.Log.d("AuthRepository", "SOURCE_SYNC_SKIPPED_RETIRED: $id")
                            continue
                        }

                        val expireDateCloud = try { obj["expires_at"]?.jsonPrimitive?.content } catch (_: Exception) { null }
                        val maxConsCloud = try { obj["max_connections"]?.jsonPrimitive?.content?.toIntOrNull() } catch (_: Exception) { null } ?: 1
                        val activeConsCloud = try { obj["active_connections"]?.jsonPrimitive?.content?.toIntOrNull() } catch (_: Exception) { null } ?: 0

                        if (finalServerUrl.isNotBlank()) {
                            cloudSources.add(
                                SourceConfig(
                                    id = id,
                                    name = name,
                                    serverUrl = finalServerUrl,
                                    username = userMatch,
                                    password = passMatch,
                                    type = finalType,
                                    maxConnections = maxConsCloud,
                                    activeConnections = activeConsCloud,
                                    expireDate = expireDateCloud,
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

        // ── 2. Manejo de Errores vs Lista Vacía vs Éxito ──
        if (!atLeastOneEndpointSuccessful) {
            val currentSources = preferencesDataSource.allSources.first()
            isLastSyncFromCache = true
            if (currentSources.isNotEmpty()) {
                // TEST G: ya existe source local, red falla -> conservar source local usable
                android.util.Log.w("AuthRepository", "SOURCE_SYNC_USING_LOCAL_CACHE: Error de conexión a la nube; preservando caché local (${currentSources.size} fuentes)")
                return@withContext currentSources
            } else {
                // TEST D: la nube falla y no hay fuentes locales -> ERROR_LOADING_SOURCES
                android.util.Log.e("AuthRepository", "SOURCE_SYNC_FAILED: No se pudo conectar a la nube y no hay caché local")
                throw java.io.IOException("ERROR_LOADING_SOURCES: Fallo de red al conectar con fuentes en la nube")
            }
        }
        isLastSyncFromCache = false

        if (cloudSources.isEmpty()) {
            // FASE 35: la nube puede responder 200 sin filas utilizables (por ejemplo, solo la
            // lista personalizada retirada). Vaciar las fuentes locales aquí borraba las listas
            // Xtream del usuario y dejaba la pantalla sin nada. Se preservan las locales.
            val currentSources = preferencesDataSource.allSources.first()
            if (currentSources.isEmpty()) {
                android.util.Log.d("AuthRepository", "SOURCE_SYNC_EMPTY: sin fuentes locales ni remotas")
                preferencesDataSource.saveAllSources(emptyList())
                return@withContext emptyList()
            }
            android.util.Log.d("AuthRepository", "SOURCE_SYNC_EMPTY: la nube no tiene fuentes utilizables; preservando ${currentSources.size} fuentes locales")
            return@withContext currentSources
        }

        // ── 3. Reconciliación por identidad (servidor + usuario) ──
        // Las filas de `playlists` traen ids UUID, mientras que Room indexa los catálogos por
        // el id local (`xtream_...`/`m3u_...`). Reutilizar el id local evita que un sync de la
        // nube deje huérfano el catálogo ya descargado.
        val currentSources = preferencesDataSource.allSources.first()
        val localByKey = currentSources.associateBy { sourceKey(it.serverUrl, it.username) }
        val currentActiveId = currentSources.firstOrNull { it.isActive }?.id
        val consolidatedSources = mutableListOf<SourceConfig>()
        val seenKeys = mutableSetOf<String>()

        for (src in cloudSources) {
            if (isRetiredCustomSource(src.id, src.serverUrl)) continue
            val key = sourceKey(src.serverUrl, src.username)
            if (!seenKeys.add(key)) continue

            val localMatch = localByKey[key]
            val resolvedPass = if (src.password.isNotBlank()) src.password else (localMatch?.password ?: "")
            val resolvedId = localMatch?.id ?: src.id
            val resolvedExpire = if (!src.expireDate.isNullOrBlank() && src.expireDate != "null") {
                src.expireDate
            } else {
                localMatch?.expireDate
            }
            val resolvedMaxCons = if (src.maxConnections > 1) src.maxConnections else (localMatch?.maxConnections ?: 1)
            val resolvedActiveCons = if (src.activeConnections > 0) src.activeConnections else (localMatch?.activeConnections ?: 0)
            val isSourceActive = if (currentActiveId != null) {
                resolvedId == currentActiveId
            } else {
                src.isActive
            }
            consolidatedSources.add(
                src.copy(
                    id = resolvedId,
                    password = resolvedPass,
                    expireDate = resolvedExpire,
                    maxConnections = resolvedMaxCons,
                    activeConnections = resolvedActiveCons,
                    isTrial = localMatch?.isTrial ?: src.isTrial,
                    isActive = isSourceActive
                )
            )
        }

        // La nube es un espejo parcial: las altas manuales Xtream nunca se subieron a
        // `playlists` y desaparecían en el siguiente arranque. Se conservan las locales que la
        // nube no menciona.
        for (local in currentSources) {
            if (isRetiredCustomSource(local.id, local.serverUrl)) continue
            val key = sourceKey(local.serverUrl, local.username)
            if (seenKeys.add(key)) {
                val isLocalActive = local.id == currentActiveId ||
                    (currentActiveId == null && local.isActive)
                consolidatedSources.add(local.copy(isActive = isLocalActive))
            }
        }

        // Si ninguna fuente quedó activa, activar la primera disponible.
        val finalSources = if (consolidatedSources.isNotEmpty() && consolidatedSources.none { it.isActive }) {
            consolidatedSources.mapIndexed { idx, s -> s.copy(isActive = idx == 0) }
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

    override suspend fun refreshAllSourcesStatus(): List<SourceConfig> = withContext(Dispatchers.IO) {
        val currentSources = preferencesDataSource.allSources.first()
        if (currentSources.isEmpty()) return@withContext emptyList()

        val updatedSources = currentSources.map { src ->
            if (src.type == SourceType.XTREAM && src.serverUrl.isNotBlank() && src.username.isNotBlank() && src.password.isNotBlank()) {
                try {
                    val cleanBase = src.serverUrl.trimEnd('/')
                    val apiService = NetworkClient.createXtreamApiService(cleanBase, httpClient)
                    val response = apiService.authenticate(src.username, src.password)
                    val userInfo = response.userInfo
                    if (userInfo != null) {
                        val maxCons = userInfo.maxConnections.toIntOrNull() ?: src.maxConnections
                        val activeCons = userInfo.activeCons.toIntOrNull() ?: src.activeConnections
                        val expDate = userInfo.expDate?.takeIf { it.isNotBlank() && it != "null" } ?: src.expireDate
                        val isTrial = userInfo.isTrial == "1"
                        src.copy(
                            maxConnections = maxCons,
                            activeConnections = activeCons,
                            expireDate = expDate,
                            isTrial = isTrial
                        )
                    } else {
                        src
                    }
                } catch (_: Exception) {
                    src
                }
            } else {
                src
            }
        }

        preferencesDataSource.saveAllSources(updatedSources)
        val activeUpdated = updatedSources.firstOrNull { it.isActive }
        if (activeUpdated != null) {
            preferencesDataSource.saveActiveSource(activeUpdated)
        }
        return@withContext updatedSources
    }

    override suspend fun logout() {
        preferencesDataSource.clearSession()
    }

    /** Identidad de una fuente para reconciliar nube y dispositivo sin depender del id. */
    private fun sourceKey(serverUrl: String, username: String): String =
        serverUrl.trimEnd('/') + "|" + username.trim().lowercase()

    /** Codificación de un valor para un filtro PostgREST (`eq.<valor>`). */
    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
