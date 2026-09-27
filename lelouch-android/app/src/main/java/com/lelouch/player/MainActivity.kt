package com.lelouch.player

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
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

            val liveChannels by app.channelRepository.getFeaturedChannels(50).collectAsStateWithLifecycle(initialValue = emptyList())
            val movies by app.vodRepository.getRecentlyAddedMovies(40).collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesList by app.seriesRepository.getFeaturedSeries(30).collectAsStateWithLifecycle(initialValue = emptyList())
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
                                movies = movies,
                                seriesList = seriesList,
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

    private fun isAndroidTvDevice(): Boolean {
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        return hasLeanback || isTelevisionMode
    }
}
