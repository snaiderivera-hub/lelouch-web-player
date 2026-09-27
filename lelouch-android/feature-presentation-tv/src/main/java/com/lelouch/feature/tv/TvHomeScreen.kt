package com.lelouch.feature.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.Series
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.VodMovie
import com.lelouch.core.network.XtreamUrlBuilder
import com.lelouch.core.player.LelouchVideoPlayer
import com.lelouch.core.player.PlaybackState
import com.lelouch.core.player.rememberLelouchPlayer
import com.lelouch.feature.tv.components.EpgTimelineModal
import com.lelouch.feature.tv.components.MediaDetailUiModel
import com.lelouch.feature.tv.components.TvMediaDetailModal
import com.lelouch.feature.tv.components.TvPosterCard
import com.lelouch.feature.tv.components.TvSearchModal
import kotlinx.coroutines.delay



data class ChannelUiModel(
    val streamId: Int,
    val name: String,
    val num: Int,
    val categoryName: String,
    val streamIcon: String? = null,
    val currentProgram: String = "Transmisión en Directo",
    val nextProgram: String = "Continuación de Programación",
    val streamUrl: String = ""
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    activeSource: SourceConfig? = null,
    liveChannels: List<LiveStream> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    seriesList: List<Series> = emptyList(),
    favoriteChannels: List<LiveStream> = emptyList(),
    favoriteMovies: List<VodMovie> = emptyList(),
    onToggleFavoriteChannel: (streamId: Int, isFav: Boolean) -> Unit = { _, _ -> },
    onToggleFavoriteMovie: (streamId: Int, isFav: Boolean) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {}
) {
    val playerEngine = rememberLelouchPlayer()
    val playbackState by playerEngine.playbackState.collectAsStateWithLifecycle()
    val videoInfo by playerEngine.videoTrackInfo.collectAsStateWithLifecycle()

    // Canales mapeados o canales de demostración
    val displayChannels = remember(liveChannels, activeSource) {
        if (liveChannels.isNotEmpty()) {
            liveChannels.mapIndexed { index, stream ->
                val streamUrl = activeSource?.let {
                    XtreamUrlBuilder.buildLiveStreamUrl(
                        it.serverUrl,
                        it.username,
                        it.password,
                        stream.streamId,
                        "m3u8"
                    )
                } ?: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

                ChannelUiModel(
                    streamId = stream.streamId,
                    name = stream.name,
                    num = stream.num.takeIf { it > 0 } ?: (index + 1),
                    categoryName = stream.categoryName ?: "General",
                    streamIcon = stream.streamIcon,
                    currentProgram = stream.epgChannelId ?: "En Directo",
                    streamUrl = streamUrl
                )
            }
        } else {
            listOf(
                ChannelUiModel(101, "ESPN HD", 101, "Deportes", currentProgram = "UEFA Champions League: Cuartos de Final", nextProgram = "SportsCenter en Vivo", streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                ChannelUiModel(102, "Fox Sports", 102, "Deportes", currentProgram = "Fórmula 1: Gran Premio en Directo", nextProgram = "Fox Sports Radio", streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                ChannelUiModel(103, "TyC Sports HD", 103, "Deportes", currentProgram = "Fútbol de Primera en Directo", nextProgram = "Líbero", streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                ChannelUiModel(104, "DirecTV Sports", 104, "Deportes", currentProgram = "Copa Libertadores: Partido de Ida", nextProgram = "De Fútbol Se Habla Así", streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                ChannelUiModel(105, "HBO Max HD", 105, "Cine & Series", currentProgram = "Duna: Parte Dos (Estreno 4K)", nextProgram = "House of the Dragon", streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                ChannelUiModel(106, "Star Channel", 106, "Entretenimiento", currentProgram = "Los Simpson: Maratón Especial", nextProgram = "Futurama", streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
            )
        }
    }

    var focusedChannelIndex by remember { mutableIntStateOf(0) }
    val focusedChannel = displayChannels.getOrElse(focusedChannelIndex) { displayChannels.first() }

    // Estado del Spotlight Hero cuando se enfoca una película o serie
    var focusedHeroMovie by remember { mutableStateOf<VodMovie?>(null) }
    var focusedHeroSeries by remember { mutableStateOf<Series?>(null) }

    // Modal de Detalle de Película / Serie
    var activeDetailMedia by remember { mutableStateOf<MediaDetailUiModel?>(null) }
    var isSearchModalVisible by remember { mutableStateOf(false) }
    var isEpgModalVisible by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }


    var isHudVisible by remember { mutableStateOf(false) }
    var selectedTopTab by remember { mutableIntStateOf(0) } // 0 = Inicio, 1 = En Vivo, 2 = Películas, 3 = Series, 4 = Favoritos
    val topTabs = listOf("Inicio", "En Vivo", "Películas", "Series", "Favoritos")

    // Live Background Zapping con Debounce inteligente de 300ms (activo en Inicio y En Vivo)
    LaunchedEffect(focusedChannel.streamUrl, selectedTopTab) {
        if (selectedTopTab == 0 || selectedTopTab == 1) {
            delay(300)
            if (focusedChannel.streamUrl.isNotEmpty()) {
                playerEngine.playStream(focusedChannel.streamUrl, isLive = true)
            }
        }
    }

    // Auto-ocultar Mini-Guía HUD tras 5 segundos
    LaunchedEffect(isHudVisible) {
        if (isHudVisible) {
            delay(5000)
            isHudVisible = false
        }
    }

    // Manejo de tecla BACK en Pantalla Completa: regresa al carrusel sin cortar la señal
    BackHandler(enabled = isFullscreen) {
        if (isHudVisible) {
            isHudVisible = false
        } else {
            isFullscreen = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground)
            .onKeyEvent { keyEvent ->
                if (isFullscreen && keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            isHudVisible = !isHudVisible
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (focusedChannelIndex < displayChannels.lastIndex) {
                                focusedChannelIndex++
                                isHudVisible = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (focusedChannelIndex > 0) {
                                focusedChannelIndex--
                                isHudVisible = true
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // CAPA 1: Reproductor de Video de Fondo Nativo Media3 (Live Background Zapping)
        LelouchVideoPlayer(
            playerEngine = playerEngine,
            modifier = Modifier.fillMaxSize()
        )

        // CAPA 2: Vignette Scrim Cinemático (se desvanece al pasar a Fullscreen)
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to LelouchBackground.copy(alpha = 0.85f),
                                0.20f to LelouchBackground.copy(alpha = 0.45f),
                                0.50f to Color.Transparent,
                                0.70f to LelouchBackground.copy(alpha = 0.82f),
                                1.0f to LelouchBackground
                            )
                        )
                    )
            )
        }

        // CAPA 3: Interfaz Principal con Spotlight Hero y Carruseles D-Pad
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {
                // Barra de Navegación Superior Fina
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LELOUCH",
                            color = LelouchCyanAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.width(36.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            topTabs.forEachIndexed { index, title ->
                                var isTabFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .onFocusChanged { isTabFocused = it.isFocused }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isTabFocused -> LelouchCyanAccent.copy(alpha = 0.25f)
                                                selectedTopTab == index -> LelouchSurfaceVariant
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isTabFocused) 2.dp else 0.dp,
                                            color = if (isTabFocused) LelouchCyanAccent else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedTopTab = index }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isTabFocused || selectedTopTab == index) LelouchTextPrimary else LelouchTextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedTopTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        var isSearchFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isSearchFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSearchFocused) LelouchCyanAccent.copy(alpha = 0.25f) else Color.Transparent)
                                .border(1.dp, if (isSearchFocused) LelouchCyanAccent else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { isSearchModalVisible = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔍 Buscar",
                                color = if (isSearchFocused) LelouchTextPrimary else LelouchTextSecondary,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Indicador de Estado de Conexión & Resolución

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (playbackState) {
                                            is PlaybackState.Playing -> LelouchCyanAccent
                                            is PlaybackState.Buffering -> Color(0xFFFFB300)
                                            is PlaybackState.Error -> LelouchLiveRed
                                            else -> LelouchTextMuted
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = videoInfo.resolutionLabel,
                                color = LelouchCyanAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Spotlight Hero Dinámico
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 20.dp, bottom = 24.dp, end = 120.dp)
                    ) {
                        val heroTitle = when (selectedTopTab) {
                            2 -> focusedHeroMovie?.name ?: "PELÍCULAS EN TENDENCIA"
                            3 -> focusedHeroSeries?.name ?: "SERIES DESTACADAS"
                            else -> focusedChannel.name
                        }
                        val heroSubtitle = when (selectedTopTab) {
                            2 -> "Estrenos y Clásicos en Máxima Calidad"
                            3 -> "Temporadas Completas en Streaming"
                            else -> focusedChannel.currentProgram
                        }
                        val heroBadge = when (selectedTopTab) {
                            2 -> "4K UHD"
                            3 -> "SERIES"
                            else -> "EN VIVO"
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (heroBadge == "EN VIVO") LelouchLiveRed else LelouchCyanAccent)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (heroBadge == "EN VIVO") {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                    }
                                    Text(
                                        text = heroBadge,
                                        color = if (heroBadge == "EN VIVO") Color.White else Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (selectedTopTab == 1 || selectedTopTab == 0) "CH ${focusedChannel.num}  •  ${focusedChannel.categoryName}  •  ${videoInfo.resolutionLabel}" else "Catálogo Digital Lelouch",
                                color = LelouchCyanAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = heroTitle,
                            color = LelouchTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = heroSubtitle,
                            color = LelouchTextSecondary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Botones de Acción Hero Spotlight
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPlayFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                                    .border(
                                        width = if (isPlayFocused) 2.dp else 1.dp,
                                        color = if (isPlayFocused) Color.White else LelouchBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (selectedTopTab == 2 && focusedHeroMovie != null) {
                                            activeSource?.let { src ->
                                                val url = XtreamUrlBuilder.buildVodStreamUrl(
                                                    src.serverUrl,
                                                    src.username,
                                                    src.password,
                                                    focusedHeroMovie!!.streamId,
                                                    focusedHeroMovie!!.containerExtension ?: "mp4"
                                                )
                                                playerEngine.playStream(url, isLive = false)
                                            }
                                        }
                                        isFullscreen = true
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isPlayFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ver Pantalla Completa (OK)",
                                        color = if (isPlayFocused) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            var isReloadFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isReloadFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isReloadFocused) LelouchSurfaceVariant.copy(alpha = 0.8f) else Color.Transparent)
                                    .border(
                                        width = if (isReloadFocused) 2.dp else 1.dp,
                                        color = if (isReloadFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        playerEngine.playStream(focusedChannel.streamUrl, isLive = true)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = LelouchTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reconectar",
                                        color = LelouchTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            var isEpgFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isEpgFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isEpgFocused) LelouchCyanAccent.copy(alpha = 0.25f) else Color.Transparent)
                                    .border(
                                        width = if (isEpgFocused) 2.dp else 1.dp,
                                        color = if (isEpgFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { isEpgModalVisible = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = if (isEpgFocused) LelouchCyanAccent else LelouchTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Guía EPG",
                                        color = if (isEpgFocused) LelouchTextPrimary else LelouchTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                }

                // SECCIÓN 1: Canales en Vivo (Visible en Inicio, En Vivo y Favoritos)
                if (selectedTopTab == 0 || selectedTopTab == 1 || selectedTopTab == 4) {
                    item {
                        Text(
                            text = if (selectedTopTab == 4) "⭐ Canales Favoritos" else "🔴 Zapping de Canales en Vivo",
                            color = LelouchTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )

                        val channelsToRender = if (selectedTopTab == 4 && favoriteChannels.isNotEmpty()) {
                            favoriteChannels.map { stream ->
                                ChannelUiModel(
                                    streamId = stream.streamId,
                                    name = stream.name,
                                    num = stream.num,
                                    categoryName = stream.categoryName ?: "Favoritos",
                                    currentProgram = "Canal Favorito",
                                    streamUrl = activeSource?.let {
                                        XtreamUrlBuilder.buildLiveStreamUrl(it.serverUrl, it.username, it.password, stream.streamId, "m3u8")
                                    } ?: ""
                                )
                            }
                        } else displayChannels

                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(channelsToRender) { index, channel ->
                                val isSelected = (index == focusedChannelIndex)
                                var isCardFocused by remember { mutableStateOf(false) }
                                val scale by animateFloatAsState(
                                    targetValue = if (isCardFocused) 1.08f else 1.0f,
                                    label = "channelCardScale"
                                )

                                Box(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(115.dp)
                                        .scale(scale)
                                        .onFocusChanged {
                                            isCardFocused = it.isFocused
                                            if (it.isFocused) {
                                                focusedChannelIndex = index
                                            }
                                        }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isCardFocused -> LelouchCardFocused
                                                isSelected -> LelouchSurfaceVariant
                                                else -> LelouchSurface
                                            }
                                        )
                                        .border(
                                            width = if (isCardFocused) 2.5.dp else 1.dp,
                                            color = if (isCardFocused) LelouchCyanAccent else if (isSelected) LelouchCyanAccent.copy(alpha = 0.5f) else LelouchBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { isFullscreen = true }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "CH ${channel.num}",
                                            color = LelouchCyanAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(LelouchLiveRed)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                        Text(
                                            text = channel.name,
                                            color = LelouchTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = channel.currentProgram,
                                            color = LelouchTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // SECCIÓN 2: Películas Recientes y Populares (Inicio y Películas)
                if (selectedTopTab == 0 || selectedTopTab == 2 || selectedTopTab == 4) {
                    item {
                        Text(
                            text = if (selectedTopTab == 4) "⭐ Películas Favoritas" else "🎬 Películas Recientemente Añadidas",
                            color = LelouchTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )

                        val moviesToRender = if (selectedTopTab == 4 && favoriteMovies.isNotEmpty()) {
                            favoriteMovies
                        } else if (movies.isNotEmpty()) {
                            movies
                        } else {
                            listOf(
                                VodMovie(id = "1", streamId = 1, name = "Duna: Parte Dos", streamIcon = null, rating = 8.6, year = "2024", categoryId = "1"),
                                VodMovie(id = "2", streamId = 2, name = "Oppenheimer", streamIcon = null, rating = 8.9, year = "2023", categoryId = "1"),
                                VodMovie(id = "3", streamId = 3, name = "Spider-Man: Across the Spider-Verse", streamIcon = null, rating = 8.7, year = "2023", categoryId = "1"),
                                VodMovie(id = "4", streamId = 4, name = "Interstellar", streamIcon = null, rating = 8.7, year = "2014", categoryId = "1"),
                                VodMovie(id = "5", streamId = 5, name = "The Batman", streamIcon = null, rating = 7.8, year = "2022", categoryId = "1")
                            )
                        }

                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(moviesToRender) { _, movie ->
                                TvPosterCard(
                                    title = movie.name,
                                    posterUrl = movie.streamIcon,
                                    rating = movie.rating ?: 0.0,
                                    year = movie.year,
                                    onFocused = {
                                        focusedHeroMovie = movie
                                    },
                                    onClick = {
                                        activeDetailMedia = MediaDetailUiModel(
                                            id = movie.streamId,
                                            title = movie.name,
                                            posterUrl = movie.streamIcon,
                                            rating = movie.rating ?: 0.0,
                                            year = movie.year,
                                            isSeries = false,
                                            isFavorite = movie.isFavorite
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // SECCIÓN 3: Series Destacadas (Inicio y Series)
                if (selectedTopTab == 0 || selectedTopTab == 3) {
                    item {
                        Text(
                            text = "📺 Series Populares",
                            color = LelouchTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )

                        val seriesToRender = if (seriesList.isNotEmpty()) {
                            seriesList
                        } else {
                            listOf(
                                Series(id = "1", seriesId = 1, name = "Breaking Bad", cover = null, rating = 9.5, releaseDate = "2008", categoryId = "1"),
                                Series(id = "2", seriesId = 2, name = "The Last of Us", cover = null, rating = 8.8, releaseDate = "2023", categoryId = "1"),
                                Series(id = "3", seriesId = 3, name = "Game of Thrones", cover = null, rating = 9.2, releaseDate = "2011", categoryId = "1"),
                                Series(id = "4", seriesId = 4, name = "Stranger Things", cover = null, rating = 8.7, releaseDate = "2016", categoryId = "1"),
                                Series(id = "5", seriesId = 5, name = "Shogun", cover = null, rating = 8.9, releaseDate = "2024", categoryId = "1")
                            )
                        }

                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(seriesToRender) { _, series ->
                                TvPosterCard(
                                    title = series.name,
                                    posterUrl = series.cover,
                                    rating = series.rating ?: 0.0,
                                    year = series.releaseDate?.take(4),
                                    onFocused = {
                                        focusedHeroSeries = series
                                    },
                                    onClick = {
                                        activeDetailMedia = MediaDetailUiModel(
                                            id = series.seriesId,
                                            title = series.name,
                                            posterUrl = series.cover,
                                            rating = series.rating ?: 0.0,
                                            year = series.releaseDate?.take(4),
                                            synopsis = series.plot ?: "Serie completa en catálogo.",
                                            genre = series.genre,
                                            isSeries = true,
                                            isFavorite = series.isFavorite,
                                            seasons = listOf(1, 2)
                                        )
                                    }
                                )
                            }
                        }

                    }
                }
            }
        }

        // CAPA 4: Modal de Detalle de Película / Serie
        activeDetailMedia?.let { media ->
            TvMediaDetailModal(
                media = media,
                onPlayClick = { epId ->
                    activeSource?.let { src ->
                        val streamUrl = if (media.isSeries && epId != null) {
                            XtreamUrlBuilder.buildSeriesStreamUrl(src.serverUrl, src.username, src.password, epId, "mp4")
                        } else {
                            XtreamUrlBuilder.buildVodStreamUrl(src.serverUrl, src.username, src.password, media.id, "mp4")
                        }
                        playerEngine.playStream(streamUrl, isLive = false)
                        isFullscreen = true
                    }
                    activeDetailMedia = null
                },
                onToggleFavorite = {
                    if (media.isSeries) {
                        // toggle favorite series
                    } else {
                        onToggleFavoriteMovie(media.id, !media.isFavorite)
                    }
                    activeDetailMedia = media.copy(isFavorite = !media.isFavorite)
                },
                onDismiss = { activeDetailMedia = null }
            )
        }

        // CAPA 5: Mini-Guía Carrusel HUD en Pantalla Completa
        AnimatedVisibility(
            visible = isFullscreen && isHudVisible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                LelouchBackground.copy(alpha = 0.90f),
                                LelouchBackground
                            )
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LelouchLiveRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("EN DIRECTO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CH ${focusedChannel.num}  •  ${focusedChannel.categoryName}",
                                    color = LelouchCyanAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = focusedChannel.name,
                                color = LelouchTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = focusedChannel.currentProgram,
                                color = LelouchTextSecondary,
                                fontSize = 14.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = videoInfo.resolutionLabel,
                                color = LelouchCyanAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Siguiente: ${focusedChannel.nextProgram}",
                                color = LelouchTextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(displayChannels) { index, channel ->
                            val isSelected = (index == focusedChannelIndex)
                            var isCardFocused by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(70.dp)
                                    .onFocusChanged {
                                        isCardFocused = it.isFocused
                                        if (it.isFocused) {
                                            focusedChannelIndex = index
                                        }
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCardFocused) LelouchCardFocused else if (isSelected) LelouchSurfaceVariant else LelouchSurface)
                                    .border(
                                        width = if (isCardFocused) 2.dp else 1.dp,
                                        color = if (isCardFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        focusedChannelIndex = index
                                    }
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                    Text(
                                        text = "CH ${channel.num} - ${channel.name}",
                                        color = if (isCardFocused || isSelected) LelouchCyanAccent else LelouchTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = channel.currentProgram,
                                        color = LelouchTextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Presiona BACK para salir al menú  •  D-Pad ARRIBA / ABAJO para ocultar guía",
                        color = LelouchTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // CAPA 6: Modal de Búsqueda FTS5 Instantánea
        if (isSearchModalVisible) {
            TvSearchModal(
                channels = liveChannels,
                movies = movies,
                seriesList = seriesList,
                onSelectChannel = { channel ->
                    val idx = displayChannels.indexOfFirst { it.streamId == channel.streamId }
                    if (idx >= 0) focusedChannelIndex = idx
                    selectedTopTab = 1
                    isSearchModalVisible = false
                },
                onSelectMovie = { movie ->
                    activeDetailMedia = MediaDetailUiModel(
                        id = movie.streamId,
                        title = movie.name,
                        posterUrl = movie.streamIcon,
                        rating = movie.rating ?: 0.0,
                        year = movie.year,
                        isSeries = false,
                        isFavorite = movie.isFavorite
                    )
                    isSearchModalVisible = false
                },
                onSelectSeries = { series ->
                    activeDetailMedia = MediaDetailUiModel(
                        id = series.seriesId,
                        title = series.name,
                        posterUrl = series.cover,
                        rating = series.rating ?: 0.0,
                        year = series.releaseDate?.take(4),
                        synopsis = series.plot ?: "Serie completa en catálogo.",
                        genre = series.genre,
                        isSeries = true,
                        isFavorite = series.isFavorite,
                        seasons = listOf(1, 2)
                    )
                    isSearchModalVisible = false
                },
                onDismiss = { isSearchModalVisible = false }
            )
        }

        // CAPA 7: Modal de Guía Electrónica (EPG) Timeline
        if (isEpgModalVisible) {
            EpgTimelineModal(
                channels = liveChannels,
                onSelectChannel = { channel ->
                    val idx = displayChannels.indexOfFirst { it.streamId == channel.streamId }
                    if (idx >= 0) focusedChannelIndex = idx
                    selectedTopTab = 1
                    isEpgModalVisible = false
                },
                onDismiss = { isEpgModalVisible = false }
            )
        }
    }
}


