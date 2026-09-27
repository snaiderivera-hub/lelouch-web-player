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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lelouch_user_prefs")

class UserPreferencesDataSource(private val context: Context) {

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
    }

    val activeSource: Flow<SourceConfig?> = context.dataStore.data.map { preferences ->
        val isLoggedIn = preferences[PreferencesKeys.IS_LOGGED_IN] ?: false
        if (!isLoggedIn) return@map null

        val id = preferences[PreferencesKeys.SOURCE_ID] ?: "default_source"
        val serverUrl = preferences[PreferencesKeys.SERVER_URL] ?: return@map null
        val username = preferences[PreferencesKeys.USERNAME] ?: ""
        val password = preferences[PreferencesKeys.PASSWORD] ?: ""

        SourceConfig(
            id = id,
            name = preferences[PreferencesKeys.SOURCE_NAME] ?: "Mi Lista IPTV",
            serverUrl = serverUrl,
            username = username,
            password = password,
            type = SourceType.valueOf(preferences[PreferencesKeys.SOURCE_TYPE] ?: SourceType.XTREAM.name),
            maxConnections = preferences[PreferencesKeys.MAX_CONNECTIONS] ?: 1,
            activeConnections = preferences[PreferencesKeys.ACTIVE_CONNECTIONS] ?: 0,
            expireDate = preferences[PreferencesKeys.EXPIRE_DATE],
            isTrial = preferences[PreferencesKeys.IS_TRIAL] ?: false,
            isActive = true
        )
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
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
