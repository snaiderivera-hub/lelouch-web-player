package com.lelouch.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lelouch.core.model.SourceConfig
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
     * Filtra y deduplica en memoria el JSON crudo de fuentes.
     *
     * FASE 35 (1.0.97): la lista personalizada alojada en Supabase quedó retirada. Sus filas
     * heredadas (`custom_lelouch`, cualquier `custom_*` o el manifiesto de Lelouch) se descartan
     * aquí, que es el único punto de lectura de fuentes. Ya no se sintetiza ni se colapsa nada:
     * el resto se deduplica por (servidor + usuario) conservando la primera entrada.
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

        val deduplicated = mutableListOf<SourceConfig>()
        val seen = mutableSetOf<String>()
        for (src in list) {
            if (isRetiredCustomSource(src.id, src.serverUrl)) continue
            val key = (src.serverUrl.trimEnd('/') + "|" + src.username.trim().lowercase())
            if (seen.add(key)) {
                deduplicated.add(src)
            }
        }

        // FASE 35: el puntero activo puede apuntar a una fila que ya no existe (por ejemplo la
        // lista retirada de la que era activa). Sin este fallback todas las fuentes quedarían
        // con isActive=false y la app mostraría «sin listas activas» aunque hubiera listas.
        val effectiveActiveId = when {
            activeId == null -> null
            deduplicated.any { it.id == activeId } -> activeId
            else -> deduplicated.firstOrNull()?.id
        }

        return deduplicated.map { source ->
            val isCurrentActive = if (effectiveActiveId != null) {
                source.id == effectiveActiveId
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

    /** Único punto que escribe el puntero activo y sus claves derivadas. */
    private fun MutablePreferences.applyActiveSource(source: SourceConfig) {
        this[PreferencesKeys.SOURCE_ID] = source.id
        this[PreferencesKeys.SOURCE_NAME] = source.name
        this[PreferencesKeys.SERVER_URL] = source.serverUrl
        this[PreferencesKeys.USERNAME] = source.username
        this[PreferencesKeys.PASSWORD] = source.password
        this[PreferencesKeys.SOURCE_TYPE] = source.type.name
        this[PreferencesKeys.MAX_CONNECTIONS] = source.maxConnections
        this[PreferencesKeys.ACTIVE_CONNECTIONS] = source.activeConnections
        this[PreferencesKeys.EXPIRE_DATE] = source.expireDate ?: ""
        this[PreferencesKeys.IS_TRIAL] = source.isTrial
        this[PreferencesKeys.IS_LOGGED_IN] = true
        this[PreferencesKeys.ACTIVE_SOURCE_ID] = source.id
    }

    suspend fun saveActiveSource(source: SourceConfig) {
        dataStore.edit { preferences ->
            preferences.applyActiveSource(source)
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()

            // Upsert en la lista multi-cuentas
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val currentList = if (!rawJson.isNullOrBlank()) {
                try { json.decodeFromString<List<SourceConfig>>(rawJson) } catch (e: Exception) { defaultInitialSources }
            } else defaultInitialSources

            val updated = currentList.filter {
                it.id != source.id && it.serverUrl != source.serverUrl && !isRetiredCustomSource(it.id, it.serverUrl)
            }.toMutableList()
            updated.add(0, source.copy(isActive = true))
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun setActiveSource(sourceId: String): SourceConfig? {
        var active: SourceConfig? = null
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val currentList = deduplicatedSources(rawJson, sourceId)

            val target = currentList.find { it.id == sourceId }
            if (target != null) {
                preferences.applyActiveSource(target)
                preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
                active = target.copy(isActive = true)

                // FASE 33: Persistir exclusividad en SAVED_SOURCES_JSON para evitar que fuentes previas sigan marcadas activas
                val updatedJsonList = currentList.map { it.copy(isActive = (it.id == target.id)) }
                preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(updatedJsonList)
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

            val updated = currentList.filter { it.id != sourceId && !isRetiredCustomSource(it.id, it.serverUrl) }
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(updated)

            // La metadata de frescura pertenece a la fuente: si se borra la fuente, su
            // metadata no debe sobrevivir y hacer que un id reutilizado parezca sincronizado.
            val rawMetadata = preferences[PreferencesKeys.SYNC_METADATA_JSON]
            if (!rawMetadata.isNullOrBlank()) {
                try {
                    val metadata = json.decodeFromString<Map<String, SourceSyncMetadata>>(rawMetadata).toMutableMap()
                    metadata.remove(sourceId)
                    preferences[PreferencesKeys.SYNC_METADATA_JSON] = json.encodeToString(metadata)
                } catch (_: Exception) {
                    preferences.remove(PreferencesKeys.SYNC_METADATA_JSON)
                }
            }

            val activeId = preferences[PreferencesKeys.ACTIVE_SOURCE_ID] ?: preferences[PreferencesKeys.SOURCE_ID]
            if (activeId == sourceId) {
                val next = updated.firstOrNull()
                if (next != null) {
                    preferences.applyActiveSource(next)
                } else {
                    preferences.remove(PreferencesKeys.ACTIVE_SOURCE_ID)
                    preferences.remove(PreferencesKeys.SOURCE_ID)
                    preferences.remove(PreferencesKeys.IS_LOGGED_IN)
                }
            }
        }
    }

    /**
     * FASE 35 (1.0.97): elimina físicamente del JSON las filas heredadas de la lista
     * personalizada y repara el puntero activo si apuntaba a una de ellas.
     *
     * Idempotente: si no hay filas retiradas ni el puntero está roto, no escribe nada.
     */
    suspend fun purgeRetiredSources() {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON] ?: return@edit
            val currentList = try {
                json.decodeFromString<List<SourceConfig>>(rawJson)
            } catch (_: Exception) {
                return@edit
            }

            val kept = currentList
                .filterNot { isRetiredCustomSource(it.id, it.serverUrl) }
                .distinctBy { it.serverUrl.trimEnd('/') + "|" + it.username.trim().lowercase() }
            if (kept.size != currentList.size) {
                preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(kept)
            }

            val activeId = preferences[PreferencesKeys.ACTIVE_SOURCE_ID] ?: preferences[PreferencesKeys.SOURCE_ID]
            if (kept.isEmpty()) {
                if (activeId != null) {
                    preferences.remove(PreferencesKeys.ACTIVE_SOURCE_ID)
                    preferences.remove(PreferencesKeys.SOURCE_ID)
                    preferences.remove(PreferencesKeys.IS_LOGGED_IN)
                }
                return@edit
            }
            if (activeId == null || kept.none { it.id == activeId }) {
                preferences.applyActiveSource(kept.first())
            }
        }
    }

    suspend fun saveAllSources(sources: List<SourceConfig>) {
        val sanitized = sources.filterNot { isRetiredCustomSource(it.id, it.serverUrl) }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(sanitized)
        }
    }

    /**
     * FASE 35 (1.0.97): persiste los datos reales de la cuenta Xtream obtenidos en la
     * autenticación (conexiones simultáneas y vencimiento). Sin esto la UI mostraba los
     * valores de la validación inicial aunque el panel cambiara.
     */
    suspend fun updateSourceAccountInfo(
        sourceId: String,
        activeConnections: Int,
        maxConnections: Int,
        expireDate: String?,
        isTrial: Boolean = false
    ) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON] ?: return@edit
            val currentList = try {
                json.decodeFromString<List<SourceConfig>>(rawJson)
            } catch (_: Exception) {
                return@edit
            }
            val updated = currentList.map { source ->
                if (source.id == sourceId) {
                    source.copy(
                        activeConnections = activeConnections,
                        maxConnections = maxConnections,
                        expireDate = expireDate ?: source.expireDate,
                        isTrial = isTrial
                    )
                } else source
            }
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(updated)

            if (preferences[PreferencesKeys.SOURCE_ID] == sourceId ||
                preferences[PreferencesKeys.ACTIVE_SOURCE_ID] == sourceId
            ) {
                preferences[PreferencesKeys.ACTIVE_CONNECTIONS] = activeConnections
                preferences[PreferencesKeys.MAX_CONNECTIONS] = maxConnections
                if (!expireDate.isNullOrBlank()) preferences[PreferencesKeys.EXPIRE_DATE] = expireDate
                preferences[PreferencesKeys.IS_TRIAL] = isTrial
            }
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

    /** Elimina la metadata de frescura de una fuente retirada o borrada. */
    suspend fun removeSyncMetadata(sourceId: String) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SYNC_METADATA_JSON] ?: return@edit
            val map = try {
                json.decodeFromString<Map<String, SourceSyncMetadata>>(rawJson).toMutableMap()
            } catch (_: Exception) {
                return@edit
            }
            if (map.remove(sourceId) != null) {
                preferences[PreferencesKeys.SYNC_METADATA_JSON] = json.encodeToString(map)
            }
        }
    }
}
