package com.lelouch.feature.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.Category
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.VodMovie
import com.lelouch.core.model.Series
import com.lelouch.core.model.Episode
import com.lelouch.core.player.PlaybackState
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType
import com.lelouch.core.network.StreamUrlResolver
import com.lelouch.core.player.LelouchVideoPlayer
import com.lelouch.core.player.rememberLelouchPlayer
import kotlinx.coroutines.launch

data class MobileCategoryItem(
    val id: String,
    val name: String
)

@Composable
fun MobileHomeScreen(
    activeSource: SourceConfig? = null,
    allSources: List<SourceConfig> = emptyList(),
    liveChannels: List<LiveStream> = emptyList(),
    liveCategories: List<Category> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    vodCategories: List<Category> = emptyList(),
    seriesList: List<Series> = emptyList(),
    seriesCategories: List<Category> = emptyList(),
    favoriteChannels: List<LiveStream> = emptyList(),
    favoriteMovies: List<VodMovie> = emptyList(),
    onToggleFavoriteChannel: (streamId: Int, isFav: Boolean) -> Unit = { _, _ -> },
    onToggleFavoriteMovie: (streamId: Int, isFav: Boolean) -> Unit = { _, _ -> },
    onActivateSource: (String) -> Unit = {},
    onDeleteSource: (String) -> Unit = {},
    onAddSource: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onAddSourceSmart: ((rawUrl: String, customName: String) -> Unit)? = null,
    onExtractContent: (suspend (sourceId: String, categoryNames: Set<String>, type: String) -> Int)? = null,
    onSyncCloudSources: () -> Unit = {},
    onForceSync: () -> Unit = {},
    onLogout: () -> Unit = {},
    onFetchSeriesDetails: (suspend (seriesId: Int) -> Pair<List<Int>, List<Episode>>)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var isLivePlayback by remember { mutableStateOf(true) }
    var currentPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    var activeChannelName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    val playerEngine = rememberLelouchPlayer()

    val context = LocalContext.current
    val activity = context as? Activity
    val hiddenPrefs = remember { context.getSharedPreferences("iptv_hidden_categories", android.content.Context.MODE_PRIVATE) }
    
    var hiddenLiveCategories by remember { mutableStateOf(hiddenPrefs.getStringSet("live", emptySet())?.toSet() ?: emptySet()) }
    var hiddenMovieCategories by remember { mutableStateOf(hiddenPrefs.getStringSet("movies", emptySet())?.toSet() ?: emptySet()) }
    var hiddenSeriesCategories by remember { mutableStateOf(hiddenPrefs.getStringSet("series", emptySet())?.toSet() ?: emptySet()) }

    fun saveHiddenLive(newSet: Set<String>) {
        hiddenLiveCategories = newSet
        hiddenPrefs.edit().putStringSet("live", newSet).apply()
    }
    fun saveHiddenMovies(newSet: Set<String>) {
        hiddenMovieCategories = newSet
        hiddenPrefs.edit().putStringSet("movies", newSet).apply()
    }
    fun saveHiddenSeries(newSet: Set<String>) {
        hiddenSeriesCategories = newSet
        hiddenPrefs.edit().putStringSet("series", newSet).apply()
    }

    // FASE 32: Permitir rotación automática controlada
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto-rotar a horizontal al entrar a pantalla completa y volver a vertical al salir
    LaunchedEffect(isPlayerFullscreen) {
        if (isPlayerFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    BackHandler(enabled = isPlayerFullscreen) {
        isPlayerFullscreen = false
    }

    // FASE 33: Regex defensivo para filtrar episodios de series en pantalla de canales en vivo
    val seriesLeakRegex = remember { Regex("(?i)\\b(s\\d{1,2}|t\\d{1,2}|cap\\.?\\s*\\d+|ep\\.?\\s*\\d+)\\b") }
    val cleanLiveChannels = remember(liveChannels, hiddenLiveCategories) {
        liveChannels.filter { stream ->
            (stream.streamType.isBlank() || stream.streamType.equals("live", ignoreCase = true)) &&
            !seriesLeakRegex.containsMatchIn(stream.name) &&
            stream.categoryId !in hiddenLiveCategories && stream.categoryName !in hiddenLiveCategories
        }.distinctBy { ch -> ch.streamUrl.ifBlank { ch.name } }
    }

    val playbackState by playerEngine.playbackState.collectAsState()
    var consecutiveAutoSkips by remember { mutableIntStateOf(0) }

    LaunchedEffect(playbackState) {
        if (playbackState is com.lelouch.core.player.PlaybackState.Playing) {
            consecutiveAutoSkips = 0
        }
    }

    // FASE 33: Anticaídas de señal y Omisión Automática de canales caídos o vacíos
    DisposableEffect(playerEngine, cleanLiveChannels, activeStreamUrl, isLivePlayback, activeSource) {
        playerEngine.onChannelUnavailable = {
            if (isLivePlayback && cleanLiveChannels.isNotEmpty()) {
                if (consecutiveAutoSkips < 5) {
                    consecutiveAutoSkips++
                    val currentIndex = cleanLiveChannels.indexOfFirst { it.name == activeChannelName }
                    val nextIndex = if (currentIndex in 0 until cleanLiveChannels.size - 1) currentIndex + 1 else 0
                    val nextChannel = cleanLiveChannels[nextIndex]
                    android.widget.Toast.makeText(
                        context,
                        "⚠️ Señal caída. Saltando a: ${nextChannel.name}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    activeChannelName = nextChannel.name
                    val targetUrl = com.lelouch.core.network.StreamUrlResolver.resolveLive(nextChannel.streamUrl, activeSource, nextChannel.streamId)
                    if (targetUrl == activeStreamUrl) {
                        playerEngine.playStream(targetUrl, isLive = true)
                    } else {
                        activeStreamUrl = targetUrl
                    }
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Demasiados canales caídos. Deteniendo salto automático.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    consecutiveAutoSkips = 0
                }
            }
        }
        onDispose {
            playerEngine.onChannelUnavailable = null
        }
    }


    // FASE 34: Gestos táctiles de cambio de canal y OSD flotante
    var channelOsdText by remember { mutableStateOf<String?>(null) }
    var channelOsdSubtext by remember { mutableStateOf<String?>(null) }
    var channelOsdDirection by remember { mutableStateOf<String?>(null) }
    var channelOsdVersion by remember { mutableIntStateOf(0) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    LaunchedEffect(channelOsdVersion) {
        if (channelOsdVersion > 0) {
            kotlinx.coroutines.delay(2200L)
            channelOsdText = null
            channelOsdSubtext = null
            channelOsdDirection = null
        }
    }

    val switchChannelByOffset: (Int) -> Unit = remember(cleanLiveChannels, activeStreamUrl, activeChannelName, activeSource) {
        { delta: Int ->
            if (cleanLiveChannels.isNotEmpty()) {
                val currentIndex = cleanLiveChannels.indexOfFirst { it.name == activeChannelName }
                val newIndex = when {
                    currentIndex == -1 -> 0
                    delta > 0 -> if (currentIndex < cleanLiveChannels.size - 1) currentIndex + 1 else 0
                    else -> if (currentIndex > 0) currentIndex - 1 else cleanLiveChannels.size - 1
                }
                
                val targetChannel = cleanLiveChannels[newIndex]
                activeChannelName = targetChannel.name
                val targetUrl = com.lelouch.core.network.StreamUrlResolver.resolveLive(targetChannel.streamUrl, activeSource, targetChannel.streamId)
                if (targetUrl == activeStreamUrl) {
                    playerEngine.playStream(targetUrl, isLive = true)
                } else {
                    activeStreamUrl = targetUrl
                }
                channelOsdText = targetChannel.name
                channelOsdSubtext = "Canal ${newIndex + 1} de ${cleanLiveChannels.size}"
                channelOsdDirection = if (delta > 0) "▲ Siguiente Canal" else "▼ Canal Anterior"
                channelOsdVersion++
            }
        }
    }

    val liveCategoryList = remember(liveCategories, cleanLiveChannels, hiddenLiveCategories) {
        val fromDb = liveCategories.map { MobileCategoryItem(id = it.categoryId, name = it.categoryName) }
        val fromChannels = cleanLiveChannels.mapNotNull { ch ->
            if (ch.categoryName.isNotBlank() && ch.categoryId.isNotBlank()) {
                MobileCategoryItem(id = ch.categoryId, name = ch.categoryName)
            } else null
        }
        val combined = (fromDb + fromChannels)
            .filter { it.id != "all" && !it.name.equals("todos", ignoreCase = true) }
            .filter { it.id !in hiddenLiveCategories && it.name !in hiddenLiveCategories }
            .distinctBy { it.name.trim().lowercase() }
            .sortedBy { it.name }
        listOf(MobileCategoryItem(id = "all", name = "Todos")) + combined
    }

    val filteredMovies = remember(movies, hiddenMovieCategories) {
        movies.filter { it.categoryId !in hiddenMovieCategories && it.categoryName !in hiddenMovieCategories }
    }

    val movieCategoryList = remember(vodCategories, filteredMovies, hiddenMovieCategories) {
        val fromDb = vodCategories.map { MobileCategoryItem(id = it.categoryId, name = it.categoryName) }
        val fromMovies = filteredMovies.mapNotNull { mov ->
            if (mov.categoryName.isNotBlank() && mov.categoryId.isNotBlank()) {
                MobileCategoryItem(id = mov.categoryId, name = mov.categoryName)
            } else null
        }.distinctBy { it.id }
        val combined = (fromDb + fromMovies)
            .filter { it.id !in hiddenMovieCategories && it.name !in hiddenMovieCategories }
            .distinctBy { it.id }.sortedBy { it.name }
        listOf(MobileCategoryItem(id = "all", name = "Todas")) + combined
    }

    val filteredSeriesList = remember(seriesList, hiddenSeriesCategories) {
        seriesList.filter { it.categoryId !in hiddenSeriesCategories && it.categoryName !in hiddenSeriesCategories }
    }

    val seriesCategoryList = remember(seriesCategories, filteredSeriesList, hiddenSeriesCategories) {
        val fromDb = seriesCategories.map { MobileCategoryItem(id = it.categoryId, name = it.categoryName) }
        val fromSeries = filteredSeriesList.mapNotNull { s ->
            if (s.categoryName.isNotBlank() && s.categoryId.isNotBlank()) {
                MobileCategoryItem(id = s.categoryId, name = s.categoryName)
            } else null
        }.distinctBy { it.id }
        val combined = (fromDb + fromSeries)
            .filter { it.id !in hiddenSeriesCategories && it.name !in hiddenSeriesCategories }
            .distinctBy { it.id }.sortedBy { it.name }
        listOf(MobileCategoryItem(id = "all", name = "Todas")) + combined
    }

    var newServerUrl by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    var selectedMovie by remember { mutableStateOf<VodMovie?>(null) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var isUpdateModalOpen by remember { mutableStateOf(false) }
    var extractTargetSource by remember { mutableStateOf<SourceConfig?>(null) }

    LaunchedEffect(activeStreamUrl) {
        activeStreamUrl?.let { url -> playerEngine.playStream(url, isLive = isLivePlayback) }
    }

    if (isUpdateModalOpen) {
        MobileUpdateModal(
            onDismiss = { isUpdateModalOpen = false }
        )
    }

    selectedMovie?.let { movie ->
        MobileMovieDetailDialog(
            movie = movie,
            activeSource = activeSource,
            onPlay = { url ->
                isLivePlayback = false
                activeChannelName = movie.name
                activeStreamUrl = url
                isPlayerFullscreen = true
                selectedMovie = null
            },
            onDismiss = { selectedMovie = null }
        )
    }

    selectedSeries?.let { series ->
        MobileSeriesDetailDialog(
            series = series,
            activeSource = activeSource,
            onFetchSeriesDetails = onFetchSeriesDetails,
            onPlayEpisode = { url, title ->
                isLivePlayback = false
                activeChannelName = title
                activeStreamUrl = url
                isPlayerFullscreen = true
                selectedSeries = null
            },
            onDismiss = { selectedSeries = null }
        )
    }

    BackHandler(enabled = selectedTab != 0 && !isPlayerFullscreen) {
        selectedTab = 0
    }

    var isCategoryManagerModalOpen by remember { mutableStateOf(false) }
    var categoryManagerInitialScope by remember { mutableStateOf(MobileCategoryScope.LIVE) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BixDarkBackground,
            topBar = {
                if (selectedTab != 0) {
                    MobileTopBar(
                        activeSource = activeSource,
                        showSearch = selectedTab != 4,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onSettingsTap = { selectedTab = 4 },
                        selectedTab = selectedTab,
                        onBackTap = { selectedTab = 0 }
                    )
                }
            },
            bottomBar = {
                if (!isPlayerFullscreen) {
                    MobileBottomNavBar(
                        selectedTab = selectedTab,
                        onTabChange = { selectedTab = it }
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (activeStreamUrl != null && !isPlayerFullscreen) {
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val context = LocalContext.current
                    MiniPlayerOverlay(
                        channelName = activeChannelName,
                        isLive = isLivePlayback,
                        playerEngine = playerEngine,
                        onToggleFullscreen = { isPlayerFullscreen = true },
                        onCopyUrl = {
                            activeStreamUrl?.let { url ->
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                                android.widget.Toast.makeText(context, "📋 Enlace copiado: $activeChannelName", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClose = { playerEngine.stop(); activeStreamUrl = null },
                        osdText = channelOsdText,
                        osdSubtext = channelOsdSubtext,
                        osdDirection = channelOsdDirection,
                        modifier = Modifier.swipeToChangeChannel(
                            enabled = isLivePlayback,
                            onNext = { switchChannelByOffset(1) },
                            onPrev = { switchChannelByOffset(-1) }
                        )
                    )
                }
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxSize(),
                    label = "tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> MobileHomeTab(
                            liveChannels = cleanLiveChannels, movies = filteredMovies, seriesList = filteredSeriesList,
                            activeSource = activeSource,
                            onChannelClick = { ch, url -> isPlayerFullscreen = true; isLivePlayback = true; activeChannelName = ch; activeStreamUrl = url },
                            onMovieClick = { selectedMovie = it },
                            onSeriesClick = { selectedSeries = it },
                            onSeeAllLive = { selectedTab = 2 },
                            onSeeAllMovies = { selectedTab = 1 },
                            onSeeAllSeries = { selectedTab = 1 },
                            onReloadCatalog = onForceSync,
                            onOpenSettings = { selectedTab = 4 }
                        )
                        1 -> MobileVodTab(
                            movies = if (searchQuery.isBlank()) filteredMovies
                                     else filteredMovies.filter { it.name.contains(searchQuery, ignoreCase = true) },
                            movieCategories = movieCategoryList,
                            onMovieClick = { selectedMovie = it },
                            seriesList = if (searchQuery.isBlank()) filteredSeriesList
                                         else filteredSeriesList.filter { it.name.contains(searchQuery, ignoreCase = true) },
                            seriesCategories = seriesCategoryList,
                            onSeriesClick = { selectedSeries = it }
                        )
                        2 -> MobileLiveTab(
                            channels = if (searchQuery.isBlank()) cleanLiveChannels
                                       else cleanLiveChannels.filter { it.name.contains(searchQuery, ignoreCase = true) },
                            activeSource = activeSource,
                            categories = liveCategoryList,
                            favoriteChannels = favoriteChannels,
                            onToggleFavorite = onToggleFavoriteChannel,
                            onChannelClick = { ch, url -> isPlayerFullscreen = true; isLivePlayback = true; activeChannelName = ch; activeStreamUrl = url }
                        )
                        3 -> MobileFavoritesTab(
                            favoriteChannels = favoriteChannels,
                            favoriteMovies = favoriteMovies,
                            activeSource = activeSource,
                            onChannelClick = { ch, url -> isPlayerFullscreen = true; isLivePlayback = true; activeChannelName = ch; activeStreamUrl = url },
                            onMovieClick = { selectedMovie = it },
                            onToggleFavoriteChannel = onToggleFavoriteChannel,
                            onToggleFavoriteMovie = onToggleFavoriteMovie
                        )
                        4 -> MobileSettingsTab(
                            activeSource = activeSource, allSources = allSources,
                            onActivateSource = onActivateSource, onDeleteSource = onDeleteSource,
                            onAddSourceSmart = { rawUrl, name ->
                                onAddSourceSmart?.invoke(rawUrl, name) ?: onAddSource(rawUrl, "", "", name)
                            },
                            onOpenExtractModal = { target ->
                                extractTargetSource = target
                            },
                            onSyncCloud = onSyncCloudSources, onForceSync = onForceSync, onLogout = onLogout,
                            onOpenUpdate = { isUpdateModalOpen = true },
                            onNavigateToFavorites = { selectedTab = 3 },
                            liveTotal = liveCategories.size,
                            liveHiddenCount = liveCategories.count { hiddenLiveCategories.contains(it.categoryName) || hiddenLiveCategories.contains(it.categoryId) },
                            moviesTotal = vodCategories.size,
                            moviesHiddenCount = vodCategories.count { hiddenMovieCategories.contains(it.categoryName) || hiddenMovieCategories.contains(it.categoryId) },
                            seriesTotal = seriesCategories.size,
                            seriesHiddenCount = seriesCategories.count { hiddenSeriesCategories.contains(it.categoryName) || hiddenSeriesCategories.contains(it.categoryId) },
                            onOpenCategoryManager = { scope ->
                                categoryManagerInitialScope = scope
                                isCategoryManagerModalOpen = true
                            }
                        )
                    }
                }
            }
        }

        // REPRODUCTOR HORIZONTAL A PANTALLA COMPLETA
        if (isPlayerFullscreen && activeStreamUrl != null) {
            var isOverlayVisible by remember { mutableStateOf(true) }

            // Auto-hide timer
            LaunchedEffect(isOverlayVisible, playbackState) {
                if (isOverlayVisible && playbackState is PlaybackState.Playing) {
                    kotlinx.coroutines.delay(4000L)
                    isOverlayVisible = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        isOverlayVisible = !isOverlayVisible
                    }
                    .swipeToChangeChannel(
                        enabled = isLivePlayback,
                        onNext = { switchChannelByOffset(1) },
                        onPrev = { switchChannelByOffset(-1) }
                    )
            ) {
                LelouchVideoPlayer(
                    playerEngine = playerEngine,
                    modifier = Modifier.fillMaxSize()
                )

                // OSD Flotante Neón en pantalla completa
                AnimatedVisibility(
                    visible = channelOsdText != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    ChannelChangeOsd(
                        channelName = channelOsdText.orEmpty(),
                        subtext = channelOsdSubtext.orEmpty(),
                        direction = channelOsdDirection.orEmpty()
                    )
                }

                // Barra Superior
                AnimatedVisibility(
                    visible = isOverlayVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = { isPlayerFullscreen = false },
                            modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = activeChannelName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val context = LocalContext.current
                        IconButton(
                            onClick = {
                                activeStreamUrl?.let { url ->
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                                    android.widget.Toast.makeText(context, "📋 Enlace copiado: $activeChannelName", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Copiar Enlace", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val cur = activity?.requestedOrientation
                                if (cur == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE || cur == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Girar Pantalla", tint = Color.White)
                        }
                        IconButton(
                            onClick = { isPlayerFullscreen = false },
                            modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "Minimizar", tint = Color.White)
                        }
                    }
                    }
                }

                // Barra Inferior para VOD / Películas / Series (Timeline, Adelantar, Velocidad)
                if (!isLivePlayback) {
                    val playbackState by playerEngine.playbackState.collectAsState()
                    val (currentPos, totalDur) = when (val ps = playbackState) {
                        is PlaybackState.Playing -> ps.positionMs to ps.durationMs
                        is PlaybackState.Paused -> ps.positionMs to ps.durationMs
                        else -> 0L to 0L
                    }
                    val isPlaying = playbackState is PlaybackState.Playing

                    AnimatedVisibility(
                        visible = isOverlayVisible,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                        // Barra de progreso y tiempos
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(currentPos),
                                color = Color.White.copy(0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = if (totalDur > 0) currentPos.toFloat().coerceIn(0f, totalDur.toFloat()) else 0f,
                                onValueChange = { targetPos ->
                                    playerEngine.seekTo(targetPos.toLong())
                                },
                                valueRange = 0f..(if (totalDur > 0) totalDur.toFloat() else 1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = LelouchCyanAccent,
                                    activeTrackColor = LelouchCyanAccent,
                                    inactiveTrackColor = Color.White.copy(0.3f)
                                )
                            )
                            Text(
                                text = formatDuration(totalDur),
                                color = Color.White.copy(0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Botones de transporte: -10s, Play/Pausa, +10s, Velocidad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Retroceso 10 segundos
                            IconButton(
                                onClick = { playerEngine.seekBy(-10000L) },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White.copy(0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Retroceder 10s", tint = Color.White)
                            }

                            Spacer(Modifier.width(20.dp))

                            // Play / Pausa
                            IconButton(
                                onClick = { playerEngine.togglePlayPause() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(LelouchCyanAccent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pausa",
                                    tint = Color.Black,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(Modifier.width(20.dp))

                            // Avance 10 segundos
                            IconButton(
                                onClick = { playerEngine.seekBy(10000L) },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White.copy(0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.FastForward, contentDescription = "Adelantar 10s", tint = Color.White)
                            }

                            Spacer(Modifier.width(24.dp))

                            // Selector de velocidad
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(0.15f))
                                    .clickable {
                                        currentPlaybackSpeed = when (currentPlaybackSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        playerEngine.setPlaybackSpeed(currentPlaybackSpeed)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${currentPlaybackSpeed}x",
                                    color = LelouchCyanAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPinDialog = false; pinInput = "" },
            title = { Text("Acceso a Ajustes", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Ingresa el PIN para gestionar listas y visibilidad de categorías:", color = Color.White.copy(0.7f))
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = LelouchCyanAccent,
                            cursorColor = LelouchCyanAccent
                        )
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (pinInput == "1990") {
                        showPinDialog = false
                        pinInput = ""
                        selectedTab = 4 // Direct to MobileSettingsTab instead of TvAdminPanelModal!
                    } else {
                        android.widget.Toast.makeText(context, "PIN Incorrecto", android.widget.Toast.LENGTH_SHORT).show()
                        pinInput = ""
                    }
                }) {
                    Text("Aceptar", color = LelouchCyanAccent)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPinDialog = false; pinInput = "" }) {
                    Text("Cancelar", color = LelouchTextSecondary)
                }
            },
            containerColor = LelouchSurface
        )
    }

    if (isCategoryManagerModalOpen) {
        MobileCategoryManagerModal(
            initialScope = categoryManagerInitialScope,
            liveCategories = liveCategories,
            hiddenLiveCategoryNames = hiddenLiveCategories,
            movieCategories = vodCategories,
            hiddenMovieCategoryNames = hiddenMovieCategories,
            seriesCategories = seriesCategories,
            hiddenSeriesCategoryNames = hiddenSeriesCategories,
            onToggleLiveCategory = { cat ->
                if (cat.categoryName in hiddenLiveCategories || cat.categoryId in hiddenLiveCategories) {
                    saveHiddenLive(hiddenLiveCategories - cat.categoryName - cat.categoryId)
                } else {
                    saveHiddenLive(hiddenLiveCategories + cat.categoryName + cat.categoryId)
                }
            },
            onToggleMovieCategory = { cat ->
                if (cat.categoryName in hiddenMovieCategories || cat.categoryId in hiddenMovieCategories) {
                    saveHiddenMovies(hiddenMovieCategories - cat.categoryName - cat.categoryId)
                } else {
                    saveHiddenMovies(hiddenMovieCategories + cat.categoryName + cat.categoryId)
                }
            },
            onToggleSeriesCategory = { cat ->
                if (cat.categoryName in hiddenSeriesCategories || cat.categoryId in hiddenSeriesCategories) {
                    saveHiddenSeries(hiddenSeriesCategories - cat.categoryName - cat.categoryId)
                } else {
                    saveHiddenSeries(hiddenSeriesCategories + cat.categoryName + cat.categoryId)
                }
            },
            onDismiss = { isCategoryManagerModalOpen = false }
        )
    }

    extractTargetSource?.let { targetSource ->
        val coroutineScope = rememberCoroutineScope()
        MobileExtractContentModal(
            source = targetSource,
            liveCategories = liveCategories,
            vodCategories = vodCategories,
            seriesCategories = seriesCategories,
            onExtract = { contentType, selectedCatNames ->
                coroutineScope.launch {
                    val count = onExtractContent?.invoke(targetSource.id, selectedCatNames, contentType) ?: 0
                    android.widget.Toast.makeText(
                        context,
                        "🎉 ¡Se extrajeron y añadieron $count elementos a Tu Lista LELOUCH!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    extractTargetSource = null
                }
            },
            onDismiss = { extractTargetSource = null }
        )
    }
}

// TOP BAR
@Composable
private fun MobileTopBar(
    activeSource: SourceConfig?, showSearch: Boolean,
    searchQuery: String, onSearchChange: (String) -> Unit, onSettingsTap: () -> Unit,
    selectedTab: Int = 0, onBackTap: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().background(LelouchBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (expanded) {
            BasicTextField(
                value = searchQuery, onValueChange = onSearchChange,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(LelouchSurface).padding(horizontal = 14.dp, vertical = 10.dp),
                singleLine = true,
                textStyle = TextStyle(color = LelouchTextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(LelouchCyanAccent),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("Buscar...", color = LelouchTextMuted, fontSize = 14.sp)
                    inner()
                }
            )
            IconButton(onClick = { expanded = false; onSearchChange("") }) {
                Icon(Icons.Default.Close, null, tint = LelouchTextSecondary)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (selectedTab != 0) {
                    IconButton(onClick = onBackTap) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = LelouchCyanAccent)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text("LELOUCH", color = LelouchTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
                if (activeSource != null) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(LelouchSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp) // Quitado clickable(onSettingsTap)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(Modifier.width(4.dp))
                            Text(activeSource.name, color = LelouchCyanAccent, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp))
                        }
                    }
                }
            }
            if (showSearch) {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.Search, null, tint = LelouchCyanAccent) }
            }
            // Quitado el IconButton de Settings
        }
    }
}

// BOTTOM NAV (ESTILO BIX PLUS - 5 PESTAÑAS)
@Composable
private fun MobileBottomNavBar(selectedTab: Int, onTabChange: (Int) -> Unit) {
    NavigationBar(
        containerColor = BixDarkBackground,
        tonalElevation = 10.dp
    ) {
        val navItems = listOf(
            Triple(Icons.Default.Home, "Inicio", 0),
            Triple(Icons.Default.Movie, "Películas", 1),
            Triple(Icons.Default.LiveTv, "En Vivo", 2),
            Triple(Icons.Default.Favorite, "Favoritos", 3),
            Triple(Icons.Default.Menu, "Más", 4)
        )
        navItems.forEach { (icon, label, index) ->
            val isSelected = selectedTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabChange(index) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BixOrangeAccent,
                    selectedTextColor = BixOrangeAccent,
                    indicatorColor = BixOrangeAccent.copy(alpha = 0.15f),
                    unselectedIconColor = BixTextMuted,
                    unselectedTextColor = BixTextMuted
                )
            )
        }
    }
}

// TAB 0: INICIO (PORTAL DASHBOARD FASE 32)
@Composable
private fun MobileHomeTab(
    liveChannels: List<LiveStream>, movies: List<VodMovie>, seriesList: List<Series>,
    activeSource: SourceConfig?,
    onChannelClick: (String, String) -> Unit, onMovieClick: (VodMovie) -> Unit,
    onSeriesClick: (Series) -> Unit, onSeeAllLive: () -> Unit,
    onSeeAllMovies: () -> Unit, onSeeAllSeries: () -> Unit,
    onReloadCatalog: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val sportsCount = remember(liveChannels) {
        liveChannels.count { ch ->
            val cat = ch.categoryName.lowercase()
            val name = ch.name.lowercase()
            cat.contains("deporte") || cat.contains("sport") || cat.contains("espn") || cat.contains("fox sport") || cat.contains("dazn") ||
            name.contains("espn") || name.contains("fox sport") || name.contains("tudn") || name.contains("dazn")
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            MobilePortalDashboard(
                activeSource = activeSource,
                liveChannelsCount = liveChannels.size,
                moviesCount = movies.size,
                seriesCount = seriesList.size,
                sportsCount = sportsCount,
                onNavigateToLive = onSeeAllLive,
                onNavigateToMovies = onSeeAllMovies,
                onNavigateToSeries = onSeeAllSeries,
                onNavigateToSports = onSeeAllLive,
                onDownloadM3U = {
                    android.widget.Toast.makeText(context, "Lista M3U lista para exportar", android.widget.Toast.LENGTH_SHORT).show()
                },
                onReloadCatalog = onReloadCatalog,
                onOpenSettings = onOpenSettings,
                liveChannels = liveChannels,
                movies = movies,
                seriesList = seriesList,
                onChannelClick = onChannelClick,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (liveChannels.isEmpty() && movies.isEmpty() && seriesList.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(
                            color = LelouchCyanAccent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Text("Sincronizando catálogo...", color = LelouchTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Obteniendo canales, películas y series de esta cuenta.", color = LelouchTextSecondary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onReloadCatalog,
                            colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔄 Sincronizar Ahora", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// TAB 1: EN VIVO
@Composable
private fun MobileLiveTab(
    channels: List<LiveStream>,
    activeSource: SourceConfig?,
    categories: List<MobileCategoryItem> = emptyList(),
    favoriteChannels: List<LiveStream> = emptyList(),
    onToggleFavorite: (Int, Boolean) -> Unit = { _, _ -> },
    onChannelClick: (String, String) -> Unit
) {
    if (channels.isEmpty() && favoriteChannels.isEmpty()) {
        EmptyState("No hay canales disponibles", "Sincroniza tu lista desde la pestaña Listas")
        return
    }
    var selectedCategoryId by remember { mutableStateOf("all") }
    val favIds = remember(favoriteChannels) { favoriteChannels.map { it.streamId }.toSet() }
    val selectedCat = categories.find { it.id == selectedCategoryId }
    val filtered = when (selectedCategoryId) {
        "all" -> channels
        "favs" -> channels.filter { it.streamId in favIds }
        else -> channels.filter {
            it.categoryId == selectedCategoryId ||
            (selectedCat != null && it.categoryName.equals(selectedCat.name, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra de categorías
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chip "⭐ Favoritos" — solo aparece si hay canales favoritos marcados
            if (favoriteChannels.isNotEmpty()) {
                item {
                    val isSel = selectedCategoryId == "favs"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) Color(0xFFFFD700) else LelouchSurface)
                            .clickable { selectedCategoryId = "favs" }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "⭐ Favoritos",
                            color = if (isSel) Color.Black else LelouchTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            // Chips de categorías normales
            if (categories.isNotEmpty()) {
                items(categories) { cat ->
                    val isSel = cat.id == selectedCategoryId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) LelouchCyanAccent else LelouchSurface)
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            cat.name,
                            color = if (isSel) Color.Black else LelouchTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(filtered, key = { idx, ch -> "mobile_live_${ch.streamId}_${ch.id}_$idx" }) { _, ch ->
                val url = StreamUrlResolver.resolveLive(ch.streamUrl, activeSource, ch.streamId)
                val isFav = ch.streamId in favIds
                ChannelListItem(
                    channel = ch,
                    isFavorite = isFav,
                    onToggleFavorite = { onToggleFavorite(ch.streamId, !isFav) },
                    onClick = { onChannelClick(ch.name, url) }
                )
            }
        }
    }
}

// TAB 2: PELICULAS
@Composable
private fun MobileMoviesTab(
    movies: List<VodMovie>,
    categories: List<MobileCategoryItem> = emptyList(),
    onMovieClick: (VodMovie) -> Unit
) {
    if (movies.isEmpty()) {
        EmptyState("No hay películas", "Sincroniza tu lista desde la pestaña Listas")
        return
    }
    var selectedCategoryId by remember { mutableStateOf("all") }
    val filtered = if (selectedCategoryId == "all") movies
                   else movies.filter { it.categoryId == selectedCategoryId }

    Column(modifier = Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSel = cat.id == selectedCategoryId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) LelouchCyanAccent else LelouchSurface)
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            cat.name,
                            color = if (isSel) Color.Black else LelouchTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(85.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(filtered, key = { idx, movie -> "mobile_movie_${movie.streamId}_${movie.id}_$idx" }) { _, movie ->
                MovieGridCard(movie = movie, onClick = { onMovieClick(movie) })
            }
        }
    }
}

// TAB 3: SERIES
@Composable
private fun MobileSeriesTab(
    seriesList: List<Series>,
    categories: List<MobileCategoryItem> = emptyList(),
    onSeriesClick: (Series) -> Unit
) {
    if (seriesList.isEmpty()) {
        EmptyState("No hay series", "Sincroniza tu lista desde la pestaña Listas")
        return
    }
    var selectedCategoryId by remember { mutableStateOf("all") }
    val filtered = if (selectedCategoryId == "all") seriesList
                   else seriesList.filter { it.categoryId == selectedCategoryId }

    Column(modifier = Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSel = cat.id == selectedCategoryId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) LelouchCyanAccent else LelouchSurface)
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            cat.name,
                            color = if (isSel) Color.Black else LelouchTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(85.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(filtered, key = { idx, s -> "mobile_series_${s.seriesId}_$idx" }) { _, s ->
                SeriesGridCard(series = s, onClick = { onSeriesClick(s) })
            }
        }
    }
}

// TAB 1: PELÍCULAS Y SERIES COMBINADO CON SUB-TABS ESTILO BIX PLUS
@Composable
private fun MobileVodTab(
    movies: List<VodMovie>,
    movieCategories: List<MobileCategoryItem>,
    onMovieClick: (VodMovie) -> Unit,
    seriesList: List<Series>,
    seriesCategories: List<MobileCategoryItem>,
    onSeriesClick: (Series) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) } // 0: Películas, 1: Series

    Column(modifier = Modifier.fillMaxSize().background(BixDarkBackground)) {
        // Selector tipo cápsula Bix Plus
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isMovies = subTab == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isMovies) BixOrangeAccent else BixSurfaceCard)
                    .border(1.dp, if (isMovies) BixOrangeAccent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .clickable { subTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Películas (${movies.size})",
                        color = Color.White,
                        fontWeight = if (isMovies) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            val isSeries = subTab == 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSeries) BixOrangeAccent else BixSurfaceCard)
                    .border(1.dp, if (isSeries) BixOrangeAccent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .clickable { subTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Series (${seriesList.size})",
                        color = Color.White,
                        fontWeight = if (isSeries) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (subTab == 0) {
            MobileMoviesTab(movies = movies, categories = movieCategories, onMovieClick = onMovieClick)
        } else {
            MobileSeriesTab(seriesList = seriesList, categories = seriesCategories, onSeriesClick = onSeriesClick)
        }
    }
}

// TAB 3: FAVORITOS (DISEÑO BIX PLUS CON EMPTY STATE EXACTO CAPTURA 8)
@Composable
private fun MobileFavoritesTab(
    favoriteChannels: List<LiveStream>,
    favoriteMovies: List<VodMovie>,
    activeSource: SourceConfig?,
    onChannelClick: (String, String) -> Unit,
    onMovieClick: (VodMovie) -> Unit,
    onToggleFavoriteChannel: (Int, Boolean) -> Unit,
    onToggleFavoriteMovie: (Int, Boolean) -> Unit
) {
    var favSubTab by remember { mutableIntStateOf(0) } // 0: Canales, 1: Películas

    if (favoriteChannels.isEmpty() && favoriteMovies.isEmpty()) {
        // EMPTY STATE EXACTO CAPTURA 8 BIX PLUS
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BixDarkBackground)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Círculo Naranja con Play
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(BixOrangeAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Text(
                    text = "No se encontró",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Cuando guardes títulos como favoritos aparecerán aquí.",
                    color = BixTextMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(BixDarkBackground)) {
        // Selector tipo cápsula para cambiar entre Canales y Películas Favoritas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isCh = favSubTab == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCh) BixOrangeAccent else BixSurfaceCard)
                    .clickable { favSubTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Canales (${favoriteChannels.size})",
                    color = Color.White,
                    fontWeight = if (isCh) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                )
            }

            val isMov = favSubTab == 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isMov) BixOrangeAccent else BixSurfaceCard)
                    .clickable { favSubTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Películas (${favoriteMovies.size})",
                    color = Color.White,
                    fontWeight = if (isMov) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }

        if (favSubTab == 0) {
            if (favoriteChannels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tienes canales favoritos guardados.", color = BixTextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favoriteChannels, key = { "fav_ch_${it.streamId}" }) { ch ->
                        val url = StreamUrlResolver.resolveLive(ch.streamUrl, activeSource, ch.streamId)
                        ChannelListItem(
                            channel = ch,
                            isFavorite = true,
                            onToggleFavorite = { onToggleFavoriteChannel(ch.streamId, false) },
                            onClick = { onChannelClick(ch.name, url) }
                        )
                    }
                }
            }
        } else {
            if (favoriteMovies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tienes películas favoritas guardadas.", color = BixTextMuted, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(85.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(favoriteMovies, key = { "fav_mov_${it.streamId}" }) { mov ->
                        MovieGridCard(movie = mov, onClick = { onMovieClick(mov) })
                    }
                }
            }
        }
    }
}

// TAB 4: AJUSTES/OPCIONES (ESTILO BIX PLUS FASE 35)
@Composable
private fun MobileSettingsTab(
    activeSource: SourceConfig?, allSources: List<SourceConfig>,
    onActivateSource: (String) -> Unit, onDeleteSource: (String) -> Unit,
    onAddSourceSmart: (String, String) -> Unit,
    onOpenExtractModal: (SourceConfig) -> Unit,
    onSyncCloud: () -> Unit, onForceSync: () -> Unit, onLogout: () -> Unit,
    onOpenUpdate: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    liveTotal: Int, liveHiddenCount: Int,
    moviesTotal: Int, moviesHiddenCount: Int,
    seriesTotal: Int, seriesHiddenCount: Int,
    onOpenCategoryManager: (MobileCategoryScope) -> Unit
) {
    var adultModeEnabled by remember { mutableStateOf(false) }
    var seriesOptimizedView by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BixDarkBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. CABECERA: TÍTULO "Opciones"
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Opciones",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp
                )
                IconButton(onClick = onForceSync) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar opciones",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2. TRES ACCESOS DIRECTOS CIRCULARES DE COLOR (BIX PLUS)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🟢 Actualizaciones
                CircleActionItem(
                    icon = Icons.Default.Refresh,
                    label = "Actualizaciones",
                    bgColor = BixCircleGreen,
                    onClick = onOpenUpdate
                )
                // 💖 Favoritos
                CircleActionItem(
                    icon = Icons.Default.Favorite,
                    label = "Favoritos",
                    bgColor = BixCirclePink,
                    onClick = onNavigateToFavorites
                )
                // 🔵 Historial
                CircleActionItem(
                    icon = Icons.Default.History,
                    label = "Historial",
                    bgColor = BixCircleBlue,
                    onClick = onNavigateToFavorites
                )
            }
        }

        // 3. SECCIÓN PREFERENCIAS (ESTILO BIX PLUS)
        item {
            Text(
                text = "PREFERENCIAS",
                color = BixTextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BixSurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Item 1: Mostrar modo +18
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCategoryManager(MobileCategoryScope.LIVE) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mostrar modo +18 / Categorías",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Gestiona la visibilidad de canales y categorías.",
                                color = BixTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = adultModeEnabled,
                            onCheckedChange = {
                                adultModeEnabled = it
                                onOpenCategoryManager(MobileCategoryScope.LIVE)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BixOrangeAccent,
                                uncheckedThumbColor = Color.White.copy(0.6f),
                                uncheckedTrackColor = Color.White.copy(0.15f)
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    // Item 2: Vista optimizada de series
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vista optimizada de series",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Agrupa episodios en temporadas para navegar más rápido.",
                                color = BixTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = seriesOptimizedView,
                            onCheckedChange = { seriesOptimizedView = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BixOrangeAccent,
                                uncheckedThumbColor = Color.White.copy(0.6f),
                                uncheckedTrackColor = Color.White.copy(0.15f)
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    // Botones rápidos Cloud Sync
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncCloud,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("☁️ Nube", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onForceSync,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🔄 Recargar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    // Botón para administrar categorías ocultas
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCategoryManager(MobileCategoryScope.LIVE) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = BixOrangeAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Categorías Ocultas",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Ocultos: $liveHiddenCount TV • $moviesHiddenCount Pelis • $seriesHiddenCount Series",
                                    color = BixTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = BixTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                "LISTAS REGISTRADAS (${allSources.size})",
                color = BixTextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
        items(allSources) { source ->
            val isActive = source.id == activeSource?.id
            val daysRem = source.expireDaysRemaining
            val isCustom = source.type == SourceType.M3U || source.id.startsWith("custom_")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) LelouchSurfaceVariant else LelouchSurface)
                    .border(
                        if (isActive) 1.5.dp else 1.dp,
                        if (isActive) Color(0xFF10B981) else LelouchBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // FILA 1: Nombre con elipsis + Badges de estado alineados
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = source.name,
                            color = LelouchTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "✓ EN USO",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "☁️ Nube",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // FILA 2: Badges de Expiración y Conexiones Simultáneas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCustom || source.expireDate.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "♾️ Ilimitado",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (daysRem != null) {
                            val badgeColor = when {
                                daysRem <= 0 -> Color(0xFFEF4444)
                                daysRem <= 15 -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                            val badgeText = when {
                                daysRem <= 0 -> "🔴 Venció: ${source.formattedExpireDate}"
                                daysRem <= 15 -> "⚠️ Vence: ${source.formattedExpireDate} (${daysRem}d)"
                                else -> "📅 Vence: ${source.formattedExpireDate} (${daysRem} días)"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    badgeText,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (source.maxConnections > 0 && !isCustom) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "👥 ${source.activeConnections}/${source.maxConnections} Conex.",
                                    color = Color(0xFFC084FC),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // FILA 3: Servidor y Usuario
                    Text(
                        text = "🌐 ${source.serverUrl}  •  👤 ${source.username.ifBlank { "—" }}",
                        color = LelouchTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // FILA 4: Botones de Acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "✓ En uso",
                                        color = Color(0xFF10B981),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { onActivateSource(source.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "⚡ Activar",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    onActivateSource(source.id)
                                    onForceSync()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LelouchBorder)
                            ) {
                                Text("🔄 Recargar", color = LelouchTextPrimary, fontSize = 11.sp)
                            }

                            if (!isCustom) {
                                OutlinedButton(
                                    onClick = { onOpenExtractModal(source) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, LelouchCyanAccent.copy(alpha = 0.5f))
                                ) {
                                    Text("📥 Extraer", color = LelouchCyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (allSources.size > 1) {
                            IconButton(
                                onClick = { onDeleteSource(source.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = LelouchLiveRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECCIÓN AÑADIR NUEVA LISTA (ENLACE INTELIGENTE SMART URL)
        item {
            var smartUrl by remember { mutableStateOf("") }
            var smartName by remember { mutableStateOf("") }
            var isSubmitting by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LelouchSurface)
                    .border(1.dp, LelouchCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "➕ Añadir Nueva Lista (Enlace Inteligente)",
                        color = LelouchTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(
                        "Pega tu enlace M3U o Xtream Codes completo (como en Web Player). Detecta servidor, usuario y contraseña automáticamente.",
                        color = LelouchTextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = smartUrl,
                        onValueChange = { smartUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Pega enlace M3U / Xtream completo...", fontSize = 12.sp, color = LelouchTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LelouchCyanAccent,
                            unfocusedBorderColor = LelouchBorder,
                            focusedTextColor = LelouchTextPrimary,
                            unfocusedTextColor = LelouchTextPrimary,
                            focusedContainerColor = LelouchBackground,
                            unfocusedContainerColor = LelouchBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = smartName,
                        onValueChange = { smartName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nombre de la cuenta (opcional)", fontSize = 12.sp, color = LelouchTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LelouchCyanAccent,
                            unfocusedBorderColor = LelouchBorder,
                            focusedTextColor = LelouchTextPrimary,
                            unfocusedTextColor = LelouchTextPrimary,
                            focusedContainerColor = LelouchBackground,
                            unfocusedContainerColor = LelouchBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (smartUrl.isNotBlank()) {
                                isSubmitting = true
                                onAddSourceSmart(smartUrl, smartName)
                                smartUrl = ""
                                smartName = ""
                                isSubmitting = false
                            }
                        },
                        enabled = smartUrl.isNotBlank() && !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LelouchCyanAccent,
                            disabledContainerColor = LelouchSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "⚡ Conectar y Guardar Lista",
                            color = if (smartUrl.isNotBlank()) Color.Black else LelouchTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hrs = totalSec / 3600
    return if (hrs > 0) {
        "%d:%02d:%02d".format(hrs, min, sec)
    } else {
        "%02d:%02d".format(min, sec)
    }
}

// GESTO SWIPE TÁCTIL PARA CAMBIO DE CANAL
fun Modifier.swipeToChangeChannel(
    enabled: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit
): Modifier = if (!enabled) this else this.pointerInput(Unit) {
    var totalX = 0f
    var totalY = 0f
    detectDragGestures(
        onDragStart = {
            totalX = 0f
            totalY = 0f
        },
        onDragEnd = {
            val minThreshold = 45.dp.toPx()
            if (kotlin.math.abs(totalY) > kotlin.math.abs(totalX)) {
                if (totalY < -minThreshold) {
                    onNext()
                } else if (totalY > minThreshold) {
                    onPrev()
                }
            } else {
                if (totalX < -minThreshold) {
                    onNext()
                } else if (totalX > minThreshold) {
                    onPrev()
                }
            }
        },
        onDrag = { change, dragAmount ->
            change.consume()
            totalX += dragAmount.x
            totalY += dragAmount.y
        }
    )
}

// OSD FLOTANTE DE CAMBIO DE CANAL
@Composable
private fun ChannelChangeOsd(
    channelName: String,
    subtext: String,
    direction: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.88f))
            .border(1.5.dp, LelouchCyanAccent, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📺",
                    fontSize = 18.sp
                )
                Text(
                    text = direction,
                    color = LelouchCyanAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = channelName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtext.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtext,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// MINI PLAYER
@Composable
private fun MiniPlayerOverlay(
    channelName: String,
    isLive: Boolean = true,
    playerEngine: com.lelouch.core.player.LelouchPlayerEngine,
    onToggleFullscreen: () -> Unit,
    onCopyUrl: () -> Unit,
    onClose: () -> Unit,
    osdText: String? = null,
    osdSubtext: String? = null,
    osdDirection: String? = null,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLandscape) Modifier.height(180.dp) else Modifier.aspectRatio(16f / 9f))
            .background(Color.Black)
    ) {
        LelouchVideoPlayer(playerEngine = playerEngine, modifier = Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = osdText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ChannelChangeOsd(
                channelName = osdText.orEmpty(),
                subtext = osdSubtext.orEmpty(),
                direction = osdDirection.orEmpty()
            )
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onCopyUrl,
                modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Copiar Enlace", tint = Color.White)
            }
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla Completa", tint = Color.White)
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(0.7f)).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(channelName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isLive) LelouchLiveRed else LelouchCyanAccent)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isLive) "EN VIVO" else "VOD",
                    color = if (isLive) Color.White else Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// DIALOGOS DE DETALLE
@Composable
private fun MobileMovieDetailDialog(
    movie: VodMovie, activeSource: SourceConfig?,
    onPlay: (String) -> Unit, onDismiss: () -> Unit
) {
    val streamUrl = StreamUrlResolver.resolve(
        directUrl = movie.streamUrl,
        source = activeSource,
        streamId = movie.streamId,
        kind = StreamUrlResolver.Kind.VOD,
        extension = movie.containerExtension
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(16.dp)).background(LelouchBackground)) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(LelouchSurfaceVariant)) {
                    if (!movie.streamIcon.isNullOrBlank()) {
                        AsyncImage(model = movie.streamIcon, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, LelouchBackground))))
                    }
                    IconButton(onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .background(Color.Black.copy(0.5f), CircleShape)) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(movie.name, color = LelouchTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val rating = movie.rating ?: 0.0
                        if (rating > 0) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE5A00D)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("%.1f".format(rating), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!movie.year.isNullOrBlank()) Text(movie.year!!, color = LelouchTextSecondary, fontSize = 12.sp)
                        if (!movie.genre.isNullOrBlank()) Text(movie.genre!!, color = LelouchCyanAccent, fontSize = 12.sp)
                    }
                    if (!movie.plot.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(movie.plot!!, color = LelouchTextSecondary, fontSize = 13.sp,
                            maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val context = LocalContext.current
                        Button(
                            onClick = { onPlay(streamUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                            modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("REPRODUCIR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                try {
                                    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                                    if (dm != null && streamUrl.isNotBlank()) {
                                        val cleanName = movie.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                        val ext = if (movie.containerExtension.isNotBlank()) movie.containerExtension else "mp4"
                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(streamUrl))
                                            .setTitle(movie.name)
                                            .setDescription("Descargando película...")
                                            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "${cleanName}.${ext}")
                                            .setAllowedOverMetered(true)
                                            .setAllowedOverRoaming(true)
                                        dm.enqueue(request)
                                        android.widget.Toast.makeText(context, "📥 Descarga iniciada: ${movie.name}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error al descargar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LelouchSurfaceVariant),
                            border = BorderStroke(1.dp, LelouchCyanAccent.copy(alpha = 0.5f)),
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Descargar", tint = LelouchCyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("DESCARGAR", color = LelouchCyanAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = {
                                if (streamUrl.isNotBlank()) {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(streamUrl))
                                    android.widget.Toast.makeText(context, "📋 Enlace copiado: ${movie.name}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(LelouchSurfaceVariant).size(44.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Copiar Enlace", tint = LelouchCyanAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSeriesDetailDialog(
    series: Series,
    activeSource: SourceConfig?,
    onFetchSeriesDetails: (suspend (seriesId: Int) -> Pair<List<Int>, List<Episode>>)? = null,
    onPlayEpisode: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(series.seriesId) {
        isLoading = true
        try {
            if (onFetchSeriesDetails != null) {
                val (seasonList, epList) = onFetchSeriesDetails(series.seriesId)
                episodes = epList
                val sList = if (seasonList.isNotEmpty()) seasonList else epList.map { it.seasonNumber }.distinct().sorted()
                selectedSeason = sList.firstOrNull() ?: 1
            } else {
                episodes = emptyList()
            }
        } catch (_: Exception) {
            episodes = emptyList()
        } finally {
            isLoading = false
        }
    }

    val seasons = remember(episodes) {
        episodes.map { it.seasonNumber }.distinct().sorted()
    }
    val currentEpisodes = remember(episodes, selectedSeason) {
        if (selectedSeason != null) {
            episodes.filter { it.seasonNumber == selectedSeason }
        } else {
            episodes
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(16.dp)).background(LelouchBackground)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(LelouchSurfaceVariant)) {
                    if (!series.cover.isNullOrBlank()) {
                        AsyncImage(
                            model = series.cover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, LelouchBackground))
                            )
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .background(Color.Black.copy(0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(series.name, color = LelouchTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val r = series.rating5based ?: 0.0
                        if (r > 0) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE5A00D)).padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("%.1f".format(r), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!series.genre.isNullOrBlank()) Text(series.genre!!, color = LelouchCyanAccent, fontSize = 12.sp)
                        if (series.seasonsCount > 0) Text("${series.seasonsCount} Temporadas", color = LelouchTextSecondary, fontSize = 12.sp)
                    }
                    if (!series.plot.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            series.plot!!,
                            color = LelouchTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = LelouchCyanAccent, modifier = Modifier.size(32.dp))
                        }
                    } else if (episodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(LelouchSurface).border(1.dp, LelouchBorder, RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No se encontraron episodios disponibles", color = LelouchTextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        // Selector de Temporadas
                        if (seasons.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                items(seasons) { sNum ->
                                    val isSelected = sNum == selectedSeason
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isSelected) LelouchCyanAccent else LelouchSurface)
                                            .border(1.dp, if (isSelected) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(14.dp))
                                            .clickable { selectedSeason = sNum }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Temporada $sNum",
                                            color = if (isSelected) Color.Black else LelouchTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Lista de Episodios
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentEpisodes, key = { it.episodeId }) { ep ->
                                val streamUrl = StreamUrlResolver.resolve(
                                    directUrl = ep.streamUrl,
                                    source = activeSource,
                                    streamId = ep.episodeId,
                                    kind = StreamUrlResolver.Kind.SERIES,
                                    extension = ep.containerExtension.ifBlank { "mp4" }
                                )
                                val epTitle = "T${ep.seasonNumber}E${ep.episodeNumber}: ${ep.title.ifBlank { "Episodio ${ep.episodeNumber}" }}"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LelouchSurface)
                                        .border(1.dp, LelouchBorder, RoundedCornerShape(10.dp))
                                        .clickable { onPlayEpisode(streamUrl, "${series.name} - $epTitle") }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = epTitle,
                                            color = LelouchTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (ep.durationFormatted.isNotBlank()) {
                                            Text(
                                                text = ep.durationFormatted,
                                                color = LelouchTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                                                    if (dm != null && streamUrl.isNotBlank()) {
                                                        val cleanName = "${series.name}_${epTitle}".replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                                        val ext = if (ep.containerExtension.isNotBlank()) ep.containerExtension else "mp4"
                                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(streamUrl))
                                                            .setTitle("${series.name} - $epTitle")
                                                            .setDescription("Descargando episodio...")
                                                            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "${cleanName}.${ext}")
                                                            .setAllowedOverMetered(true)
                                                            .setAllowedOverRoaming(true)
                                                        dm.enqueue(request)
                                                        android.widget.Toast.makeText(context, "📥 Descargando: $epTitle", android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "Error al descargar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Descargar Episodio", tint = LelouchCyanAccent, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                if (streamUrl.isNotBlank()) {
                                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(streamUrl))
                                                    android.widget.Toast.makeText(context, "📋 Enlace copiado: $epTitle", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Copiar Enlace", tint = LelouchTextSecondary, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { onPlayEpisode(streamUrl, "${series.name} - $epTitle") },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(LelouchCyanAccent)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = Color.Black, modifier = Modifier.size(20.dp))
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

// TARJETAS
@Composable
private fun LiveChannelCard(channel: LiveStream, onClick: () -> Unit) {
    Box(modifier = Modifier.width(140.dp).height(85.dp).clip(RoundedCornerShape(10.dp))
        .background(LelouchSurfaceVariant).clickable(onClick = onClick).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(width = 44.dp, height = 28.dp).clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
                if (!channel.streamIcon.isNullOrBlank()) {
                    AsyncImage(model = channel.streamIcon, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text(channel.name.take(3).uppercase(), color = LelouchCyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(LelouchLiveRed)
                .padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("LIVE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(channel.name, color = LelouchTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Toca para ver", color = LelouchCyanAccent, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MovieCard(movie: VodMovie, onClick: () -> Unit) {
    Box(modifier = Modifier.width(110.dp).height(160.dp).clip(RoundedCornerShape(10.dp))
        .background(LelouchSurface).clickable(onClick = onClick)) {
        if (!movie.streamIcon.isNullOrBlank()) {
            AsyncImage(model = movie.streamIcon, contentDescription = movie.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(LelouchSurfaceVariant),
                contentAlignment = Alignment.Center) {
                Text(movie.name.take(2).uppercase(), color = LelouchCyanAccent, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(movie.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

@Composable
private fun SeriesCard(series: Series, onClick: () -> Unit) {
    Box(modifier = Modifier.width(110.dp).height(160.dp).clip(RoundedCornerShape(10.dp))
        .background(LelouchSurface).clickable(onClick = onClick)) {
        if (!series.cover.isNullOrBlank()) {
            AsyncImage(model = series.cover, contentDescription = series.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(LelouchSurfaceVariant),
                contentAlignment = Alignment.Center) {
                Text(series.name.take(2).uppercase(), color = LelouchCyanAccent, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(series.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

@Composable
private fun MovieGridCard(movie: VodMovie, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(12.dp))
        .background(LelouchSurface).clickable(onClick = onClick)) {
        if (!movie.streamIcon.isNullOrBlank()) {
            AsyncImage(model = movie.streamIcon, contentDescription = movie.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(LelouchSurfaceVariant),
                contentAlignment = Alignment.Center) {
                Text(movie.name.take(2).uppercase(), color = LelouchCyanAccent, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
        val rating = movie.rating ?: 0.0
        if (rating > 0) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.75f))
                .padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text("%.1f".format(rating), color = Color(0xFFFFD700), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(movie.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!movie.year.isNullOrBlank()) {
                Text(movie.year!!, color = Color.White.copy(0.6f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SeriesGridCard(series: Series, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(12.dp))
        .background(LelouchSurface).clickable(onClick = onClick)) {
        if (!series.cover.isNullOrBlank()) {
            AsyncImage(model = series.cover, contentDescription = series.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(LelouchSurfaceVariant),
                contentAlignment = Alignment.Center) {
                Text(series.name.take(2).uppercase(), color = LelouchCyanAccent, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
        val r = series.rating5based ?: 0.0
        if (r > 0) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.75f))
                .padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text("%.1f".format(r), color = Color(0xFFFFD700), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(series.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!series.genre.isNullOrBlank()) {
                Text(series.genre!!, color = LelouchCyanAccent, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChannelListItem(
    channel: LiveStream,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFavorite) LelouchSurface.copy(alpha = 0.95f) else LelouchSurface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = channel.streamIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    channel.name.take(3).uppercase(),
                    color = LelouchCyanAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = LelouchTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.categoryName.isNotBlank()) {
                Text(channel.categoryName, color = LelouchTextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Botón favorito: toca la estrella para agregar/quitar de favoritos
        if (onToggleFavorite != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isFavorite) Color(0xFFFFD700).copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFavorite) "⭐" else "☆",
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(LelouchLiveRed)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SeeMoreCard(onClick: () -> Unit) {
    Box(modifier = Modifier.width(80.dp).height(85.dp).clip(RoundedCornerShape(10.dp))
        .background(LelouchSurface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ArrowForward, null, tint = LelouchCyanAccent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text("Ver mas", color = LelouchCyanAccent, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, onSeeAll: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = LelouchTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        TextButton(onClick = onSeeAll, colors = ButtonDefaults.textButtonColors(contentColor = LelouchCyanAccent)) {
            Text("Ver todos ($count)", fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(title, color = LelouchTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = LelouchTextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MobileActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = LelouchSurfaceVariant),
        shape = RoundedCornerShape(8.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun MobileDangerButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = LelouchLiveRed.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp)) {
        Text(label, fontSize = 11.sp, color = LelouchLiveRed, maxLines = 1)
    }
}

@Composable
private fun CircleActionItem(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
