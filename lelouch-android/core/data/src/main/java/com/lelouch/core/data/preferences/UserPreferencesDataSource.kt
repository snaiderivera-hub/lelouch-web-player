package com.lelouch.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lelouch_user_prefs")

class UserPreferencesDataSource private constructor(
    private val context: Context?,
    private val customDataStore: DataStore<Preferences>?
) {
    constructor(context: Context) : this(context = context, customDataStore = null)

    constructor(customDataStore: DataStore<Preferences>) : this(context = null, customDataStore = customDataStore)

    private val dataStore: DataStore<Preferences>
        get() = customDataStore ?: context?.dataStore ?: error("Neither Context nor DataStore provided")


    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true 
        encodeDefaults = true 
    }

    private object PreferencesKeys {
        val SOURCE_ID = stringPreferencesKey("source_id")
        val SOURCE_NAME = stringPreferencesKey("source_name")
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val SOURCE_TYPE = stringPreferencesKey("source_type")
        val MAX_CONNECTIONS = intPreferencesKey("max_connections")
        val ACTIVE_CONNECTIONS = intPreferencesKey("active_connections")
        val EXPIRE_DATE = stringPreferencesKey("expire_date")
        val IS_TRIAL = booleanPreferencesKey("is_trial")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")

        // Multi-lista persistente
        val SAVED_SOURCES_JSON = stringPreferencesKey("saved_sources_json")
        val ACTIVE_SOURCE_ID = stringPreferencesKey("active_source_id")

        // Metadatos de sincronización atómica y frescura (P0 #5)
        val SYNC_METADATA_JSON = stringPreferencesKey("sync_metadata_json")
    }

    val defaultInitialSources = emptyList<SourceConfig>()

    /**
     * Deduplica en memoria el JSON crudo de fuentes.
     *
     * Cualquier entrada que parezca la lista personalizada (dominio Vercel, usuario LELOUCH,
     * nombre "Mi Lista"/"Personalizada" o id `custom_*`) se colapsa en UNA sola fuente
     * canónica `custom_lelouch`; el resto se deduplica por (servidor + usuario).
     *
     * Extraído a función para que [allSources] y [activeSource] compartan EXACTAMENTE el
     * mismo resultado: así el id que ve la UI, con el que se consulta Room y con el que se
     * llama a syncAll es siempre idéntico.
     */
    private fun deduplicatedSources(rawJson: String?, activeId: String?): List<SourceConfig> {
        val list = if (!rawJson.isNullOrBlank()) {
            try {
                json.decodeFromString<List<SourceConfig>>(rawJson)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val isCustom = { s: SourceConfig ->
            s.serverUrl.contains("vercel.app") ||
            s.username.equals("LELOUCH", ignoreCase = true) ||
            s.name.contains("Personalizada", ignoreCase = true) ||
            s.name.contains("Mi Lista", ignoreCase = true) ||
            s.id.startsWith("custom_")
        }

        val customItems = list.filter { isCustom(it) }
        val normalItems = list.filterNot { isCustom(it) }
        val deduplicated = mutableListOf<SourceConfig>()

        if (customItems.isNotEmpty()) {
            val best = customItems.find { it.accessToken?.isNotBlank() == true || it.serverUrl.contains("token=") || it.serverUrl.contains("/api/playlist/") } ?: customItems.first()
            val token = best.accessToken.takeIf { !it.isNullOrBlank() }
                ?: (when {
                    best.serverUrl.contains("/api/playlist/") -> best.serverUrl.substringAfter("/api/playlist/").substringBefore("?").trim()
                    best.serverUrl.contains("token=") -> best.serverUrl.substringAfter("token=").substringBefore("&").trim()
                    else -> "pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R"
                })
            val tokenUrl = "https://lelouch-web-player.vercel.app/api/playlist?token=$token"
            deduplicated.add(
                SourceConfig(
                    id = "custom_lelouch",
                    name = "⭐ Mi Lista Personalizada LELOUCH",
                    serverUrl = tokenUrl,
                    username = "",
                    password = "",
                    type = SourceType.M3U,
                    isActive = customItems.any { it.id == activeId || it.isActive },
                    accessToken = token
                )
            )
        }

        val seen = mutableSetOf<String>()
        for (src in normalItems) {
            val key = (src.serverUrl.trimEnd('/') + "|" + src.username.trim().lowercase())
            if (!seen.contains(key)) {
                seen.add(key)
                deduplicated.add(src)
            }
        }

        return deduplicated.map { source ->
            val isCurrentActive = if (activeId != null) {
                source.id == activeId
            } else {
                source.isActive
            }
            source.copy(isActive = isCurrentActive)
        }
    }

    val allSources: Flow<List<SourceConfig>> = dataStore.data.map { preferences ->
        val activeId = preferences[PreferencesKeys.ACTIVE_SOURCE_ID] ?: preferences[PreferencesKeys.SOURCE_ID]
        deduplicatedSources(preferences[PreferencesKeys.SAVED_SOURCES_JSON], activeId)
    }

    /**
     * FASE 33 — Fuente activa derivada del catálogo YA deduplicado.
     *
     * Antes releía el JSON crudo (`list.find { it.id == activeId } ?: firstOrNull()`), con lo
     * que podía devolver una fila duplicada que [allSources] ya había colapsado en
     * `custom_lelouch`. La pantalla consultaba entonces Room con un `sourceId` con el que
     * nunca se guardó nada -> 0 canales -> lista vacía sin ningún error visible.
     *
     * Derivarla de [allSources] garantiza que el id que ve la UI, el que se usa para
     * consultar Room y el que se pasa a syncAll sean siempre el mismo.
     */
    val activeSource: Flow<SourceConfig?> = dataStore.data.map { preferences ->
        val activeId = preferences[PreferencesKeys.ACTIVE_SOURCE_ID] ?: preferences[PreferencesKeys.SOURCE_ID]
        val list = deduplicatedSources(preferences[PreferencesKeys.SAVED_SOURCES_JSON], activeId)
        if (list.isEmpty()) null else (list.firstOrNull { it.id == activeId } ?: list.firstOrNull { it.isActive })
    }

    suspend fun saveActiveSource(source: SourceConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOURCE_ID] = source.id
            preferences[PreferencesKeys.SOURCE_NAME] = source.name
            preferences[PreferencesKeys.SERVER_URL] = source.serverUrl
            preferences[PreferencesKeys.USERNAME] = source.username
            preferences[PreferencesKeys.PASSWORD] = source.password
            preferences[PreferencesKeys.SOURCE_TYPE] = source.type.name
            preferences[PreferencesKeys.MAX_CONNECTIONS] = source.maxConnections
            preferences[PreferencesKeys.ACTIVE_CONNECTIONS] = source.activeConnections
            preferences[PreferencesKeys.EXPIRE_DATE] = source.expireDate ?: ""
            preferences[PreferencesKeys.IS_TRIAL] = source.isTrial
            preferences[PreferencesKeys.IS_LOGGED_IN] = true
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
            preferences[PreferencesKeys.ACTIVE_SOURCE_ID] = source.id

            // Upsert en la lista multi-cuentas
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val currentList = if (!rawJson.isNullOrBlank()) {
                try { json.decodeFromString<List<SourceConfig>>(rawJson) } catch (e: Exception) { defaultInitialSources }
            } else defaultInitialSources

            val updated = currentList.filter { it.id != source.id && it.serverUrl != source.serverUrl }.toMutableList()
            updated.add(0, source.copy(isActive = true))
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun setActiveSource(sourceId: String): SourceConfig? {
        var active: SourceConfig? = null
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val currentList = deduplicatedSources(rawJson, sourceId)

            val isCustomId = sourceId == "custom_lelouch" || sourceId.startsWith("custom_")
            val target = currentList.find { 
                it.id == sourceId || (isCustomId && (it.id == "custom_lelouch" || it.serverUrl.contains("vercel.app") || it.name.contains("Personalizada", ignoreCase = true)))
            }
            if (target != null) {
                preferences[PreferencesKeys.ACTIVE_SOURCE_ID] = target.id
                preferences[PreferencesKeys.SOURCE_ID] = target.id
                preferences[PreferencesKeys.SOURCE_NAME] = target.name
                preferences[PreferencesKeys.SERVER_URL] = target.serverUrl
                preferences[PreferencesKeys.USERNAME] = target.username
                preferences[PreferencesKeys.PASSWORD] = target.password
                preferences[PreferencesKeys.SOURCE_TYPE] = target.type.name
                preferences[PreferencesKeys.IS_LOGGED_IN] = true
                preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
                active = target.copy(isActive = true)
            }
        }
        return active
    }

    suspend fun removeSource(sourceId: String) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val currentList = if (!rawJson.isNullOrBlank()) {
                try { json.decodeFromString<List<SourceConfig>>(rawJson) } catch (e: Exception) { emptyList() }
            } else emptyList()

            val updated = currentList.filter { it.id != sourceId }
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(updated)

            if (preferences[PreferencesKeys.ACTIVE_SOURCE_ID] == sourceId) {
                val next = updated.firstOrNull()
                if (next != null) {
                    preferences[PreferencesKeys.ACTIVE_SOURCE_ID] = next.id
                    preferences[PreferencesKeys.SOURCE_ID] = next.id
                    preferences[PreferencesKeys.SOURCE_NAME] = next.name
                    preferences[PreferencesKeys.SERVER_URL] = next.serverUrl
                    preferences[PreferencesKeys.USERNAME] = next.username
                    preferences[PreferencesKeys.PASSWORD] = next.password
                } else {
                    preferences.remove(PreferencesKeys.ACTIVE_SOURCE_ID)
                    preferences.remove(PreferencesKeys.IS_LOGGED_IN)
                }
            }
        }
    }

    suspend fun saveAllSources(sources: List<SourceConfig>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(sources)
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] = false
            preferences.remove(PreferencesKeys.ACTIVE_SOURCE_ID)
        }
    }

    suspend fun getSyncMetadata(sourceId: String): SourceSyncMetadata? {
        val prefs = dataStore.data.first()
        val rawJson = prefs[PreferencesKeys.SYNC_METADATA_JSON] ?: return null
        return try {
            val map = json.decodeFromString<Map<String, SourceSyncMetadata>>(rawJson)
            map[sourceId]
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveSyncMetadata(metadata: SourceSyncMetadata) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SYNC_METADATA_JSON]
            val map = if (!rawJson.isNullOrBlank()) {
                try {
                    json.decodeFromString<Map<String, SourceSyncMetadata>>(rawJson).toMutableMap()
                } catch (_: Exception) {
                    mutableMapOf()
                }
            } else {
                mutableMapOf()
            }
            map[metadata.sourceId] = metadata
            preferences[PreferencesKeys.SYNC_METADATA_JSON] = json.encodeToString(map)
        }
    }
}
