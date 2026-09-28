package com.lelouch.player

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lelouch.core.data.sync.SyncState
import com.lelouch.core.designsystem.LelouchBackground
import com.lelouch.core.designsystem.LelouchTheme
import com.lelouch.core.designsystem.LelouchTvTheme
import com.lelouch.feature.mobile.MobileHomeScreen
import com.lelouch.feature.mobile.MobileLoginScreen
import com.lelouch.feature.tv.TvHomeScreen
import com.lelouch.feature.tv.TvLoginScreen
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = LelouchApplication.instance
        val isTv = isAndroidTvDevice()

        setContent {
            val activeSource by app.authRepository.getActiveSource().collectAsStateWithLifecycle(initialValue = null)
            val syncState by app.syncManager.syncState.collectAsStateWithLifecycle()

            var errorMessage by remember { mutableStateOf<String?>(null) }
            val isLoading = syncState is SyncState.Authenticating ||
                    syncState is SyncState.SyncingCategories ||
                    syncState is SyncState.SyncingLive ||
                    syncState is SyncState.SyncingMovies ||
                    syncState is SyncState.SyncingSeries

            val syncStatusText = when (val state = syncState) {
                is SyncState.Authenticating -> "Validando credenciales con el servidor..."
                is SyncState.SyncingCategories -> state.step
                is SyncState.SyncingLive -> "Sincronizando canales en vivo (${state.count} importados)..."
                is SyncState.SyncingMovies -> "Sincronizando películas VOD (${state.count} importadas)..."
                is SyncState.SyncingSeries -> "Sincronizando series (${state.count} importadas)..."
                is SyncState.Completed -> "¡Catálogo sincronizado con éxito!"
                is SyncState.Error -> state.message
                else -> ""
            }

            if (syncState is SyncState.Error) {
                errorMessage = (syncState as SyncState.Error).message
            }

            val allSources by app.authRepository.getAllSources().collectAsStateWithLifecycle(initialValue = emptyList())

            LaunchedEffect(Unit) {
                app.authRepository.syncCloudSources()
            }

            fun onLogin(serverUrl: String, user: String, pass: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val authResult = app.authRepository.validateXtream(serverUrl, user, pass)
                    authResult.onSuccess { source ->
                        // Sincronizar catálogo tras login exitoso
                        app.syncManager.syncAll(source.id, serverUrl, user, pass)
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Error al conectar con el servidor IPTV"
                    }
                }
            }

            fun onActivateSource(sourceId: String) {
                lifecycleScope.launch {
                    val activated = app.authRepository.activateSource(sourceId)
                    if (activated != null) {
                        app.syncManager.syncAll(activated.id, activated.serverUrl, activated.username, activated.password)
                    }
                }
            }

            fun onDeleteSource(sourceId: String) {
                lifecycleScope.launch {
                    app.authRepository.removeSource(sourceId)
                }
            }

            fun onAddSource(serverUrl: String, user: String, pass: String, name: String) {
                lifecycleScope.launch {
                    val authResult = app.authRepository.validateXtream(serverUrl, user, pass, name)
                    authResult.onSuccess { source ->
                        app.syncManager.syncAll(source.id, source.serverUrl, source.username, source.password)
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Error al conectar"
                    }
                }
            }

            fun onSyncCloud() {
                lifecycleScope.launch {
                    app.authRepository.syncCloudSources()
                }
            }

            fun onForceSync() {
                activeSource?.let { src ->
                    lifecycleScope.launch {
                        app.syncManager.syncAll(src.id, src.serverUrl, src.username, src.password)
                    }
                }
            }

            fun onLogout() {
                lifecycleScope.launch {
                    app.authRepository.logout()
                }
            }

            val liveChannels by (if (activeSource != null) app.channelRepository.getAllChannels(activeSource!!.id) else app.channelRepository.getAllChannels()).collectAsStateWithLifecycle(initialValue = emptyList())
            val liveChannelsPaging = if (activeSource != null) app.channelRepository.getAllChannelsPaging(activeSource!!.id) else app.channelRepository.getAllChannelsPaging()
            
            val movies by (if (activeSource != null) app.vodRepository.getAllMovies(activeSource!!.id) else app.vodRepository.getAllMovies()).collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesList by (if (activeSource != null) app.seriesRepository.getAllSeries(activeSource!!.id) else app.seriesRepository.getAllSeries()).collectAsStateWithLifecycle(initialValue = emptyList())
            val liveCategories by (if (activeSource != null) app.channelRepository.getCategories(activeSource!!.id) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val vodCategories by (if (activeSource != null) app.vodRepository.getCategories(activeSource!!.id) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesCategories by (if (activeSource != null) app.seriesRepository.getCategories(activeSource!!.id) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val favoriteChannels by app.channelRepository.getFavoriteChannels().collectAsStateWithLifecycle(initialValue = emptyList())
            val favoriteMovies by app.vodRepository.getFavoriteMovies().collectAsStateWithLifecycle(initialValue = emptyList())

            if (isTv) {
                LelouchTvTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        if (activeSource != null) {
                            TvHomeScreen(
                                activeSource = activeSource,
                                allSources = allSources,
                                liveChannels = liveChannels,
                                liveChannelsPaging = liveChannelsPaging,
                                liveCategories = liveCategories,
                                movies = movies,
                                vodCategories = vodCategories,
                                seriesList = seriesList,
                                seriesCategories = seriesCategories,
                                favoriteChannels = favoriteChannels,
                                favoriteMovies = favoriteMovies,
                                onToggleFavoriteChannel = { streamId, isFav ->
                                    lifecycleScope.launch { app.channelRepository.toggleFavorite(streamId, isFav) }
                                },
                                onToggleFavoriteMovie = { streamId, isFav ->
                                    lifecycleScope.launch { app.vodRepository.toggleFavorite(streamId, isFav) }
                                },
                                onActivateSource = ::onActivateSource,
                                onDeleteSource = ::onDeleteSource,
                                onAddSource = ::onAddSource,
                                onSyncCloudSources = ::onSyncCloud,
                                onForceSync = ::onForceSync,
                                onLogout = ::onLogout,
                                onFetchSeriesDetails = { seriesId ->
                                    activeSource?.let { src ->
                                        app.seriesRepository.getSeriesDetailAndEpisodes(
                                            src.serverUrl,
                                            src.username,
                                            src.password,
                                            seriesId
                                        )
                                    } ?: Pair(listOf(1), emptyList())
                                }
                            )
                        } else {
                            TvLoginScreen(
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                syncStatusText = syncStatusText,
                                savedSources = allSources,
                                onSelectSavedSource = { src ->
                                    onLogin(src.serverUrl, src.username, src.password)
                                },
                                onSyncCloudSources = ::onSyncCloud,
                                onLoginClick = ::onLogin
                            )
                        }
                    }
                }
            } else {
                LelouchTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        if (activeSource != null) {
                            MobileHomeScreen(
                                activeSource = activeSource,
                                allSources = allSources,
                                liveChannels = liveChannels,
                                liveCategories = liveCategories,
                                movies = movies,
                                vodCategories = vodCategories,
                                seriesList = seriesList,
                                seriesCategories = seriesCategories,
                                onActivateSource = ::onActivateSource,
                                onDeleteSource = ::onDeleteSource,
                                onAddSource = ::onAddSource,
                                onSyncCloudSources = ::onSyncCloud,
                                onForceSync = ::onForceSync,
                                onLogout = ::onLogout
                            )
                        } else {
                            MobileLoginScreen(
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                savedSources = allSources,
                                onSelectSavedSource = { src ->
                                    onLogin(src.serverUrl, src.username, src.password)
                                },
                                onSyncCloudSources = ::onSyncCloud,
                                onLoginClick = ::onLogin
                            )
                        }
                    }
                }
            }

        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Telemetría segura de teclas del control remoto Xiaomi (sin URLs ni credenciales)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyName = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
                KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
                KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
                KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER"
                KeyEvent.KEYCODE_ENTER -> "ENTER"
                KeyEvent.KEYCODE_NUMPAD_ENTER -> "NUMPAD_ENTER"
                KeyEvent.KEYCODE_BACK -> "BACK"
                KeyEvent.KEYCODE_CHANNEL_UP -> "CHANNEL_UP"
                KeyEvent.KEYCODE_CHANNEL_DOWN -> "CHANNEL_DOWN"
                KeyEvent.KEYCODE_PAGE_UP -> "PAGE_UP"
                KeyEvent.KEYCODE_PAGE_DOWN -> "PAGE_DOWN"
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "MEDIA_PLAY_PAUSE"
                KeyEvent.KEYCODE_MEDIA_PLAY -> "MEDIA_PLAY"
                KeyEvent.KEYCODE_MEDIA_PAUSE -> "MEDIA_PAUSE"
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "MEDIA_FAST_FORWARD"
                KeyEvent.KEYCODE_MEDIA_REWIND -> "MEDIA_REWIND"
                KeyEvent.KEYCODE_TV -> "TV"
                else -> "KEY_${event.keyCode}"
            }
            android.util.Log.d("XIAOMI_REMOTE", "KeyEvent: keyCode=${event.keyCode} ($keyName), action=ACTION_DOWN, repeat=${event.repeatCount}")
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isAndroidTvDevice(): Boolean {
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        return hasLeanback || isTelevisionMode
    }
}
