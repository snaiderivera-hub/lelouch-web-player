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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.LelouchBackground
import com.lelouch.core.designsystem.LelouchCyanAccent
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
                is SyncState.Checking -> "Comprobando actualizaciones..."
                is SyncState.UpToDate -> state.message
                is SyncState.TtlFresh -> state.message
                is SyncState.OfflineUsingCache -> state.message
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
                        app.syncManager.syncIfNeeded(
                            sourceId = activated.id,
                            serverUrl = activated.serverUrl,
                            user = activated.username,
                            pass = activated.password,
                            sourceType = activated.type
                        )
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

            val effectiveActiveSource = activeSource ?: allSources.firstOrNull()

            fun onForceSync() {
                val src = effectiveActiveSource ?: return
                lifecycleScope.launch {
                    app.syncManager.syncAll(
                        sourceId = src.id,
                        serverUrl = src.serverUrl,
                        user = src.username,
                        pass = src.password,
                        sourceType = src.type,
                        force = true
                    )
                }
            }

            fun onLogout() {
                lifecycleScope.launch {
                    app.authRepository.logout()
                }
            }

            // ── FASE P0 #5: Arranque inteligente — catálogo local Room primero, sync solo si cambió ──
            LaunchedEffect(effectiveActiveSource?.id) {
                val src = effectiveActiveSource ?: return@LaunchedEffect
                if (activeSource == null) {
                    // Fija el id activo en DataStore si aún no está fijado.
                    app.authRepository.activateSource(src.id)
                }
                app.syncManager.syncIfNeeded(
                    sourceId = src.id,
                    serverUrl = src.serverUrl,
                    user = src.username,
                    pass = src.password,
                    sourceType = src.type
                )
            }

            // FASE P1 #2A & P1 #2B: En Android TV las listas completas de Canales y Películas NO se colectan en memoria (Paging 3 directo a Room)
            val liveChannels by (if (!isTv && effectiveActiveSource != null) app.channelRepository.getAllChannels(effectiveActiveSource.id) else kotlinx.coroutines.flow.emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val movies by (if (!isTv && effectiveActiveSource != null) app.vodRepository.getAllMovies(effectiveActiveSource.id) else kotlinx.coroutines.flow.emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val recentMovies by (if (isTv && effectiveActiveSource != null) app.vodRepository.getRecentlyAddedMovies(20) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesList by (if (effectiveActiveSource != null) app.seriesRepository.getAllSeries(effectiveActiveSource.id) else kotlinx.coroutines.flow.emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val liveCategories by (if (effectiveActiveSource != null) app.channelRepository.getCategories(effectiveActiveSource.id) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val vodCategories by (if (effectiveActiveSource != null) app.vodRepository.getCategories(effectiveActiveSource.id) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesCategories by (if (effectiveActiveSource != null) app.seriesRepository.getCategories(effectiveActiveSource.id) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val favoriteChannels by app.channelRepository.getFavoriteChannels().collectAsStateWithLifecycle(initialValue = emptyList())
            val favoriteMovies by app.vodRepository.getFavoriteMovies().collectAsStateWithLifecycle(initialValue = emptyList())

            if (isTv) {
                LelouchTvTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        if (effectiveActiveSource != null) {
                            TvHomeScreen(
                                activeSource = effectiveActiveSource,
                                allSources = allSources,
                                liveChannels = emptyList(),
                                channelRepository = app.channelRepository,
                                liveCategories = liveCategories,
                                movies = emptyList(),
                                vodRepository = app.vodRepository,
                                recentMovies = recentMovies,
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
                                syncStateText = syncStatusText,
                                onFetchSeriesDetails = { seriesId ->
                                    app.seriesRepository.getSeriesDetailAndEpisodes(
                                        effectiveActiveSource.serverUrl,
                                        effectiveActiveSource.username,
                                        effectiveActiveSource.password,
                                        seriesId
                                    )
                                }
                            )
                        } else {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    androidx.compose.material3.Text(
                                        text = "REPRODUCTOR LELOUCH",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                        letterSpacing = 2.sp
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
                                    androidx.compose.material3.Text(
                                        text = "Sincronizando listas desde Supabase Cloud...",
                                        color = LelouchCyanAccent,
                                        fontSize = 13.sp
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
                                    androidx.compose.material3.CircularProgressIndicator(color = LelouchCyanAccent)
                                }
                            }
                        }
                    }
                }
            } else {
                LelouchTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LelouchBackground
                    ) {
                        if (effectiveActiveSource != null) {
                            MobileHomeScreen(
                                activeSource = effectiveActiveSource,
                                allSources = allSources,
                                liveChannels = liveChannels,
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
                                    app.seriesRepository.getSeriesDetailAndEpisodes(
                                        effectiveActiveSource.serverUrl,
                                        effectiveActiveSource.username,
                                        effectiveActiveSource.password,
                                        seriesId
                                    )
                                }
                            )
                        } else {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    androidx.compose.material3.Text(
                                        text = "REPRODUCTOR LELOUCH",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                        letterSpacing = 1.5.sp
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
                                    androidx.compose.material3.Text(
                                        text = "Sincronizando listas desde Supabase Cloud...",
                                        color = LelouchCyanAccent,
                                        fontSize = 12.sp
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
                                    androidx.compose.material3.CircularProgressIndicator(color = LelouchCyanAccent)
                                }
                            }
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
