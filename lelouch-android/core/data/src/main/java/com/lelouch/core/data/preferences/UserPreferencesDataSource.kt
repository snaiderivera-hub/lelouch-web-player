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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lelouch_user_prefs")

class UserPreferencesDataSource(private val context: Context) {

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
    }

    val defaultInitialSources = listOf(
        SourceConfig(
            id = "source_67_220",
            name = "67.220.71.35 (@full2)",
            serverUrl = "http://67.220.71.35:8880",
            username = "@full2",
            password = "JdC2QtxtSDda",
            type = SourceType.XTREAM,
            isActive = true
        ),
        SourceConfig(
            id = "source_liontv",
            name = "liontv.es (Helenmejia)",
            serverUrl = "http://liontv.es:80",
            username = "Helenmejia",
            password = "Ejmavv5cPf",
            type = SourceType.XTREAM,
            isActive = false
        ),
        SourceConfig(
            id = "source_ak47",
            name = "ak-47scan.dyndns.tv (Eliezer77tv)",
            serverUrl = "http://ak-47scan.dyndns.tv:25461",
            username = "Eliezer77tv",
            password = "PfkMt2mwEtcR",
            type = SourceType.XTREAM,
            isActive = false
        )
    )

    val allSources: Flow<List<SourceConfig>> = context.dataStore.data.map { preferences ->
        val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
        val activeId = preferences[PreferencesKeys.ACTIVE_SOURCE_ID] ?: preferences[PreferencesKeys.SOURCE_ID]

        val list = if (!rawJson.isNullOrBlank()) {
            try {
                json.decodeFromString<List<SourceConfig>>(rawJson)
            } catch (e: Exception) {
                defaultInitialSources
            }
        } else {
            defaultInitialSources
        }

        list.map { source ->
            source.copy(isActive = (source.id == activeId || (activeId == null && source.id == list.firstOrNull()?.id)))
        }
    }

    val activeSource: Flow<SourceConfig?> = context.dataStore.data.map { preferences ->
        val isLoggedIn = preferences[PreferencesKeys.IS_LOGGED_IN] ?: false
        if (!isLoggedIn) {
            null
        } else {
            val activeId = preferences[PreferencesKeys.ACTIVE_SOURCE_ID] ?: preferences[PreferencesKeys.SOURCE_ID]
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val list = if (!rawJson.isNullOrBlank()) {
                try {
                    json.decodeFromString<List<SourceConfig>>(rawJson)
                } catch (e: Exception) {
                    defaultInitialSources
                }
            } else {
                defaultInitialSources
            }
            list.find { it.id == activeId } ?: list.firstOrNull()
        }
    }

    suspend fun saveActiveSource(source: SourceConfig) {
        context.dataStore.edit { preferences ->
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
        context.dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val currentList = if (!rawJson.isNullOrBlank()) {
                try { json.decodeFromString<List<SourceConfig>>(rawJson) } catch (e: Exception) { defaultInitialSources }
            } else defaultInitialSources

            val target = currentList.find { it.id == sourceId }
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
        context.dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.SAVED_SOURCES_JSON]
            val currentList = if (!rawJson.isNullOrBlank()) {
                try { json.decodeFromString<List<SourceConfig>>(rawJson) } catch (e: Exception) { defaultInitialSources }
            } else defaultInitialSources

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
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVED_SOURCES_JSON] = json.encodeToString(sources)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] = false
            preferences.remove(PreferencesKeys.ACTIVE_SOURCE_ID)
        }
    }
}
