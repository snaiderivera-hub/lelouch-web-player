package com.lelouch.feature.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import com.lelouch.feature.tv.focus.MoviesFocusGraph
import com.lelouch.feature.tv.focus.NavResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import com.lelouch.feature.tv.focus.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import coil.compose.AsyncImage
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
import com.lelouch.core.model.Episode
import com.lelouch.feature.tv.components.TvAdminPanelModal
import com.lelouch.feature.tv.components.TvMediaDetailModal
import com.lelouch.feature.tv.components.TvPosterCard
import com.lelouch.feature.tv.components.TvSearchModal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(
    activeSource: SourceConfig? = null,
    allSources: List<SourceConfig> = emptyList(),
    liveChannels: List<LiveStream> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    seriesList: List<Series> = emptyList(),
    favoriteChannels: List<LiveStream> = emptyList(),
    favoriteMovies: List<VodMovie> = emptyList(),
    onToggleFavoriteChannel: (streamId: Int, isFav: Boolean) -> Unit = { _, _ -> },
    onToggleFavoriteMovie: (streamId: Int, isFav: Boolean) -> Unit = { _, _ -> },
    onActivateSource: (sourceId: String) -> Unit = {},
    onDeleteSource: (sourceId: String) -> Unit = {},
    onAddSource: (serverUrl: String, user: String, pass: String, name: String) -> Unit = { _, _, _, _ -> },
    onSyncCloudSources: () -> Unit = {},
    onForceSync: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onFetchSeriesDetails: (suspend (seriesId: Int) -> Pair<List<Int>, List<Episode>>)? = null
) {
    val playerEngine = rememberLelouchPlayer()
    val playbackState by playerEngine.playbackState.collectAsStateWithLifecycle()
    val videoInfo by playerEngine.videoTrackInfo.collectAsStateWithLifecycle()

    // Canales mapeados o canales de demostración con logos reales de alta resolución
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
                    streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/512px-ESPN_wordmark.svg.png",
                    currentProgram = "UEFA Champions League: En Directo",
                    nextProgram = "SportsCenter en Vivo",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 102,
                    name = "Fox Sports",
                    num = 102,
                    categoryName = "Deportes",
                    streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Fox_Sports_logo.svg/512px-Fox_Sports_logo.svg.png",
                    currentProgram = "Fórmula 1: Gran Premio en Directo",
                    nextProgram = "Fox Sports Radio",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 103,
                    name = "TyC Sports HD",
                    num = 103,
                    categoryName = "Deportes",
                    streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/TyC_Sports_Logo_2019.svg/512px-TyC_Sports_Logo_2019.svg.png",
                    currentProgram = "Fútbol de Primera en Directo",
                    nextProgram = "Líbero",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 104,
                    name = "DirecTV Sports",
                    num = 104,
                    categoryName = "Deportes",
                    streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/DSports_logo_2022.svg/512px-DSports_logo_2022.svg.png",
                    currentProgram = "Copa Libertadores: Partido de Ida",
                    nextProgram = "De Fútbol Se Habla Así",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 105,
                    name = "HBO Max HD",
                    num = 105,
                    categoryName = "Cine & Series",
                    streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/512px-HBO_logo.svg.png",
                    currentProgram = "Duna: Parte Dos (Estreno 4K)",
                    nextProgram = "House of the Dragon",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                ChannelUiModel(
                    streamId = 106,
                    name = "Star Channel",
                    num = 106,
                    categoryName = "Entretenimiento",
                    streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Star_Channel_2021.svg/512px-Star_Channel_2021.svg.png",
                    currentProgram = "Los Simpson: Maratón Especial",
                    nextProgram = "Futurama",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                )
            )
        }
    }

    // Catálogo de películas (real o demo enriquecido con carátulas y fondos HD)
    val displayMovies = remember(movies) {
        if (movies.isNotEmpty()) movies
        else listOf(
            VodMovie(
                id = "1",
                streamId = 1,
                name = "Duna: Parte Dos",
                streamIcon = "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nx2zx.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
                rating = 8.6,
                year = "2024",
                categoryId = "1",
                plot = "Paul Atreides se une a Chani y a los Fremen mientras busca venganza contra los conspiradores que destruyeron a su familia."
            ),
            VodMovie(
                id = "2",
                streamId = 2,
                name = "Oppenheimer",
                streamIcon = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
                rating = 8.9,
                year = "2023",
                categoryId = "1",
                plot = "La historia del físico J. Robert Oppenheimer y su liderazgo en el Proyecto Manhattan durante la Segunda Guerra Mundial."
            ),
            VodMovie(
                id = "3",
                streamId = 3,
                name = "Spider-Man: A Través del Spider-Verso",
                streamIcon = "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
                rating = 8.7,
                year = "2023",
                categoryId = "1",
                plot = "Miles Morales es catapultado a través del Multiverso, donde se encuentra con un equipo de Spider-People."
            ),
            VodMovie(
                id = "4",
                streamId = 4,
                name = "Interstellar",
                streamIcon = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/rAiYTsqBkRefBXweC1Nq4qg49Hq.jpg",
                rating = 8.7,
                year = "2014",
                categoryId = "1",
                plot = "Un grupo de exploradores espaciales viaja a través de un agujero de gusano en busca de un nuevo hogar para la humanidad."
            ),
            VodMovie(
                id = "5",
                streamId = 5,
                name = "The Batman",
                streamIcon = "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/b0PlSFdDwbyK0cf5RxwDpaOJQvQ.jpg",
                rating = 7.8,
                year = "2022",
                categoryId = "1",
                plot = "En su segundo año luchando contra el crimen, Batman explora la corrupción en Ciudad Gótica y el vínculo con su familia."
            )
        )
    }

    val topRatedMovies = remember(displayMovies) {
        displayMovies.sortedByDescending { it.rating ?: 0.0 }
    }

    // Catálogo de series (incluye Demon Slayer de EveryCine como primera opción)
    val displaySeries = remember(seriesList) {
        if (seriesList.isNotEmpty()) seriesList
        else listOf(
            Series(
                id = "1",
                seriesId = 1,
                name = "Demon Slayer: Kimetsu no Yaiba",
                cover = "https://image.tmdb.org/t/p/w500/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/nTvM4mhqZlHIvUkIvd4MVooSl6m.jpg",
                rating = 8.9,
                releaseDate = "2024",
                categoryId = "1",
                seasonsCount = 4,
                plot = "Tanjiro Kamado lucha como cazador de demonios para devolverle la humanidad a su hermana Nezuko en una aventura épica."
            ),
            Series(
                id = "2",
                seriesId = 2,
                name = "The Last of Us",
                cover = "https://image.tmdb.org/t/p/w500/uKvVjK19ySNtTUrNX7j59tTtkif.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/uDgy6hyPd82kOHh6I95FLtLnj6p.jpg",
                rating = 8.8,
                releaseDate = "2023",
                categoryId = "1",
                seasonsCount = 1,
                plot = "Veinte años después de que una plaga destruyera la civilización, un superviviente cínico debe escoltar a una joven inmune."
            ),
            Series(
                id = "3",
                seriesId = 3,
                name = "Stranger Things",
                cover = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
                rating = 8.7,
                releaseDate = "2022",
                categoryId = "1",
                seasonsCount = 4,
                plot = "La desaparición de un niño desencadena una serie de eventos misteriosos que involucran experimentos secretos y fuerzas siniestras."
            ),
            Series(
                id = "4",
                seriesId = 4,
                name = "Breaking Bad",
                cover = "https://image.tmdb.org/t/p/w500/ztkUQFLlC19CCMYHW9o1zWhJRNq.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/tsRy63Mu5cu8etL1X7ZLyf7UP1M.jpg",
                rating = 9.5,
                releaseDate = "2008",
                categoryId = "1",
                seasonsCount = 5,
                plot = "Un profesor de química recurre a la fabricación de metanfetamina para asegurar el futuro económico de su familia."
            ),
            Series(
                id = "5",
                seriesId = 5,
                name = "Shōgun",
                cover = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WNzG1AgYT.jpg",
                backdropPath = "https://image.tmdb.org/t/p/original/y4F4yR3L01v9NfP4e5k0l2fGjW6.jpg",
                rating = 8.9,
                releaseDate = "2024",
                categoryId = "1",
                seasonsCount = 1,
                plot = "En el Japón feudal de 1600, el señor Toranaga lucha por su supervivencia frente a sus rivales en el Consejo de Regentes."
            )
        )
    }

    var focusedChannelIndex by remember { mutableIntStateOf(0) }
    val focusedChannel = displayChannels.getOrElse(focusedChannelIndex) { displayChannels.first() }

    // Elementos destacados en el Spotlight Hero
    var focusedHeroMovie by remember(displayMovies) { mutableStateOf<VodMovie?>(displayMovies.firstOrNull()) }
    var focusedHeroSeries by remember(displaySeries) { mutableStateOf<Series?>(displaySeries.firstOrNull()) }

    // Modal de Detalle de Película / Serie
    var activeDetailMedia by remember { mutableStateOf<MediaDetailUiModel?>(null) }
    var isSearchModalVisible by remember { mutableStateOf(false) }
    var isEpgModalVisible by remember { mutableStateOf(false) }
    var isAdminModalVisible by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }
    var isHudVisible by remember { mutableStateOf(false) }
    var isQuickZappingOpen by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    val focusTracker = remember { FocusTracker() }
    var focusedTopTab by remember { mutableIntStateOf(2) } // Desacoplado de selectedTopTab (Paso 10)
    var selectedTopTab by remember { mutableIntStateOf(2) } // Iniciamos en Películas para validar MOVIES (Paso 12)
    val topTabs = listOf("Inicio", "En Vivo", "Películas", "Series", "Favoritos", "⚙️ Admin", "🧪 Lab")
    val coroutineScope = rememberCoroutineScope()
    // Estado de scroll de los rieles de Movies — nivel superior para coordinación en Single Owner
    val recentRowState = rememberTvLazyListState()
    val topRatedRowState = rememberTvLazyListState()
    // Job de scroll pendiente — cancelable si llega una nueva pulsación antes de que el item se compose
    var pendingScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(selectedTopTab) {
        focusTracker.currentScreen = when (selectedTopTab) {
            0 -> "HOME"
            1 -> "LIVE"
            2 -> "MOVIES"
            3 -> "SERIES"
            4 -> "FAVORITES"
            5 -> "ADMIN"
            else -> "LAB"
        }
        // FIX F2/F7: Después de que CENTER cambia selectedTopTab, el contenido de la
        // pantalla se recompone. Sin un requestFocus() explícito, Compose TV asigna
        // foco al primer focusable geométrico (que puede ser nav_live u otro).
        // Garantizamos que el foco permanezca en el tab que el usuario acaba de activar
        // (focusedTopTab), que es el tab que tenía el foco físico cuando se presionó CENTER.
        // Solo aplicamos en tabs 0-5 (no en LAB que tiene su propio handler).
        if (selectedTopTab < 6) {
            val tabToFocus = focusedTopTab.coerceIn(0, 5)
            try { focusTracker.getNavRequester(tabToFocus).requestFocus() } catch (_: Exception) {}
        }
    }

    if (selectedTopTab == 6) {
        DpadFocusLabScreen(
            onBack = { selectedTopTab = 2 }
        )
        return
    }

    val playerFocusRequester = remember { FocusRequester() }

    // ── PUNTO DE INICIALIZACIÓN ÚNICO (FIX F1) ────────────────────────────────
    // Un único LaunchedEffect controla el foco inicial en MOVIES.
    // Target inicial: navMoviesAnchor (nav_movies, TOP_NAV).
    // Se ejecuta una sola vez al montar el composable.
    // NO hay segundo LaunchedEffect compitiendo por getRecentRequester(0).
    // Si el usuario quiere navegar al carrusel, usa DPAD_DOWN desde TOP_NAV.
    LaunchedEffect(Unit) {
        if (selectedTopTab == 2) {
            try { focusTracker.navMoviesAnchor.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            playerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(1500)
            seekFeedbackText = null
        }
    }

    fun openSeriesDetails(series: Series) {
        activeDetailMedia = MediaDetailUiModel(
            id = series.seriesId,
            title = series.name,
            posterUrl = series.cover,
            backdropUrl = series.backdropPath,
            rating = series.rating ?: 0.0,
            year = series.releaseDate?.take(4),
            synopsis = series.plot ?: "Serie completa en catálogo de streaming.",
            genre = series.genre,
            isSeries = true,
            isFavorite = series.isFavorite,
            seasons = (1..(series.seasonsCount.takeIf { it > 0 } ?: 3)).toList(),
            episodes = emptyList()
        )

        if (onFetchSeriesDetails != null) {
            coroutineScope.launch {
                try {
                    val (seasons, episodes) = onFetchSeriesDetails(series.seriesId)
                    if (activeDetailMedia?.id == series.seriesId) {
                        activeDetailMedia = activeDetailMedia?.copy(
                            seasons = if (seasons.isNotEmpty()) seasons else listOf(1),
                            episodes = episodes
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TvHomeScreen", "Error cargando episodios de serie: ${e.message}")
                }
            }
        }
    }

    // Backdrop cinemático dinámico para la portada (EveryCine Style)
    val currentBackdropUrl = remember(selectedTopTab, focusedHeroMovie, focusedHeroSeries, focusedChannel) {
        when (selectedTopTab) {
            2 -> focusedHeroMovie?.backdropPath ?: focusedHeroMovie?.streamIcon
            3 -> focusedHeroSeries?.backdropPath ?: focusedHeroSeries?.cover
            else -> {
                if (focusedHeroMovie != null && selectedTopTab == 0) {
                    focusedHeroMovie?.backdropPath ?: focusedHeroMovie?.streamIcon
                } else if (focusedHeroSeries != null && selectedTopTab == 0) {
                    focusedHeroSeries?.backdropPath ?: focusedHeroSeries?.cover
                } else {
                    focusedChannel.streamIcon
                }
            }
        }
    }

    // Live Background Zapping instantáneo (activo solo en Inicio y En Vivo)
    LaunchedEffect(focusedChannel.streamUrl, selectedTopTab) {
        if (selectedTopTab == 0 || selectedTopTab == 1) {
            if (focusedChannel.streamUrl.isNotEmpty()) {
                playerEngine.playStream(focusedChannel.streamUrl, isLive = true)
            }
        } else {
            // Pausar video en vivo al explorar Películas o Series
            playerEngine.pause()
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
    BackHandler(enabled = isFullscreen || isQuickZappingOpen) {
        if (isQuickZappingOpen) {
            isQuickZappingOpen = false
        } else if (isHudVisible) {
            isHudVisible = false
        } else {
            isFullscreen = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground)
            .onPreviewKeyEvent { event ->
                // ══════════════════════════════════════════════════════════════
                // SINGLE OWNER D-PAD — MOVIES (FIX F3)
                //
                // Este handler es el ÚNICO propietario del movimiento direccional
                // en la pantalla MOVIES. Para ACTION_DOWN + dirección:
                //   A. Capturar BEFORE
                //   B. Calcular TARGET via MoviesFocusGraph
                //   C. recordKeyRequest() (metadata HUD)
                //   D. requestFocus() en el FocusRequester real
                //   E. Retornar TRUE → Compose NO realiza búsqueda geométrica
                //
                // CENTER, BACK, CHANNEL_UP/DOWN: NO manejados aquí.
                // ACTION_UP de cualquier tecla: ignorado (sin movimiento doble).
                // ══════════════════════════════════════════════════════════════
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (selectedTopTab != 2) return@onPreviewKeyEvent false

                val keyName = when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP    -> "DPAD_UP"
                    KeyEvent.KEYCODE_DPAD_DOWN  -> "DPAD_DOWN"
                    KeyEvent.KEYCODE_DPAD_LEFT  -> "DPAD_LEFT"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
                    else -> return@onPreviewKeyEvent false  // CENTER, BACK, etc.: no manejado aquí
                }

                // Cancelar cualquier scroll pendiente de la pulsación anterior
                pendingScrollJob?.cancel()
                pendingScrollJob = null

                // Construir el grafo con tamaños actuales de los rieles
                // topRatedMovies está disponible desde el nivel del Composable (remember(displayMovies))
                val graph = MoviesFocusGraph(
                    tracker       = focusTracker,
                    navTabCount   = topTabs.size,
                    recentCount   = displayMovies.size,
                    topRatedCount = topRatedMovies.size
                )

                when (val navResult = graph.resolve(keyName)) {
                    is NavResult.Ready -> {
                        val target = navResult.target
                        focusTracker.recordKeyRequest(
                            key         = keyName,
                            targetTag   = target.tag,
                            targetZone  = target.zone,
                            targetIndex = target.index,
                            consumed    = true
                        )
                        try { target.requester?.requestFocus() } catch (_: Exception) {}
                        true  // Consumido: Compose NO aplica búsqueda geométrica
                    }
                    is NavResult.ScrollNeeded -> {
                        val target = navResult.target
                        val scrollIdx = navResult.scrollToIndex
                        focusTracker.recordKeyRequest(
                            key         = keyName,
                            targetTag   = target.tag,
                            targetZone  = target.zone,
                            targetIndex = target.index,
                            consumed    = true
                        )
                        // Coordinar scroll + esperar composición + requestFocus
                        // El Job es cancelable si llega una nueva pulsación antes de completarse
                        pendingScrollJob = coroutineScope.launch {
                            val listState = when (target.zone) {
                                TvFocusZone.RAIL_RECENT    -> recentRowState
                                TvFocusZone.RAIL_TOP_RATED -> topRatedRowState
                                else -> return@launch
                            }
                            // 1. Solicitar scroll hacia el índice destino
                            listState.animateScrollToItem(scrollIdx)
                            // 2. Esperar hasta que el item esté compuesto (máx ~10 frames @ 60fps)
                            var waitCount = 0
                            while (!focusTracker.isComposed(target.zone, scrollIdx) && isActive && waitCount < 10) {
                                kotlinx.coroutines.delay(16)
                                waitCount++
                            }
                            if (!isActive) return@launch  // cancelado por nueva pulsación
                            // 3. Ejecutar requestFocus si el item está compuesto
                            if (focusTracker.isComposed(target.zone, scrollIdx)) {
                                try {
                                    focusTracker.resolveFocusRequester(target.zone, scrollIdx)?.requestFocus()
                                } catch (_: Exception) {}
                            }
                        }
                        true  // Consumido aunque el scroll sea asíncrono
                    }
                    is NavResult.Cancel -> {
                        // Borde del grafo: no mover foco, pero consumir la tecla para
                        // evitar que el motor TV intente escapar del área actual
                        true
                    }
                    is NavResult.NotHandled -> false
                }
            }
    ) {
        // Capa de control D-pad para Pantalla Completa (Garantiza foco 100% permanente en Xiaomi Remote)
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(playerFocusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val isLiveStream = (selectedTopTab == 0 || selectedTopTab == 1)
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            // CAMBIO DE CANALES CON CONTROL REMOTO XIAOMI (D-Pad Arriba / Channel Up)
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_CHANNEL_UP,
                            KeyEvent.KEYCODE_PAGE_UP -> {
                                if (isLiveStream && displayChannels.isNotEmpty()) {
                                    val nextIndex = if (focusedChannelIndex < displayChannels.lastIndex) focusedChannelIndex + 1 else 0
                                    focusedChannelIndex = nextIndex
                                    playerEngine.playStream(displayChannels[nextIndex].streamUrl, isLive = true)
                                    isHudVisible = true
                                    true
                                } else {
                                    isHudVisible = true
                                    true
                                }
                            }

                            // CAMBIO DE CANALES CON CONTROL REMOTO XIAOMI (D-Pad Abajo / Channel Down)
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_CHANNEL_DOWN,
                            KeyEvent.KEYCODE_PAGE_DOWN -> {
                                if (isLiveStream && displayChannels.isNotEmpty()) {
                                    val prevIndex = if (focusedChannelIndex > 0) focusedChannelIndex - 1 else displayChannels.lastIndex
                                    focusedChannelIndex = prevIndex
                                    playerEngine.playStream(displayChannels[prevIndex].streamUrl, isLive = true)
                                    isHudVisible = true
                                    true
                                } else {
                                    isHudVisible = true
                                    true
                                }
                            }

                            // D-Pad Izquierda: Abre Guía Rápida de Canales en Vivo, o Rebobina 10s en Película/Serie
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                if (isLiveStream) {
                                    isQuickZappingOpen = !isQuickZappingOpen
                                    isHudVisible = true
                                } else {
                                    playerEngine.seekBy(-10_000)
                                    seekFeedbackText = "⏪ -10s"
                                    isHudVisible = true
                                }
                                true
                            }

                            // D-Pad Derecha: Info en Vivo, o Avanza 10s en Película/Serie
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                if (isLiveStream) {
                                    isHudVisible = !isHudVisible
                                } else {
                                    playerEngine.seekBy(10_000)
                                    seekFeedbackText = "⏩ +10s"
                                    isHudVisible = true
                                }
                                true
                            }

                            // Botón Centro / OK: Toggle OSD o Play/Pause
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                if (isLiveStream) {
                                    isHudVisible = !isHudVisible
                                } else {
                                    playerEngine.togglePlayPause()
                                    isHudVisible = true
                                }
                                true
                            }

                            // Teclas multimedia dedicadas
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                playerEngine.togglePlayPause()
                                isHudVisible = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                playerEngine.resume()
                                isHudVisible = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                playerEngine.pause()
                                isHudVisible = true
                                true
                            }

                            KeyEvent.KEYCODE_BACK -> {
                                if (isQuickZappingOpen) {
                                    isQuickZappingOpen = false
                                    true
                                } else if (isHudVisible) {
                                    isHudVisible = false
                                    true
                                } else {
                                    isFullscreen = false
                                    true
                                }
                            }
                            else -> false
                        }
                    }
            )
        }
        // CAPA 1: Video de Fondo o Portada Cinemática de Alta Calidad (EveryCine Style)
        Box(modifier = Modifier.fillMaxSize()) {
            // Reproductor de video nativo para canales en vivo (Inicio y En Vivo)
            if (selectedTopTab == 0 || selectedTopTab == 1) {
                LelouchVideoPlayer(
                    playerEngine = playerEngine,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Portada Cinemática / Backdrop Artístico HD
            // Se muestra de fondo si estamos en VOD (Películas/Series) o si el video en vivo está cargando/pausado
            val showBackdrop = (selectedTopTab == 2 || selectedTopTab == 3 || selectedTopTab == 4 || playbackState !is PlaybackState.Playing)
            if (showBackdrop && !currentBackdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = currentBackdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // CAPA 2: Vignette Scrim Cinemático de Doble Eje (Vertical + Horizontal)
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            val verticalScrimBrush = remember {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to LelouchBackground.copy(alpha = 0.90f),
                        0.18f to LelouchBackground.copy(alpha = 0.40f),
                        0.45f to Color.Transparent,
                        0.65f to LelouchBackground.copy(alpha = 0.85f),
                        0.88f to LelouchBackground,
                        1.0f to LelouchBackground
                    )
                )
            }
            val horizontalScrimBrush = remember {
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to LelouchBackground.copy(alpha = 0.95f),
                        0.35f to LelouchBackground.copy(alpha = 0.70f),
                        0.65f to Color.Transparent,
                        1.0f to Color.Transparent
                    )
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Scrim Vertical: Protege la barra de pestañas superior y difumina hacia los carruseles inferiores
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(verticalScrimBrush)
                )
                // Scrim Horizontal: Oscurece el lateral izquierdo para máxima legibilidad de títulos y sinopsis
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(horizontalScrimBrush)
                )
            }
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
                // Barra de Navegación Superior Fina con TabRow oficial de Android TV
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LELOUCH",
                            color = LelouchCyanAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Chip de Lista Activa (Clic abre el gestor de listas / admin)
                        var isSourcePillFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .focusable()
                                .onFocusChanged { isSourcePillFocused = it.isFocused }
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSourcePillFocused) LelouchCyanAccent.copy(alpha = 0.35f) else LelouchSurfaceVariant)
                                .border(
                                    width = if (isSourcePillFocused) 1.5.dp else 1.dp,
                                    color = if (isSourcePillFocused) LelouchCyanAccent else LelouchBorder,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedTopTab = 5 }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeSource?.name ?: "IPTV Activa",
                                    color = if (isSourcePillFocused) Color.White else LelouchTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cambiar ▼",
                                    color = LelouchCyanAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Barra de Navegación Determinista con 4 estados visuales claros (FOCUSED != SELECTED)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            topTabs.forEachIndexed { index, title ->
                                key(index) {
                                    val tabRequester = focusTracker.getNavRequester(index)
                                    var isThisTabFocused by remember { mutableStateOf(false) }
                                    val isSelected = (selectedTopTab == index)
                                    val tabInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

                                    // 4 ESTADOS VISUALES INEQUÍVOCOS
                                    val bgColor = when {
                                        isThisTabFocused && isSelected -> LelouchCyanAccent
                                        isThisTabFocused && !isSelected -> LelouchSurfaceVariant
                                        !isThisTabFocused && isSelected -> LelouchCyanAccent.copy(alpha = 0.18f)
                                        else -> Color.Transparent
                                    }
                                    val borderColor = when {
                                        isThisTabFocused && isSelected -> Color.White
                                        isThisTabFocused && !isSelected -> LelouchCyanAccent
                                        !isThisTabFocused && isSelected -> LelouchCyanAccent.copy(alpha = 0.6f)
                                        else -> Color.Transparent
                                    }
                                    val borderWidth = when {
                                        isThisTabFocused -> 2.5.dp
                                        isSelected -> 1.5.dp
                                        else -> 1.dp
                                    }
                                    val textColor = when {
                                        isThisTabFocused && isSelected -> Color.Black
                                        isThisTabFocused && !isSelected -> Color.White
                                        !isThisTabFocused && isSelected -> LelouchCyanAccent
                                        else -> LelouchTextSecondary
                                    }
                                    val textWeight = when {
                                        isThisTabFocused && isSelected -> FontWeight.Black
                                        isThisTabFocused || isSelected -> FontWeight.Bold
                                        else -> FontWeight.Medium
                                    }

                                    Box(
                                        modifier = Modifier
                                            .focusRequester(tabRequester)
                                            .focusProperties {
                                                // Single Owner controla UP/DOWN/LEFT/RIGHT desde onPreviewKeyEvent.
                                                // focusProperties solo declara los límites de borde para
                                                // evitar que el motor TV escape por búsqueda geométrica
                                                // en caso de que un evento no sea consumido.
                                                up    = FocusRequester.Cancel
                                                down  = FocusRequester.Cancel
                                                left  = FocusRequester.Cancel
                                                right = FocusRequester.Cancel
                                            }
                                            .onFocusChanged {
                                                isThisTabFocused = it.isFocused
                                                if (it.isFocused) {
                                                    focusedTopTab = index
                                                    val tag = focusTracker.getNavTabTag(index)
                                                    focusTracker.onFocusChanged(
                                                        tag = tag,
                                                        zone = TvFocusZone.TOP_NAV,
                                                        rowIndex = 0,
                                                        cardIndex = index,
                                                        isFocused = true
                                                    )
                                                }
                                            }
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown &&
                                                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                                                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                                                ) {
                                                    selectedTopTab = index
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bgColor)
                                            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                                            .clickable(
                                                interactionSource = tabInteraction,
                                                indication = null
                                            ) {
                                                selectedTopTab = index
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected && !isThisTabFocused) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(LelouchCyanAccent)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = title,
                                                fontSize = 13.sp,
                                                fontWeight = textWeight,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Botón de Búsqueda FTS5 (Focusable TV)
                        var isSearchFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .focusable()
                                .onFocusChanged { isSearchFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSearchFocused) LelouchCyanAccent.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    width = if (isSearchFocused) 1.5.dp else 1.dp,
                                    color = if (isSearchFocused) LelouchCyanAccent else LelouchBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { isSearchModalVisible = true }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = if (isSearchFocused) LelouchCyanAccent else LelouchTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Buscar",
                                    color = if (isSearchFocused) LelouchTextPrimary else LelouchTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Botón Panel Administrador & Gestor de Listas (Focusable TV)
                        var isAdminFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .focusable()
                                .onFocusChanged { isAdminFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAdminFocused || selectedTopTab == 5) LelouchCyanAccent.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    width = if (isAdminFocused || selectedTopTab == 5) 1.5.dp else 1.dp,
                                    color = if (isAdminFocused || selectedTopTab == 5) LelouchCyanAccent else LelouchBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedTopTab = 5
                                    isAdminModalVisible = true
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Admin",
                                    tint = if (isAdminFocused || selectedTopTab == 5) LelouchCyanAccent else LelouchTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "⚙️ Admin",
                                    color = if (isAdminFocused || selectedTopTab == 5) LelouchTextPrimary else LelouchTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Indicador de Conexión & Resolución
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
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

                // Spotlight Hero Dinámico (EveryCine Style)
                if (selectedTopTab != 5) {
                    item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 20.dp, bottom = 24.dp, end = 120.dp)
                    ) {
                        val heroTitle = when (selectedTopTab) {
                            2 -> focusedHeroMovie?.name ?: "PELÍCULAS EN TENDENCIA"
                            3 -> focusedHeroSeries?.name ?: "SERIES DESTACADAS"
                            else -> {
                                if (focusedHeroMovie != null && selectedTopTab == 0) focusedHeroMovie!!.name
                                else if (focusedHeroSeries != null && selectedTopTab == 0) focusedHeroSeries!!.name
                                else focusedChannel.name
                            }
                        }
                        val heroSubtitle = when (selectedTopTab) {
                            2 -> focusedHeroMovie?.plot ?: "Estrenos y Clásicos en Máxima Calidad 4K UHD"
                            3 -> focusedHeroSeries?.plot ?: "Temporadas Completas en Streaming de Alta Definición"
                            else -> {
                                if (focusedHeroMovie != null && selectedTopTab == 0) focusedHeroMovie?.plot ?: "Película en Catálogo"
                                else if (focusedHeroSeries != null && selectedTopTab == 0) focusedHeroSeries?.plot ?: "Serie Completa"
                                else focusedChannel.currentProgram
                            }
                        }
                        val heroBadge = when (selectedTopTab) {
                            2 -> "4K UHD"
                            3 -> "SERIE"
                            else -> {
                                if (focusedHeroMovie != null && selectedTopTab == 0) "4K UHD"
                                else if (focusedHeroSeries != null && selectedTopTab == 0) "SERIE"
                                else "EN VIVO"
                            }
                        }

                        val heroMeta = when (selectedTopTab) {
                            2 -> "★ ${focusedHeroMovie?.rating ?: 8.5}  •  ${focusedHeroMovie?.year ?: "2024"}  •  ${focusedHeroMovie?.categoryName.takeIf { !it.isNullOrBlank() } ?: "Cine"}"
                            3 -> "★ ${focusedHeroSeries?.rating ?: 8.8}  •  ${focusedHeroSeries?.releaseDate?.take(4) ?: "2024"}  •  ${focusedHeroSeries?.seasonsCount ?: 1} Temporadas"
                            else -> {
                                if (focusedHeroMovie != null && selectedTopTab == 0) {
                                    "★ ${focusedHeroMovie?.rating ?: 8.5}  •  ${focusedHeroMovie?.year ?: "2024"}  •  ${focusedHeroMovie?.categoryName.takeIf { !it.isNullOrBlank() } ?: "Cine"}"
                                } else if (focusedHeroSeries != null && selectedTopTab == 0) {
                                    "★ ${focusedHeroSeries?.rating ?: 8.8}  •  ${focusedHeroSeries?.releaseDate?.take(4) ?: "2024"}  •  ${focusedHeroSeries?.seasonsCount ?: 1} Temporadas"
                                } else {
                                    "CH ${focusedChannel.num}  •  ${focusedChannel.categoryName}  •  ${videoInfo.resolutionLabel}"
                                }
                            }
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
                                text = heroMeta,
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botones de Acción Hero Spotlight
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            val playInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val isMoviesTab = (selectedTopTab == 2)
                            Box(
                                modifier = Modifier
                                    .then(if (isMoviesTab) Modifier.focusRequester(focusTracker.heroPlayAnchor) else Modifier)
                                    // Single Owner (onPreviewKeyEvent) controla UP/DOWN/LEFT/RIGHT.
                                    // focusProperties se retira de hero_play para no competir con el grafo.
                                    // El FocusRequester es suficiente para que el grafo pueda llamar requestFocus().
                                    .onFocusChanged {
                                        isPlayFocused = it.isFocused
                                        if (it.isFocused) {
                                            focusTracker.onFocusChanged("hero_play", TvFocusZone.HERO, rowIndex = 0, cardIndex = 0)
                                        }
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPlayFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                                    .border(
                                        width = if (isPlayFocused) 2.5.dp else 1.dp,
                                        color = if (isPlayFocused) Color.White else LelouchBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(
                                        interactionSource = playInteraction,
                                        indication = null
                                    ) {
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
                                            } ?: run {
                                                playerEngine.playStream("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", isLive = false)
                                            }
                                            isFullscreen = true
                                        } else if (selectedTopTab == 3 && focusedHeroSeries != null) {
                                            openSeriesDetails(focusedHeroSeries!!)
                                        } else {
                                            isFullscreen = true
                                        }
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
                                        text = if (selectedTopTab == 2) "Ver Película (OK)" else if (selectedTopTab == 3) "Ver Serie (OK)" else "Ver Pantalla Completa (OK)",
                                        color = if (isPlayFocused) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (selectedTopTab == 0 || selectedTopTab == 1) {
                                var isReloadFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .focusable()
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
                                        .focusable()
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
                            } else {
                                // Botón de Más Detalles para Películas y Series
                                var isDetailFocused by remember { mutableStateOf(false) }
                                val detailInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .then(if (isMoviesTab) Modifier.focusRequester(focusTracker.heroDetailAnchor) else Modifier)
                                        // Single Owner (onPreviewKeyEvent) controla UP/DOWN/LEFT/RIGHT.
                                        // focusProperties se retira de hero_detail para no competir con el grafo.
                                        // El FocusRequester es suficiente para que el grafo pueda llamar requestFocus().
                                        .onFocusChanged {
                                            isDetailFocused = it.isFocused
                                            if (it.isFocused) {
                                                focusTracker.onFocusChanged("hero_detail", TvFocusZone.HERO, rowIndex = 0, cardIndex = 1)
                                            }
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDetailFocused) LelouchSurfaceVariant else Color.Transparent)
                                        .border(
                                            width = if (isDetailFocused) 2.5.dp else 1.dp,
                                            color = if (isDetailFocused) LelouchCyanAccent else LelouchBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable(
                                            interactionSource = detailInteraction,
                                            indication = null
                                        ) {
                                            if (selectedTopTab == 2 && focusedHeroMovie != null) {
                                                activeDetailMedia = MediaDetailUiModel(
                                                    id = focusedHeroMovie!!.streamId,
                                                    title = focusedHeroMovie!!.name,
                                                    posterUrl = focusedHeroMovie!!.streamIcon,
                                                    backdropUrl = focusedHeroMovie!!.backdropPath,
                                                    rating = focusedHeroMovie!!.rating ?: 0.0,
                                                    year = focusedHeroMovie!!.year,
                                                    synopsis = focusedHeroMovie!!.plot ?: "",
                                                    genre = focusedHeroMovie!!.categoryName,
                                                    isSeries = false,
                                                    isFavorite = focusedHeroMovie!!.isFavorite
                                                )
                                            } else if (selectedTopTab == 3 && focusedHeroSeries != null) {
                                                openSeriesDetails(focusedHeroSeries!!)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = if (isDetailFocused) Color.White else LelouchTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Más Detalles",
                                            color = if (isDetailFocused) Color.White else LelouchTextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isDetailFocused) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

                // SECCIÓN ADMIN Y GESTOR DE LISTAS (Pestaña 5: ⚙️ Admin)
                if (selectedTopTab == 5) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = "⚙️ PANEL ADMINISTRADOR & GESTOR DE LISTAS",
                                color = LelouchCyanAccent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cambia al instante entre tus proveedores IPTV registrados, sincroniza cuentas de la nube o administra conexiones.",
                                color = LelouchTextSecondary,
                                fontSize = 14.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Acciones de Mantenimiento
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                var isCloudBtnFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .focusable()
                                        .onFocusChanged { isCloudBtnFocused = it.isFocused }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCloudBtnFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                                        .border(1.dp, if (isCloudBtnFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(8.dp))
                                        .clickable { onSyncCloudSources() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = null,
                                            tint = if (isCloudBtnFocused) Color.Black else LelouchCyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "☁️ Sincronizar Supabase",
                                            color = if (isCloudBtnFocused) Color.Black else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                var isResyncFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .focusable()
                                        .onFocusChanged { isResyncFocused = it.isFocused }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isResyncFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                                        .border(1.dp, if (isResyncFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(8.dp))
                                        .clickable { onForceSync() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = if (isResyncFocused) Color.Black else LelouchCyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "🔄 Re-sincronizar Catálogo",
                                            color = if (isResyncFocused) Color.Black else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                var isLogoutFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .focusable()
                                        .onFocusChanged { isLogoutFocused = it.isFocused }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isLogoutFocused) LelouchLiveRed else LelouchSurfaceVariant)
                                        .border(1.dp, if (isLogoutFocused) LelouchLiveRed else LelouchBorder, RoundedCornerShape(8.dp))
                                        .clickable { onLogout() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Logout,
                                            contentDescription = null,
                                            tint = if (isLogoutFocused) Color.White else LelouchLiveRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "🚪 Cerrar Sesión",
                                            color = if (isLogoutFocused) Color.White else LelouchLiveRed,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            Text(
                                text = "📋 TUS LISTAS IPTV DISPONIBLES (${allSources.size}):",
                                color = LelouchTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    itemsIndexed(allSources) { _, source ->
                        val isActive = (source.id == activeSource?.id || source.isActive)
                        var isCardFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 6.dp)
                                .focusable()
                                .onFocusChanged { isCardFocused = it.isFocused }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCardFocused) LelouchCardFocused else if (isActive) LelouchSurfaceVariant else LelouchSurface)
                                .border(
                                    width = if (isCardFocused) 2.dp else if (isActive) 1.5.dp else 1.dp,
                                    color = if (isCardFocused) LelouchCyanAccent else if (isActive) Color(0xFF10B981) else LelouchBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (!isActive) {
                                        onActivateSource(source.id)
                                        selectedTopTab = 0
                                    }
                                }
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = source.name,
                                            color = LelouchTextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF10B981))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "🟢 LISTA ACTIVA",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(LelouchTextMuted.copy(alpha = 0.3f))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "⚪ DISPONIBLE",
                                                    color = LelouchTextSecondary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Servidor: ${source.serverUrl}  •  Usuario: ${source.username}",
                                        color = LelouchTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (!isActive) {
                                        var isActBtnFocused by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .focusable()
                                                .onFocusChanged { isActBtnFocused = it.isFocused }
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isActBtnFocused) Color.White else LelouchCyanAccent)
                                                .clickable {
                                                    onActivateSource(source.id)
                                                    selectedTopTab = 0
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "⚡ ACTIVAR (OK)",
                                                color = Color.Black,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    if (allSources.size > 1) {
                                        var isDelBtnFocused by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .focusable()
                                                .onFocusChanged { isDelBtnFocused = it.isFocused }
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isDelBtnFocused) LelouchLiveRed else Color.Transparent)
                                                .border(1.dp, LelouchBorder, RoundedCornerShape(6.dp))
                                                .clickable { onDeleteSource(source.id) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "🗑️",
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECCIÓN 1: Canales en Vivo (Visible en Inicio, En Vivo y Favoritos)
                if (selectedTopTab == 0 || selectedTopTab == 1 || selectedTopTab == 4) {
                    item {
                        Text(
                            text = if (selectedTopTab == 4) "⭐ Canales Favoritos" else "🔴 Canales en Directo",
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
                                    streamIcon = stream.streamIcon,
                                    currentProgram = "Canal Favorito",
                                    streamUrl = activeSource?.let {
                                        XtreamUrlBuilder.buildLiveStreamUrl(it.serverUrl, it.username, it.password, stream.streamId, "m3u8")
                                    } ?: ""
                                )
                            }
                        } else displayChannels

                        TvLazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = channelsToRender,
                                key = { _, channel -> "live_ch_${channel.streamId}" }
                            ) { index, channel ->
                                val isSelected = (index == focusedChannelIndex)
                                TvChannelCard(
                                    channel = channel,
                                    isSelected = isSelected,
                                    onFocused = {
                                        focusedChannelIndex = index
                                        if (selectedTopTab == 0) {
                                            focusedHeroMovie = null
                                            focusedHeroSeries = null
                                        }
                                    },
                                    onClick = { isFullscreen = true }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // SECCIÓN 2: Películas Recientes y Populares (Inicio, Películas y Favoritos)
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
                        } else {
                            displayMovies
                        }

                        // recentRowState se declara a nivel superior del composable
                        // para permitir que el Single Owner (onPreviewKeyEvent) coordine
                        // el scroll antes de requestFocus() cuando el item no está compuesto.
                        TvLazyRow(
                            state = recentRowState,
                            // focusRestorer() eliminado: Single Owner + grafo determinista
                            // controla 100% de la navegación en Movies. (FIX F6)
                            modifier = Modifier,
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = moviesToRender,
                                key = { _, movie -> "movie_${movie.streamId}" }
                            ) { index, movie ->
                                DisposableEffect(index) {
                                    focusTracker.composedRecentIndices.add(index)
                                    onDispose {
                                        focusTracker.composedRecentIndices.remove(index)
                                    }
                                }

                                val cardRequester = focusTracker.getRecentRequester(index)
                                val cardTag = "movies_recent_${movie.streamId}"
                                val isMoviesTab = (selectedTopTab == 2)
                                val cardModifier = if (isMoviesTab) {
                                    Modifier
                                        .focusRequester(cardRequester)
                                        // focusProperties en RAIL_RECENT: solo límites de borde.
                                        // UP/DOWN son responsabilidad del Single Owner (onPreviewKeyEvent).
                                        // Eliminado el bridge competidor a heroPlayAnchor y getTopRatedRequester.
                                        // (FIX F5 + FIX F6)
                                        .focusProperties {
                                            if (index == 0) left = FocusRequester.Cancel
                                            if (index == moviesToRender.lastIndex) right = FocusRequester.Cancel
                                            up   = FocusRequester.Cancel
                                            down = FocusRequester.Cancel
                                        }
                                } else Modifier

                                TvPosterCard(
                                    title = movie.name,
                                    posterUrl = movie.streamIcon,
                                    rating = movie.rating ?: 0.0,
                                    year = movie.year,
                                    modifier = cardModifier,
                                    onFocused = {
                                        focusedHeroMovie = movie
                                        focusedHeroSeries = null
                                        focusTracker.onFocusChanged(cardTag, TvFocusZone.RAIL_RECENT, rowIndex = 1, cardIndex = index)
                                    },
                                    onClick = {
                                        activeDetailMedia = MediaDetailUiModel(
                                            id = movie.streamId,
                                            title = movie.name,
                                            posterUrl = movie.streamIcon,
                                            backdropUrl = movie.backdropPath,
                                            rating = movie.rating ?: 0.0,
                                            year = movie.year,
                                            synopsis = movie.plot ?: "Película en catálogo.",
                                            genre = movie.categoryName,
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

                // SECCIÓN 2B: Películas Más Valoradas (Solo en pestaña Películas)
                if (selectedTopTab == 2) {
                    item {
                        Text(
                            text = "⭐ Más Valoradas (Top Rated)",
                            color = LelouchTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )

                        val topRatedMovies = remember(displayMovies) {
                            displayMovies.sortedByDescending { it.rating ?: 0.0 }
                        }

                        // topRatedRowState se declara a nivel superior del composable.
                        TvLazyRow(
                            state = topRatedRowState,
                            // focusRestorer() eliminado: Single Owner controla navegación en Movies. (FIX F6)
                            modifier = Modifier,
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = topRatedMovies,
                                key = { _, movie -> "top_movie_${movie.streamId}" }
                            ) { index, movie ->
                                DisposableEffect(index) {
                                    focusTracker.composedTopRatedIndices.add(index)
                                    onDispose {
                                        focusTracker.composedTopRatedIndices.remove(index)
                                    }
                                }

                                val cardRequester = focusTracker.getTopRatedRequester(index)
                                val cardTag = "movies_top_${movie.streamId}"
                                val cardModifier = Modifier
                                    .focusRequester(cardRequester)
                                    // focusProperties en RAIL_TOP_RATED: solo límites de borde.
                                    // UP/DOWN son responsabilidad del Single Owner (onPreviewKeyEvent).
                                    // Eliminado el bridge competidor a getRecentRequester. (FIX F5 + FIX F6)
                                    .focusProperties {
                                        if (index == 0) left = FocusRequester.Cancel
                                        if (index == topRatedMovies.lastIndex) right = FocusRequester.Cancel
                                        up   = FocusRequester.Cancel
                                        down = FocusRequester.Cancel
                                    }

                                TvPosterCard(
                                    title = movie.name,
                                    posterUrl = movie.streamIcon,
                                    rating = movie.rating ?: 0.0,
                                    year = movie.year,
                                    modifier = cardModifier,
                                    onFocused = {
                                        focusedHeroMovie = movie
                                        focusTracker.onFocusChanged(cardTag, TvFocusZone.RAIL_TOP_RATED, rowIndex = 2, cardIndex = index)
                                    },
                                    onClick = {
                                        activeDetailMedia = MediaDetailUiModel(
                                            id = movie.streamId,
                                            title = movie.name,
                                            posterUrl = movie.streamIcon,
                                            backdropUrl = movie.backdropPath,
                                            rating = movie.rating ?: 0.0,
                                            year = movie.year,
                                            synopsis = movie.plot ?: "Película en catálogo.",
                                            genre = movie.categoryName,
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

                // SECCIÓN 3: Series Populares (Inicio y Series)
                if (selectedTopTab == 0 || selectedTopTab == 3) {
                    item {
                        Text(
                            text = "📺 Series Populares",
                            color = LelouchTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )

                        TvLazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = displaySeries,
                                key = { _, series -> "series_${series.seriesId}" }
                            ) { _, series ->
                                TvPosterCard(
                                    title = series.name,
                                    posterUrl = series.cover,
                                    rating = series.rating ?: 0.0,
                                    year = series.releaseDate?.take(4),
                                    onFocused = {
                                        focusedHeroSeries = series
                                        focusedHeroMovie = null
                                    },
                                    onClick = {
                                        openSeriesDetails(series)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // SECCIÓN 3B: Series en Tendencia (Pestaña Series)
                if (selectedTopTab == 3) {
                    item {
                        Text(
                            text = "🔥 En Emisión / Tendencias",
                            color = LelouchTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )

                        val trendingSeries = remember(displaySeries) {
                            displaySeries.reversed()
                        }

                        TvLazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = trendingSeries,
                                key = { _, series -> "trend_series_${series.seriesId}" }
                            ) { _, series ->
                                TvPosterCard(
                                    title = series.name,
                                    posterUrl = series.cover,
                                    rating = series.rating ?: 0.0,
                                    year = series.releaseDate?.take(4),
                                    onFocused = {
                                        focusedHeroSeries = series
                                    },
                                    onClick = {
                                        openSeriesDetails(series)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Estado vacío amigable para Favoritos
                if (selectedTopTab == 4 && favoriteChannels.isEmpty() && favoriteMovies.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = LelouchCyanAccent,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Aún no tienes elementos en Favoritos",
                                color = LelouchTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Explora En Vivo, Películas o Series y presiona el botón ⭐ para acceder a tus contenidos preferidos rápidamente aquí.",
                                color = LelouchTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
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

        // CAPA 5: Mini-Guía Carrusel HUD en Pantalla Completa (con iconos de canales)
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
                        modifier = Modifier.focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = displayChannels,
                            key = { _, channel -> "hud_ch_${channel.streamId}" }
                        ) { index, channel ->
                            val isSelected = (index == focusedChannelIndex)
                            var isCardFocused by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .width(185.dp)
                                    .height(72.dp)
                                    .focusable()
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
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Logo miniatura
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp, 34.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!channel.streamIcon.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.streamIcon,
                                                contentDescription = channel.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text(
                                                text = channel.name.take(3).uppercase(),
                                                color = LelouchCyanAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CH ${channel.num} - ${channel.name}",
                                            color = if (isCardFocused || isSelected) LelouchCyanAccent else LelouchTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = channel.currentProgram,
                                            color = LelouchTextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
                        backdropUrl = movie.backdropPath,
                        rating = movie.rating ?: 0.0,
                        year = movie.year,
                        synopsis = movie.plot ?: "",
                        genre = movie.categoryName,
                        isSeries = false,
                        isFavorite = movie.isFavorite
                    )
                    isSearchModalVisible = false
                },
                onSelectSeries = { series ->
                    openSeriesDetails(series)
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

        // CAPA 8: Modal de Panel Administrador & Gestor de Listas
        if (isAdminModalVisible) {
            TvAdminPanelModal(
                activeSource = activeSource,
                allSources = allSources,
                onActivateSource = { srcId ->
                    onActivateSource(srcId)
                    isAdminModalVisible = false
                },
                onDeleteSource = { srcId ->
                    onDeleteSource(srcId)
                },
                onAddSource = { url, user, pass, name ->
                    onAddSource(url, user, pass, name)
                },
                onSyncCloudSources = onSyncCloudSources,
                onForceSync = onForceSync,
                onLogout = {
                    onLogout()
                    isAdminModalVisible = false
                },
                onDismiss = { isAdminModalVisible = false }
            )
        }

        // CAPA 9: Modal de Detalle de Película / Serie y Selector de Episodios
        if (activeDetailMedia != null) {
            TvMediaDetailModal(
                media = activeDetailMedia!!,
                onPlayClick = { episodeId ->
                    if (activeDetailMedia!!.isSeries) {
                        val ep = activeDetailMedia!!.episodes.find { it.episodeId == episodeId }
                            ?: activeDetailMedia!!.episodes.firstOrNull()
                        if (ep != null && ep.streamUrl.isNotEmpty()) {
                            playerEngine.playStream(ep.streamUrl, isLive = false)
                        } else {
                            activeSource?.let { src ->
                                val fallbackUrl = XtreamUrlBuilder.buildSeriesStreamUrl(
                                    src.serverUrl,
                                    src.username,
                                    src.password,
                                    episodeId ?: activeDetailMedia!!.id,
                                    "mp4"
                                )
                                playerEngine.playStream(fallbackUrl, isLive = false)
                            }
                        }
                    } else {
                        activeSource?.let { src ->
                            val movieUrl = XtreamUrlBuilder.buildVodStreamUrl(
                                src.serverUrl,
                                src.username,
                                src.password,
                                activeDetailMedia!!.id,
                                "mp4"
                            )
                            playerEngine.playStream(movieUrl, isLive = false)
                        }
                    }
                    isFullscreen = true
                    activeDetailMedia = null
                },
                onToggleFavorite = {
                    if (!activeDetailMedia!!.isSeries) {
                        onToggleFavoriteMovie(activeDetailMedia!!.id, !activeDetailMedia!!.isFavorite)
                    }
                    activeDetailMedia = activeDetailMedia?.copy(isFavorite = !activeDetailMedia!!.isFavorite)
                },
                onDismiss = { activeDetailMedia = null }
            )
        }

        // CAPA 10: Drawer de Zapping Rápido de Canales (para mandos Xiaomi en Pantalla Completa)
        if (isFullscreen && isQuickZappingOpen) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(LelouchBackground.copy(alpha = 0.94f))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📺 ZAPPING RÁPIDO",
                            color = LelouchCyanAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "BACK: Salir",
                            color = LelouchTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TvLazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(displayChannels) { idx, ch ->
                            var isChFocused by remember { mutableStateOf(false) }
                            val isCurrentPlaying = (idx == focusedChannelIndex)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusable()
                                    .onFocusChanged { isChFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isChFocused -> LelouchCardFocused
                                            isCurrentPlaying -> LelouchCyanAccent.copy(alpha = 0.2f)
                                            else -> LelouchSurface
                                        }
                                    )
                                    .border(
                                        width = if (isChFocused) 2.dp else if (isCurrentPlaying) 1.dp else 0.dp,
                                        color = if (isChFocused) LelouchCyanAccent else if (isCurrentPlaying) LelouchCyanAccent.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        focusedChannelIndex = idx
                                        playerEngine.playStream(ch.streamUrl, isLive = true)
                                        isQuickZappingOpen = false
                                        isHudVisible = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${ch.num}",
                                        color = if (isChFocused || isCurrentPlaying) LelouchCyanAccent else LelouchTextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(36.dp)
                                    )

                                    if (!ch.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.streamIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp, 22.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ch.name,
                                            color = if (isChFocused) Color.White else LelouchTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = ch.currentProgram,
                                            color = LelouchTextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isCurrentPlaying) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(LelouchLiveRed)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // CAPA 11: Feedback Flotante de Búsqueda/Avance (ej: ⏪ -10s o ⏩ +10s para control Xiaomi)
        if (seekFeedbackText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(1.5.dp, LelouchCyanAccent, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = seekFeedbackText!!,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // HUD solo DEBUG (condicionado por BuildConfig.DEBUG)
        if (com.lelouch.feature.tv.BuildConfig.DEBUG && !isFullscreen) {
            FocusDebugHud(
                tracker = focusTracker,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * Tarjeta de Canal para Android TV con contenedor oficial de logo de canal,
 * escala D-Pad 1.08x, borde Cyan con brillo e indicador LIVE.
 */
@Composable
fun TvChannelCard(
    channel: ChannelUiModel,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "channelCardScale"
    )

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(215.dp)
            .height(125.dp)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isFocused -> LelouchCardFocused
                    isSelected -> LelouchSurfaceVariant
                    else -> LelouchSurface
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) LelouchCyanAccent else if (isSelected) LelouchCyanAccent.copy(alpha = 0.5f) else LelouchBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Fila Superior: Logo del Canal + Badge LIVE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contenedor del Logo de la Señal (62x38 dp)
                Box(
                    modifier = Modifier
                        .size(width = 62.dp, height = 38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(0.5.dp, LelouchBorder, RoundedCornerShape(6.dp))
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.streamIcon.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.streamIcon,
                            contentDescription = channel.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = channel.name.take(3).uppercase(),
                            color = LelouchCyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CH ${channel.num}",
                        color = LelouchCyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(LelouchLiveRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Fila Inferior: Nombre y Programa en Emisión
            Column {
                Text(
                    text = channel.name,
                    color = LelouchTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
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
