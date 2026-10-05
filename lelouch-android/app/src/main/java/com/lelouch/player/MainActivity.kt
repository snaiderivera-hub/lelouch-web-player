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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lelouch.core.data.sync.SyncState
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
import com.lelouch.player.bootstrap.SourceBootstrapState
import com.lelouch.player.bootstrap.SourceBootstrapViewModel
import com.lelouch.feature.tv.components.TvBootstrapRecoveryScreen
import com.lelouch.feature.tv.components.TvAdminPanelModal
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = LelouchApplication.instance
        val isTv = isAndroidTvDevice()

        setContent {
            val bootstrapViewModel = remember { SourceBootstrapViewModel(app.authRepository) }
            val bootstrapState by bootstrapViewModel.bootstrapState.collectAsStateWithLifecycle()
            val activeSource by app.authRepository.getActiveSource().collectAsStateWithLifecycle(initialValue = null)
            val syncState by app.syncManager.syncState.collectAsStateWithLifecycle()

            var errorMessage by remember { mutableStateOf<String?>(null) }
            var isManualAdminModalVisible by remember { mutableStateOf(false) }

            val syncStatusText = when {
                bootstrapState is SourceBootstrapState.UsingLocalCache -> (bootstrapState as SourceBootstrapState.UsingLocalCache).warning
                syncState is SyncState.Checking -> "Comprobando actualizaciones..."
                syncState is SyncState.UpToDate -> (syncState as SyncState.UpToDate).message
                syncState is SyncState.TtlFresh -> (syncState as SyncState.TtlFresh).message
                syncState is SyncState.OfflineUsingCache -> (syncState as SyncState.OfflineUsingCache).message
                syncState is SyncState.Authenticating -> "Validando credenciales con el servidor..."
                syncState is SyncState.SyncingCategories -> (syncState as SyncState.SyncingCategories).step
                syncState is SyncState.SyncingLive -> "Sincronizando canales en vivo (${(syncState as SyncState.SyncingLive).count} importados)..."
                syncState is SyncState.SyncingMovies -> "Sincronizando películas VOD (${(syncState as SyncState.SyncingMovies).count} importadas)..."
                syncState is SyncState.SyncingSeries -> "Sincronizando series (${(syncState as SyncState.SyncingSeries).count} importadas)..."
                syncState is SyncState.Completed -> "¡Catálogo sincronizado con éxito!"
                syncState is SyncState.Error -> (syncState as SyncState.Error).message
                else -> ""
            }

            if (syncState is SyncState.Error) {
                errorMessage = (syncState as SyncState.Error).message
            }

            val allSources by app.authRepository.getAllSources().collectAsStateWithLifecycle(initialValue = emptyList())

            fun onLogin(serverUrl: String, user: String, pass: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val authResult = app.authRepository.validateXtream(serverUrl, user, pass)
                    authResult.onSuccess { source ->
                        app.syncManager.syncAll(source.id, serverUrl, user, pass)
                        bootstrapViewModel.retry()
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
                        bootstrapViewModel.retry()
                    }
                }
            }

            fun onDeleteSource(sourceId: String) {
                lifecycleScope.launch {
                    app.authRepository.removeSource(sourceId)
                    bootstrapViewModel.retry()
                }
            }

            fun onAddSource(serverUrl: String, user: String, pass: String, name: String) {
                lifecycleScope.launch {
                    val authResult = app.authRepository.validateXtream(serverUrl, user, pass, name)
                    authResult.onSuccess { source ->
                        app.syncManager.syncAll(source.id, source.serverUrl, source.username, source.password)
                        bootstrapViewModel.retry()
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Error al conectar"
                    }
                }
            }

            fun onSyncCloud() {
                lifecycleScope.launch {
                    bootstrapViewModel.retry()
                }
            }

            val effectiveActiveSource = when (val bs = bootstrapState) {
                is SourceBootstrapState.Ready -> bs.source
                is SourceBootstrapState.UsingLocalCache -> bs.source
                else -> activeSource ?: allSources.firstOrNull()
            }

            val isHomeVisible = effectiveActiveSource != null &&
                    (bootstrapState is SourceBootstrapState.Ready || bootstrapState is SourceBootstrapState.UsingLocalCache)

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
                    bootstrapViewModel.retry()
                }
            }

            // ── FASE P0 #5: Arranque inteligente — catálogo local Room primero, sync solo si cambió ──
            LaunchedEffect(effectiveActiveSource?.id) {
                val src = effectiveActiveSource ?: return@LaunchedEffect
                if (activeSource == null) {
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

            val liveChannels by (if (!isTv && effectiveActiveSource != null) app.channelRepository.getAllChannels(effectiveActiveSource.id) else kotlinx.coroutines.flow.emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val movies by (if (!isTv && effectiveActiveSource != null) app.vodRepository.getAllMovies(effectiveActiveSource.id) else kotlinx.coroutines.flow.emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val recentMovies by (if (isTv && effectiveActiveSource != null) app.vodRepository.getRecentlyAddedMovies(20) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val seriesList by (if (!isTv && effectiveActiveSource != null) app.seriesRepository.getAllSeries(effectiveActiveSource.id) else kotlinx.coroutines.flow.emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
            val featuredSeries by (if (isTv && effectiveActiveSource != null) app.seriesRepository.getFeaturedSeries(20) else emptyFlow()).collectAsStateWithLifecycle(initialValue = emptyList())
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
                        if (isHomeVisible && effectiveActiveSource != null) {
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
                                seriesList = emptyList(),
                                seriesRepository = app.seriesRepository,
                                featuredSeries = featuredSeries,
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
                            when (val bs = bootstrapState) {
                                is SourceBootstrapState.Loading -> {
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
                                is SourceBootstrapState.Empty -> {
                                    TvBootstrapRecoveryScreen(
                                        subtitle = "No hay listas configuradas",
                                        message = "No se encontraron listas disponibles en tu cuenta de Supabase Cloud.",
                                        retryable = true,
                                        onRetry = { bootstrapViewModel.retry() },
                                        onOpenSettings = { isManualAdminModalVisible = true }
                                    )
                                }
                                is SourceBootstrapState.ErrorNoSource -> {
                                    TvBootstrapRecoveryScreen(
                                        subtitle = "No fue posible cargar tus listas",
                                        message = bs.message,
                                        retryable = bs.retryable,
                                        onRetry = { bootstrapViewModel.retry() },
                                        onOpenSettings = { isManualAdminModalVisible = true }
                                    )
                                }
                                else -> {
                                    TvBootstrapRecoveryScreen(
                                        subtitle = "Sin listas activas",
                                        message = "Configura una cuenta IPTV o reintenta la conexión.",
                                        retryable = true,
                                        onRetry = { bootstrapViewModel.retry() },
                                        onOpenSettings = { isManualAdminModalVisible = true }
                                    )
                                }
                            }
                        }

                        if (isManualAdminModalVisible) {
                            TvAdminPanelModal(
                                activeSource = effectiveActiveSource,
                                allSources = allSources,
                                onActivateSource = { id ->
                                    onActivateSource(id)
                                    bootstrapViewModel.retry()
                                    isManualAdminModalVisible = false
                                },
                                onDeleteSource = ::onDeleteSource,
                                onAddSource = { url, user, pass, name ->
                                    onAddSource(url, user, pass, name)
                                    bootstrapViewModel.retry()
                                    isManualAdminModalVisible = false
                                },
                                onSyncCloudSources = {
                                    onSyncCloud()
                                    bootstrapViewModel.retry()
                                },
                                onForceSync = ::onForceSync,
                                onLogout = ::onLogout,
                                onDismiss = { isManualAdminModalVisible = false }
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
                        if (isHomeVisible && effectiveActiveSource != null) {
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
                            when (val bs = bootstrapState) {
                                is SourceBootstrapState.Loading -> {
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
                                else -> {
                                    val title = if (bs is SourceBootstrapState.Empty) "No hay listas configuradas" else "No fue posible cargar tus listas"
                                    val msg = if (bs is SourceBootstrapState.ErrorNoSource) bs.message else "Verifica tu conexión a internet o configura una cuenta IPTV."
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                                                text = title,
                                                color = LelouchCyanAccent,
                                                fontSize = 14.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                                            androidx.compose.material3.Text(
                                                text = msg,
                                                color = androidx.compose.ui.graphics.Color.Gray,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
                                            androidx.compose.material3.Button(
                                                onClick = { bootstrapViewModel.retry() },
                                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent)
                                            ) {
                                                androidx.compose.material3.Text("Reintentar", color = androidx.compose.ui.graphics.Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            }
                                        }
                                    }
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
