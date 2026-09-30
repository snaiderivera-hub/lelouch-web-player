package com.lelouch.feature.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.network.XtreamUrlBuilder
import com.lelouch.core.player.LelouchVideoPlayer
import com.lelouch.core.player.rememberLelouchPlayer

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
    onSyncCloudSources: () -> Unit = {},
    onForceSync: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var activeChannelName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    val playerEngine = rememberLelouchPlayer()

    val context = LocalContext.current
    val activity = context as? Activity

    // Rotación automática horizontal para reproducción a pantalla completa en teléfonos
    DisposableEffect(isPlayerFullscreen) {
        if (isPlayerFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = isPlayerFullscreen) {
        isPlayerFullscreen = false
    }

    val liveCategoryList = remember(liveCategories, liveChannels) {
        val fromDb = liveCategories.map { MobileCategoryItem(id = it.categoryId, name = it.categoryName) }
        val fromChannels = liveChannels.mapNotNull { ch ->
            if (ch.categoryName.isNotBlank() && ch.categoryId.isNotBlank()) {
                MobileCategoryItem(id = ch.categoryId, name = ch.categoryName)
            } else null
        }.distinctBy { it.id }
        val combined = (fromDb + fromChannels).distinctBy { it.id }.sortedBy { it.name }
        listOf(MobileCategoryItem(id = "all", name = "Todos")) + combined
    }

    val movieCategoryList = remember(vodCategories, movies) {
        val fromDb = vodCategories.map { MobileCategoryItem(id = it.categoryId, name = it.categoryName) }
        val fromMovies = movies.mapNotNull { mov ->
            if (mov.categoryName.isNotBlank() && mov.categoryId.isNotBlank()) {
                MobileCategoryItem(id = mov.categoryId, name = mov.categoryName)
            } else null
        }.distinctBy { it.id }
        val combined = (fromDb + fromMovies).distinctBy { it.id }.sortedBy { it.name }
        listOf(MobileCategoryItem(id = "all", name = "Todas")) + combined
    }

    val seriesCategoryList = remember(seriesCategories, seriesList) {
        val fromDb = seriesCategories.map { MobileCategoryItem(id = it.categoryId, name = it.categoryName) }
        val fromSeries = seriesList.mapNotNull { s ->
            if (s.categoryName.isNotBlank() && s.categoryId.isNotBlank()) {
                MobileCategoryItem(id = s.categoryId, name = s.categoryName)
            } else null
        }.distinctBy { it.id }
        val combined = (fromDb + fromSeries).distinctBy { it.id }.sortedBy { it.name }
        listOf(MobileCategoryItem(id = "all", name = "Todas")) + combined
    }

    var newServerUrl by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    var selectedMovie by remember { mutableStateOf<VodMovie?>(null) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var isUpdateModalOpen by remember { mutableStateOf(false) }

    LaunchedEffect(activeStreamUrl) {
        activeStreamUrl?.let { url -> playerEngine.playStream(url, isLive = true) }
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
                playerEngine.playStream(url, isLive = false)
                activeStreamUrl = url
                activeChannelName = movie.name
                selectedMovie = null
            },
            onDismiss = { selectedMovie = null }
        )
    }

    selectedSeries?.let { series ->
        MobileSeriesDetailDialog(series = series, onDismiss = { selectedSeries = null })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!isPlayerFullscreen) {
                    MobileBottomNavBar(selectedTab = selectedTab, onTabChange = {
                        selectedTab = it
                        searchQuery = ""
                    })
                }
            },
            containerColor = LelouchBackground
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (activeStreamUrl != null && !isPlayerFullscreen) {
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val context = LocalContext.current
                    MiniPlayerOverlay(
                        channelName = activeChannelName,
                        playerEngine = playerEngine,
                        onToggleFullscreen = { isPlayerFullscreen = true },
                        onCopyUrl = {
                            activeStreamUrl?.let { url ->
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                                android.widget.Toast.makeText(context, "📋 Enlace copiado: $activeChannelName", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClose = { playerEngine.stop(); activeStreamUrl = null }
                    )
                }
                MobileTopBar(
                    activeSource = activeSource,
                    showSearch = selectedTab != 4,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onSettingsTap = { selectedTab = 4 }
                )
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxSize(),
                    label = "tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> MobileHomeTab(
                            liveChannels = liveChannels, movies = movies, seriesList = seriesList,
                            activeSource = activeSource,
                            onChannelClick = { ch, url -> activeChannelName = ch; activeStreamUrl = url },
                            onMovieClick = { selectedMovie = it },
                            onSeriesClick = { selectedSeries = it },
                            onSeeAllLive = { selectedTab = 1 },
                            onSeeAllMovies = { selectedTab = 2 },
                            onSeeAllSeries = { selectedTab = 3 }
                        )
                        1 -> MobileLiveTab(
                            channels = if (searchQuery.isBlank()) liveChannels
                                       else liveChannels.filter { it.name.contains(searchQuery, ignoreCase = true) },
                            activeSource = activeSource,
                            categories = liveCategoryList,
                            favoriteChannels = favoriteChannels,
                            onToggleFavorite = onToggleFavoriteChannel,
                            onChannelClick = { ch, url -> activeChannelName = ch; activeStreamUrl = url }
                        )
                        2 -> MobileMoviesTab(
                            movies = if (searchQuery.isBlank()) movies
                                     else movies.filter { it.name.contains(searchQuery, ignoreCase = true) },
                            categories = movieCategoryList,
                            onMovieClick = { selectedMovie = it }
                        )
                        3 -> MobileSeriesTab(
                            seriesList = if (searchQuery.isBlank()) seriesList
                                         else seriesList.filter { it.name.contains(searchQuery, ignoreCase = true) },
                            categories = seriesCategoryList,
                            onSeriesClick = { selectedSeries = it }
                        )
                    4 -> MobileSettingsTab(
                        activeSource = activeSource, allSources = allSources,
                        newServerUrl = newServerUrl, newUsername = newUsername,
                        newPassword = newPassword, newName = newName,
                        onNewServerUrl = { newServerUrl = it }, onNewUsername = { newUsername = it },
                        onNewPassword = { newPassword = it }, onNewName = { newName = it },
                        onActivateSource = onActivateSource, onDeleteSource = onDeleteSource,
                        onAddSource = { s, u, p, n ->
                            onAddSource(s, u, p, n)
                            newServerUrl = ""; newUsername = ""; newPassword = ""; newName = ""
                        },
                        onSyncCloud = onSyncCloudSources, onForceSync = onForceSync, onLogout = onLogout,
                        onOpenUpdate = { isUpdateModalOpen = true }
                    )
                }
            }
        }
    }

        // REPRODUCTOR HORIZONTAL A PANTALLA COMPLETA
        if (isPlayerFullscreen && activeStreamUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                LelouchVideoPlayer(
                    playerEngine = playerEngine,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.8f), Color.Transparent)))
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
                            onClick = { isPlayerFullscreen = false },
                            modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "Minimizar", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// TOP BAR
@Composable
private fun MobileTopBar(
    activeSource: SourceConfig?, showSearch: Boolean,
    searchQuery: String, onSearchChange: (String) -> Unit, onSettingsTap: () -> Unit
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
                Text("LELOUCH", color = LelouchTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
                if (activeSource != null) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(LelouchSurfaceVariant)
                            .clickable(onClick = onSettingsTap).padding(horizontal = 8.dp, vertical = 3.dp)
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
            IconButton(onClick = onSettingsTap) { Icon(Icons.Default.Settings, null, tint = LelouchTextSecondary) }
        }
    }
}

// BOTTOM NAV
@Composable
private fun MobileBottomNavBar(selectedTab: Int, onTabChange: (Int) -> Unit) {
    NavigationBar(containerColor = LelouchSurface, tonalElevation = 8.dp) {
        listOf(
            Icons.Default.Home to "Inicio",
            Icons.Default.LiveTv to "En Vivo",
            Icons.Default.Movie to "Peliculas",
            Icons.Default.Tv to "Series",
            Icons.Default.Settings to "Listas"
        ).forEachIndexed { index, (icon, label) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabChange(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, maxLines = 1, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = LelouchCyanAccent, selectedTextColor = LelouchCyanAccent,
                    indicatorColor = LelouchSurfaceVariant,
                    unselectedIconColor = LelouchTextSecondary, unselectedTextColor = LelouchTextSecondary
                )
            )
        }
    }
}

// TAB 0: INICIO
@Composable
private fun MobileHomeTab(
    liveChannels: List<LiveStream>, movies: List<VodMovie>, seriesList: List<Series>,
    activeSource: SourceConfig?,
    onChannelClick: (String, String) -> Unit, onMovieClick: (VodMovie) -> Unit,
    onSeriesClick: (Series) -> Unit, onSeeAllLive: () -> Unit,
    onSeeAllMovies: () -> Unit, onSeeAllSeries: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            val featuredMovie = movies.firstOrNull()
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp)).background(LelouchSurfaceVariant)
            ) {
                if (featuredMovie != null && !featuredMovie.streamIcon.isNullOrBlank()) {
                    AsyncImage(model = featuredMovie.streamIcon, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.88f)))))
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(LelouchLiveRed)
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("DESTACADO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(featuredMovie?.name ?: "Bienvenido a LELOUCH", color = Color.White,
                        fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Tu centro multimedia personal", color = Color.White.copy(0.7f), fontSize = 12.sp)
                }
                if (featuredMovie != null) {
                    IconButton(
                        onClick = { onMovieClick(featuredMovie) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(LelouchCyanAccent, CircleShape)
                    ) { Icon(Icons.Default.PlayArrow, null, tint = Color.Black) }
                }
            }
        }
        if (liveChannels.isNotEmpty()) {
            item {
                SectionHeader("Canales en Vivo", liveChannels.size, onSeeAllLive)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(liveChannels.take(15)) { ch ->
                        val url = activeSource?.let {
                            XtreamUrlBuilder.buildLiveStreamUrl(it.serverUrl, it.username, it.password, ch.streamId, "m3u8")
                        } ?: ""
                        LiveChannelCard(channel = ch, onClick = { onChannelClick(ch.name, url) })
                    }
                    item { SeeMoreCard(onClick = onSeeAllLive) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (movies.isNotEmpty()) {
            item {
                SectionHeader("Peliculas", movies.size, onSeeAllMovies)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(movies.take(15)) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                    }
                    item { SeeMoreCard(onClick = onSeeAllMovies) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (seriesList.isNotEmpty()) {
            item {
                SectionHeader("Series", seriesList.size, onSeeAllSeries)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seriesList.take(15)) { series ->
                        SeriesCard(series = series, onClick = { onSeriesClick(series) })
                    }
                    item { SeeMoreCard(onClick = onSeeAllSeries) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (liveChannels.isEmpty() && movies.isEmpty() && seriesList.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sincronizando catalogo...", color = LelouchTextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Ve a Listas > Sync si tarda demasiado", color = LelouchTextMuted, fontSize = 12.sp)
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
    val filtered = when (selectedCategoryId) {
        "all" -> channels
        "favs" -> channels.filter { it.streamId in favIds }
        else -> channels.filter { it.categoryId == selectedCategoryId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra de categorías: chip "Todos", chip "⭐ Favoritos" (si hay), y las categorías normales
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chip "Todos"
            item {
                val isSel = selectedCategoryId == "all"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) LelouchCyanAccent else LelouchSurface)
                        .clickable { selectedCategoryId = "all" }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Todos",
                        color = if (isSel) Color.Black else LelouchTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
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
            items(filtered, key = { it.streamId }) { ch ->
                val url = activeSource?.let {
                    XtreamUrlBuilder.buildLiveStreamUrl(it.serverUrl, it.username, it.password, ch.streamId, "m3u8")
                } ?: ""
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
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.streamId }) { movie ->
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
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.seriesId }) { s ->
                SeriesGridCard(series = s, onClick = { onSeriesClick(s) })
            }
        }
    }
}

// TAB 4: AJUSTES/LISTAS
@Composable
private fun MobileSettingsTab(
    activeSource: SourceConfig?, allSources: List<SourceConfig>,
    newServerUrl: String, newUsername: String, newPassword: String, newName: String,
    onNewServerUrl: (String) -> Unit, onNewUsername: (String) -> Unit,
    onNewPassword: (String) -> Unit, onNewName: (String) -> Unit,
    onActivateSource: (String) -> Unit, onDeleteSource: (String) -> Unit,
    onAddSource: (String, String, String, String) -> Unit,
    onSyncCloud: () -> Unit, onForceSync: () -> Unit, onLogout: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Gestor de Listas IPTV", color = LelouchTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("Cambia entre tus cuentas o anade un nuevo proveedor.", color = LelouchTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileActionButton("Nube", Modifier.weight(1f), onClick = onSyncCloud)
                MobileActionButton("Sync", Modifier.weight(1f), onClick = onForceSync)
                MobileActionButton("Actualizar", Modifier.weight(1.2f), onClick = onOpenUpdate)
                MobileDangerButton("Salir", Modifier.weight(1f), onClick = onLogout)
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                LelouchCyanAccent.copy(alpha = 0.15f),
                                LelouchBlueSecondary.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .border(1.dp, LelouchCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onOpenUpdate() }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LelouchCyanAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = LelouchCyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Actualizaciones de App (OTA)",
                                color = LelouchTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Actualiza por Wi-Fi sin memorias USB",
                                color = LelouchTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(LelouchCyanAccent)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "BUSCAR",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
        item {
            Text("Listas Registradas (${allSources.size}):", color = LelouchCyanAccent,
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        items(allSources) { source ->
            val isActive = source.id == activeSource?.id || source.isActive
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) LelouchSurfaceVariant else LelouchSurface)
                    .border(
                        if (isActive) 1.5.dp else 1.dp,
                        if (isActive) Color(0xFF10B981) else LelouchBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !isActive) { onActivateSource(source.id) }
                    .padding(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(source.name, color = LelouchTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (isActive) {
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("ACTIVA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text("${source.serverUrl}  -  ${source.username}", color = LelouchTextSecondary,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isActive) {
                            TextButton(onClick = { onActivateSource(source.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = LelouchCyanAccent)) {
                                Text("Activar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (allSources.size > 1) {
                            IconButton(onClick = { onDeleteSource(source.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, null, tint = LelouchLiveRed)
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("Conectar Otra Cuenta Xtream", color = LelouchTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            val fc = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LelouchCyanAccent, unfocusedBorderColor = LelouchBorder,
                focusedLabelColor = LelouchCyanAccent, unfocusedLabelColor = LelouchTextSecondary,
                cursorColor = LelouchCyanAccent, focusedTextColor = LelouchTextPrimary,
                unfocusedTextColor = LelouchTextPrimary
            )
            OutlinedTextField(value = newServerUrl, onValueChange = onNewServerUrl,
                label = { Text("URL Servidor") }, placeholder = { Text("http://servidor:puerto") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fc)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newUsername, onValueChange = onNewUsername,
                label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fc)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newPassword, onValueChange = onNewPassword,
                label = { Text("Contrasena") }, visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fc)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newName, onValueChange = onNewName,
                label = { Text("Nombre (Opcional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fc)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (newServerUrl.isNotBlank() && newUsername.isNotBlank() && newPassword.isNotBlank()) {
                        onAddSource(newServerUrl.trim(), newUsername.trim(), newPassword.trim(),
                            newName.trim().ifEmpty { newUsername.trim() })
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
            ) { Text("Guardar y Conectar", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}

// MINI PLAYER
@Composable
private fun MiniPlayerOverlay(
    channelName: String,
    playerEngine: com.lelouch.core.player.LelouchPlayerEngine,
    onToggleFullscreen: () -> Unit,
    onCopyUrl: () -> Unit,
    onClose: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(Color.Black)) {
        LelouchVideoPlayer(playerEngine = playerEngine, modifier = Modifier.fillMaxSize())
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
            Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(LelouchLiveRed)
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("EN VIVO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    val streamUrl = activeSource?.let {
        XtreamUrlBuilder.buildVodStreamUrl(it.serverUrl, it.username, it.password, movie.streamId, movie.containerExtension)
    } ?: ""
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val context = LocalContext.current
                        Button(
                            onClick = { onPlay(streamUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("REPRODUCIR", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                if (streamUrl.isNotBlank()) {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(streamUrl))
                                    android.widget.Toast.makeText(context, "📋 Enlace copiado: ${movie.name}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(LelouchSurfaceVariant).size(48.dp)
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
private fun MobileSeriesDetailDialog(series: Series, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(16.dp)).background(LelouchBackground)) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(LelouchSurfaceVariant)) {
                    if (!series.cover.isNullOrBlank()) {
                        AsyncImage(model = series.cover, contentDescription = null,
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
                    Text(series.name, color = LelouchTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val r = series.rating5based ?: 0.0
                        if (r > 0) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE5A00D)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("%.1f".format(r), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!series.genre.isNullOrBlank()) Text(series.genre!!, color = LelouchCyanAccent, fontSize = 12.sp)
                        if (series.seasonsCount > 0) Text("${series.seasonsCount} Temporadas", color = LelouchTextSecondary, fontSize = 12.sp)
                    }
                    if (!series.plot.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(series.plot!!, color = LelouchTextSecondary, fontSize = 13.sp,
                            maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(LelouchSurface).border(1.dp, LelouchBorder, RoundedCornerShape(10.dp))
                        .padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Abre la app en Android TV para ver los episodios", color = LelouchTextSecondary, fontSize = 13.sp)
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
    Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
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
    Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
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
