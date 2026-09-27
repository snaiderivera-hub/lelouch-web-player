package com.lelouch.feature.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.network.XtreamUrlBuilder
import com.lelouch.core.player.LelouchVideoPlayer
import com.lelouch.core.player.PlaybackState
import com.lelouch.core.player.rememberLelouchPlayer
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
    onNavigateToSettings: () -> Unit = {}
) {
    val playerEngine = rememberLelouchPlayer()
    val playbackState by playerEngine.playbackState.collectAsStateWithLifecycle()
    val videoInfo by playerEngine.videoTrackInfo.collectAsStateWithLifecycle()

    // Canales por defecto si la base de datos aún no tiene registros
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
                ChannelUiModel(
                    streamId = 101,
                    name = "ESPN HD",
                    num = 101,
                    categoryName = "Deportes",
                    currentProgram = "UEFA Champions League: Cuartos de Final",
                    nextProgram = "SportsCenter en Vivo",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 102,
                    name = "Fox Sports",
                    num = 102,
                    categoryName = "Deportes",
                    currentProgram = "Fórmula 1: Gran Premio en Directo",
                    nextProgram = "Fox Sports Radio",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 103,
                    name = "TyC Sports HD",
                    num = 103,
                    categoryName = "Deportes",
                    currentProgram = "Fútbol de Primera en Directo",
                    nextProgram = "Líbero",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 104,
                    name = "DirecTV Sports",
                    num = 104,
                    categoryName = "Deportes",
                    currentProgram = "Copa Libertadores: Partido de Ida",
                    nextProgram = "De Fútbol Se Habla Así",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 105,
                    name = "HBO Max HD",
                    num = 105,
                    categoryName = "Cine & Series",
                    currentProgram = "Duna: Parte Dos (Estreno 4K)",
                    nextProgram = "House of the Dragon",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 106,
                    name = "Star Channel",
                    num = 106,
                    categoryName = "Entretenimiento",
                    currentProgram = "Los Simpson: Maratón Especial",
                    nextProgram = "Futurama",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                )
            )
        }
    }

    var focusedChannelIndex by remember { mutableIntStateOf(0) }
    val focusedChannel = displayChannels.getOrElse(focusedChannelIndex) { displayChannels.first() }

    var isFullscreen by remember { mutableStateOf(false) }
    var isHudVisible by remember { mutableStateOf(false) }
    var selectedTopTab by remember { mutableIntStateOf(1) } // 1 = En Vivo
    val topTabs = listOf("Inicio", "En Vivo", "Películas", "Series", "Favoritos")

    // Live Background Zapping con Debounce inteligente de 300ms
    LaunchedEffect(focusedChannel.streamUrl) {
        delay(300)
        if (focusedChannel.streamUrl.isNotEmpty()) {
            playerEngine.playStream(focusedChannel.streamUrl, isLive = true)
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
                                0.70f to LelouchBackground.copy(alpha = 0.80f),
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
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
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

                // Spotlight Hero: Metadatos del Canal Enfocado
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 20.dp, bottom = 24.dp, end = 120.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LelouchLiveRed)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "EN VIVO",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "CH ${focusedChannel.num}  •  ${focusedChannel.categoryName}  •  ${videoInfo.resolutionLabel}",
                                color = LelouchCyanAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = focusedChannel.name,
                            color = LelouchTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = focusedChannel.currentProgram,
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
                                    .clickable { isFullscreen = true }
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
                        }
                    }
                }

                // Carrusel 1: Zapping Rápido de Canales en Vivo (Live Video Zapping)
                item {
                    Text(
                        text = "🔴 Zapping de Canales en Vivo",
                        color = LelouchTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                    )

                    TvLazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(displayChannels) { index, channel ->
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

                // Carrusel 2: Categorías de Canales
                item {
                    Text(
                        text = "⭐ Categorías Recomendadas",
                        color = LelouchTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                    )

                    TvLazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val categories = listOf("⚽ Deportes", "🎬 Cine & Series", "📰 Noticias", "⭐ Favoritos", "👶 Infantil", "🎵 Música")
                        itemsIndexed(categories) { _, catName ->
                            var isCatFocused by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(targetValue = if (isCatFocused) 1.06f else 1.0f, label = "catScale")
                            Box(
                                modifier = Modifier
                                    .width(170.dp)
                                    .height(75.dp)
                                    .scale(scale)
                                    .onFocusChanged { isCatFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCatFocused) LelouchCardFocused else LelouchSurface)
                                    .border(
                                        width = if (isCatFocused) 2.dp else 1.dp,
                                        color = if (isCatFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = catName,
                                    color = if (isCatFocused) LelouchCyanAccent else LelouchTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // CAPA 4: Mini-Guía Carrusel HUD en Pantalla Completa
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
                    // Fila de metadatos del canal activo en Fullscreen
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

                        // Siguiente programa y resolución
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

                    // Mini-carrusel para zapping rápido en pantalla completa
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
    }
}
