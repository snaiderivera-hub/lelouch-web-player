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
import com.lelouch.core.model.SourceType
import com.lelouch.core.network.diagnostics.IptvDiagnostics
import com.lelouch.core.network.dns.LelouchDnsManager
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

    /**
     * FASE 40 · Picture-in-Picture.
     *
     * El modo y la salida del usuario son callbacks del sistema, así que el estado vive aquí y
     * Compose solo lo observa. [isPlaybackActive] lo mantiene [MobileHomeScreen] mientras haya
     * un stream abierto, para no abrir una ventana flotante vacía.
     */
    private var isInPictureInPicture by mutableStateOf(false)
    private var isPlaybackActive = false

    private val pipSupported: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O

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
            // FASE 41: fuerza la próxima sincronización de la fuente que se está activando.
            // Antes «🔄 Recargar» activaba y llamaba a onForceSync(), pero ese callback leía
            // `effectiveActiveSource`, que en ese instante todavía era la lista anterior: se
            // recargaba la lista vieja y la nueva quedaba vacía.
            var forceSyncSourceId by remember { mutableStateOf<String?>(null) }

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

            // Un error de sincronización deja de ser vigente en cuanto una sincronización
            // posterior termina correctamente; si no, el aviso rojo quedaría pegado.
            LaunchedEffect(syncState) {
                if (syncState is SyncState.Completed) errorMessage = null
            }

            val allSources by app.authRepository.getAllSources().collectAsStateWithLifecycle(initialValue = emptyList())

            fun onRefreshAccountsStatus() {
                lifecycleScope.launch {
                    LelouchDnsManager.prewarmHosts(allSources.map { it.serverUrl })
                    app.authRepository.refreshAllSourcesStatus()
                }
            }

            LaunchedEffect(allSources.map { it.id }.joinToString(",")) {
                if (allSources.isNotEmpty()) {
                    LelouchDnsManager.prewarmHosts(allSources.map { it.serverUrl })
                    app.authRepository.refreshAllSourcesStatus()
                }
            }

            fun onLogin(serverUrl: String, user: String, pass: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val authResult = app.authRepository.validateXtream(serverUrl, user, pass)
                    authResult.onSuccess { source ->
                        forceSyncSourceId = source.id
                        bootstrapViewModel.onSourceAdded(source)
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Error al conectar con el servidor IPTV"
                    }
                }
            }

            fun onActivateSource(sourceId: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val activated = app.authRepository.activateSource(sourceId)
                    if (activated == null) {
                        errorMessage = "No se pudo activar la lista seleccionada."
                    }
                    // No se refresca la nube aquí: eso ponía la pantalla en Loading y mantenía
                    // el estado anterior el tiempo suficiente para sincronizar la lista vieja.
                    // El cambio de fuente dispara LaunchedEffect, que sincroniza la nueva.
                }
            }

            /**
             * Activa la lista y fuerza su sincronización. Se usa desde el botón «Recargar» de
             * los ajustes móviles, donde antes se activaba una lista y se recargaba otra.
             */
            fun onReloadSource(sourceId: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val alreadyActive = activeSource?.id == sourceId
                    // Se marca ANTES de activar: si se marca después, el flujo de la fuente ya
                    // pudo emitir y LaunchedEffect habría arrancado con force = false.
                    if (!alreadyActive) forceSyncSourceId = sourceId
                    val activated = app.authRepository.activateSource(sourceId)
                    if (activated == null) {
                        forceSyncSourceId = null
                        errorMessage = "No se pudo activar la lista seleccionada."
                        return@launch
                    }
                    if (alreadyActive) {
                        // Misma fuente: LaunchedEffect no se vuelve a ejecutar.
                        val result = app.syncManager.syncAll(
                            sourceId = activated.id,
                            serverUrl = activated.serverUrl,
                            user = activated.username,
                            pass = activated.password,
                            sourceType = activated.type,
                            force = true
                        )
                        result.onFailure { error ->
                            errorMessage = error.localizedMessage ?: "No se pudo recargar la lista."
                        }
                    }
                    // Si la fuente cambia, LaunchedEffect consume la petición forzada.
                }
            }

            fun onDeleteSource(sourceId: String) {
                lifecycleScope.launch {
                    app.authRepository.removeSource(sourceId)
                    // El catálogo de la lista borrada no debe seguir en Room: alimentaba
                    // favoritos y raíles con contenido huérfano.
                    app.purgeSourceData(sourceId)
                    bootstrapViewModel.retry()
                }
            }

            fun onAddSource(serverUrl: String, user: String, pass: String, name: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val authResult = app.authRepository.validateXtream(serverUrl, user, pass, name)
                    authResult.onSuccess { source ->
                        // FASE 35: la fuente ya quedó guardada y activa. No se vuelve a
                        // sincronizar la nube ni se llama a syncAll: el LaunchedEffect de la
                        // pantalla sincroniza el catálogo con su indicador de progreso.
                        forceSyncSourceId = source.id
                        bootstrapViewModel.onSourceAdded(source)
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Error al conectar"
                    }
                }
            }

            fun onAddSourceSmart(rawUrl: String, customName: String) {
                errorMessage = null
                lifecycleScope.launch {
                    val authResult = app.authRepository.addSourceFromUrl(rawUrl, customName)
                    authResult.onSuccess { source ->
                        forceSyncSourceId = source.id
                        bootstrapViewModel.onSourceAdded(source)
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Error al conectar"
                    }
                }
            }

            suspend fun onExtractContent(sourceId: String, categoryNames: Set<String>, type: String): Int {
                // FASE 35: la extracción ya no crea una fuente local, así que no hay nada que
                // recargar aquí; un retry solo mostraría el spinner de arranque sin motivo.
                return app.syncManager.extractChannelsToCustomPlaylist(sourceId, categoryNames)
            }

            fun onSyncCloud() {
                lifecycleScope.launch {
                    bootstrapViewModel.retry()
                }
            }

            val effectiveActiveSource = activeSource ?: when (val bs = bootstrapState) {
                is SourceBootstrapState.Ready -> bs.source
                is SourceBootstrapState.UsingLocalCache -> bs.source
                else -> allSources.firstOrNull()
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
                val force = forceSyncSourceId == src.id
                if (force) forceSyncSourceId = null
                if (activeSource == null) {
                    app.authRepository.activateSource(src.id)
                }
                val result = app.syncManager.syncAll(
                    sourceId = src.id,
                    serverUrl = src.serverUrl,
                    user = src.username,
                    pass = src.password,
                    sourceType = src.type,
                    force = force
                )
                result.onFailure { error ->
                    val msg = error.localizedMessage ?: "No se pudo sincronizar la lista."
                    // El mutex devuelve esto cuando dos pantallas piden la misma fuente a la
                    // vez; no es un fallo para el usuario.
                    if (!msg.contains("ya en curso")) errorMessage = msg
                }
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
                                onRefreshAccountsStatus = ::onRefreshAccountsStatus,
                                onLogout = ::onLogout,
                                syncStateText = syncStatusText,
                                playbackHistory = app.playbackHistoryRepository,
                                onRunDiagnostics = { source, onProgress ->
                                    app.diagnostics.run(
                                        config = IptvDiagnostics.Config(
                                            serverUrl = source.serverUrl,
                                            username = source.username,
                                            password = source.password,
                                            m3uListUrl = if (source.type == SourceType.M3U) {
                                                source.serverUrl
                                            } else {
                                                null
                                            },
                                            systemDnsServers = currentDnsServers()
                                        ),
                                        onProgress = onProgress
                                    )
                                },
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
                                onAddSourceSmart = ::onAddSourceSmart,
                                onExtractContent = ::onExtractContent,
                                onSyncCloudSources = ::onSyncCloud,
                                onForceSync = ::onForceSync,
                                onReloadSource = ::onReloadSource,
                                onRefreshAccountsStatus = ::onRefreshAccountsStatus,
                                onRunDiagnostics = { source, onProgress ->
                                    app.diagnostics.run(
                                        config = IptvDiagnostics.Config(
                                            serverUrl = source.serverUrl,
                                            username = source.username,
                                            password = source.password,
                                            m3uListUrl = if (source.type == SourceType.M3U) {
                                                source.serverUrl
                                            } else {
                                                null
                                            },
                                            systemDnsServers = currentDnsServers()
                                        ),
                                        onProgress = onProgress
                                    )
                                },
                                syncStatusText = errorMessage ?: syncStatusText,
                                syncIsError = errorMessage != null || syncState is SyncState.Error,
                                onLogout = ::onLogout,
                                playbackHistory = app.playbackHistoryRepository,
                                onFetchSeriesDetails = { seriesId ->
                                    app.seriesRepository.getSeriesDetailAndEpisodes(
                                        effectiveActiveSource.serverUrl,
                                        effectiveActiveSource.username,
                                        effectiveActiveSource.password,
                                        seriesId
                                    )
                                },
                                isInPictureInPicture = isInPictureInPicture,
                                onPlaybackActiveChanged = { active ->
                                    isPlaybackActive = active
                                    // API 31+: el sistema entra solo en PiP al salir (también con
                                    // el gesto de inicio). Antes de eso lo hace onUserLeaveHint.
                                    updatePictureInPictureParams()
                                },
                                onEnterPictureInPicture = ::enterPictureInPicture
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

    /**
     * FASE 40 · Entra en ventana flotante con proporción 16:9. El vídeo se adapta dentro del
     * recuadro; el resto de la interfaz no se dibuja (ver `MobileHomeScreen`).
     */
    private fun enterPictureInPicture() {
        if (!pipSupported || isInPictureInPicture) return
        runCatching {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
            )
        }
    }

    /**
     * Mantiene al día los parámetros de PiP. Desde Android 12 el sistema entra solo al salir
     * (`autoEnter`); sin esto, el gesto de inicio no abre la ventana flotante.
     */
    private fun updatePictureInPictureParams() {
        if (!pipSupported) return
        runCatching {
            val builder = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(isPlaybackActive)
                builder.setSeamlessResizeEnabled(true)
            }
            setPictureInPictureParams(builder.build())
        }
    }

    /** Salir de la app con un stream abierto deja el vídeo flotando (Android 8–11). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlaybackActive) enterPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPicture = isInPictureInPictureMode
    }

    /**
     * Servidores DNS que usa la red activa. Se pasan al test para comparar el DNS del router
     * con 1.1.1.1 sin que el módulo de red dependa del contexto de Android.
     */
    private fun currentDnsServers(): List<String> {
        return runCatching {
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return emptyList()
            val network = manager.activeNetwork ?: return emptyList()
            manager.getLinkProperties(network)?.dnsServers
                ?.mapNotNull { it.hostAddress }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun isAndroidTvDevice(): Boolean {
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        return hasLeanback || isTelevisionMode
    }
}
