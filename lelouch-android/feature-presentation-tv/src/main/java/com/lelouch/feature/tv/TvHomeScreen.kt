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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.Category
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.Series
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.Episode
import com.lelouch.core.model.VodMovie
import com.lelouch.core.network.StreamUrlResolver
import com.lelouch.core.player.LelouchVideoPlayer
import com.lelouch.core.player.PlaybackState
import com.lelouch.core.player.rememberLelouchPlayer
import com.lelouch.feature.tv.components.*
import com.lelouch.feature.tv.focus.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.compose.itemContentType
import androidx.paging.map
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import androidx.compose.runtime.snapshotFlow
import com.lelouch.core.domain.repository.ChannelRepository
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.util.UnstableApi
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.SeriesEntity

data class ChannelUiModel(
    val id: String = "",
    val streamId: Int,
    val name: String,
    val num: Int,
    val categoryName: String,
    val categoryId: String = "",
    val streamIcon: String? = null,
    val currentProgram: String = "Transmisión en Directo",
    val nextProgram: String = "Continuación de Programación",
    val streamUrl: String = "",
    val isFavorite: Boolean = false,
    val sourceId: String = ""
)

data class MovieUiModel(
    val id: String = "",
    val streamId: Int = 0,
    val name: String = "",
    val categoryName: String = "",
    val categoryId: String = "",
    val streamIcon: String? = null,
    val streamUrl: String = "",
    val rating: Double = 0.0,
    val year: String? = null,
    val containerExtension: String = "mp4",
    val isFavorite: Boolean = false,
    val backdropPath: String? = null,
    val plot: String? = null,
    val genre: String? = null
)

data class SeriesUiModel(
    val id: String = "",
    val seriesId: Int = 0,
    val name: String = "",
    val categoryName: String = "",
    val categoryId: String = "",
    val cover: String? = null,
    val backdropPath: String? = null,
    val rating: Double = 0.0,
    val releaseDate: String? = null,
    val seasonsCount: Int = 0,
    val plot: String? = null,
    val genre: String? = null,
    val isFavorite: Boolean = false,
    val sourceId: String = ""
)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, UnstableApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun TvHomeScreen(
    activeSource: SourceConfig? = null,
    allSources: List<SourceConfig> = emptyList(),
    liveChannels: List<LiveStream> = emptyList(),
    channelRepository: ChannelRepository? = null,
    vodRepository: com.lelouch.core.domain.repository.VodRepository? = null,
    seriesRepository: com.lelouch.core.domain.repository.SeriesRepository? = null,
    liveCategories: List<Category> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    recentMovies: List<VodMovie> = emptyList(),
    featuredSeries: List<Series> = emptyList(),
    vodCategories: List<Category> = emptyList(),
    seriesList: List<Series> = emptyList(),
    seriesCategories: List<Category> = emptyList(),
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
    // FASE 33: último estado de la sincronización, para el panel de diagnóstico.
    syncStateText: String = "",
    onFetchSeriesDetails: (suspend (seriesId: Int) -> Pair<List<Int>, List<Episode>>)? = null
) {
    val playerEngine = rememberLelouchPlayer()
    val playbackState by playerEngine.playbackState.collectAsStateWithLifecycle()
    val videoInfo by playerEngine.videoTrackInfo.collectAsStateWithLifecycle()

    // Mapas de categorías para resolver nombres de categorías faltantes en Xtream Codes
    val liveCatMap = remember(liveCategories) {
        liveCategories.associate { it.categoryId to it.categoryName }
    }
    val vodCatMap = remember(vodCategories) {
        vodCategories.associate { it.categoryId to it.categoryName }
    }
    val seriesCatMap = remember(seriesCategories) {
        seriesCategories.associate { it.categoryId to it.categoryName }
    }

    // FASE 33: Regex defensivo para evitar que episodios de series se cuelen en TV en Vivo
    val seriesLeakRegex = remember { Regex("(?i)\\b(s\\d{1,2}|t\\d{1,2}|cap\\.?\\s*\\d+|ep\\.?\\s*\\d+)\\b") }

    // Canales mapeados o canales de demostración con logos reales de alta resolución
    val displayChannels = remember(liveChannels, activeSource, liveCatMap) {
        val cleanChannels = liveChannels.filter { stream ->
            (stream.streamType.isBlank() || stream.streamType.equals("live", ignoreCase = true)) &&
            !seriesLeakRegex.containsMatchIn(stream.name)
        }.distinctBy { ch -> ch.streamUrl.ifBlank { ch.name } }
        if (cleanChannels.isNotEmpty()) {
            cleanChannels.mapIndexed { index, stream ->
                // FASE 33: el resolvedor único evita reconstruir URLs sobre Vercel (404).
                // Si no hay URL directa ni fuente Xtream legítima, queda vacío y la UI lo indica.
                val streamUrl = StreamUrlResolver.resolve(
                    directUrl = stream.streamUrl,
                    source = activeSource,
                    streamId = stream.streamId,
                    kind = StreamUrlResolver.Kind.LIVE,
                    extension = "m3u8"
                )

                val resolvedCategoryName = liveCatMap[stream.categoryId]
                    ?: stream.categoryName.takeIf { it.isNotBlank() }
                    ?: "General"

                ChannelUiModel(
                    streamId = stream.streamId,
                    name = stream.name,
                    num = stream.num.takeIf { it > 0 } ?: (index + 1),
                    categoryName = resolvedCategoryName,
                    categoryId = stream.categoryId,
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
                    categoryId = "deportes",
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
                    categoryId = "deportes",
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
                    categoryId = "deportes",
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
                    categoryId = "deportes",
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
                    categoryId = "cine",
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
                    categoryId = "entretenimiento",
                    streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Star_Channel_2021.svg/512px-Star_Channel_2021.svg.png",
                    currentProgram = "Los Simpson: Maratón Especial",
                    nextProgram = "Futurama",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                )
            )
        }
    }

    // Catálogo de películas (real o demo enriquecido con carátulas y fondos HD)
    val displayMovies = remember(recentMovies, movies, vodCatMap) {
        val srcMovies = if (recentMovies.isNotEmpty()) recentMovies else movies
        if (srcMovies.isNotEmpty()) {
            srcMovies.map { mov ->
                val resolvedName = vodCatMap[mov.categoryId] ?: mov.categoryName.takeIf { it.isNotBlank() } ?: "General"
                mov.copy(categoryName = resolvedName)
            }
        } else listOf(
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

    // Catálogo de series para spotlight hero / carrusel de inicio (utiliza featuredSeries si está disponible)
    val displaySeries = remember(seriesList, featuredSeries, seriesCatMap) {
        val srcSeries = if (featuredSeries.isNotEmpty()) featuredSeries else seriesList
        if (srcSeries.isNotEmpty()) {
            srcSeries.map { ser ->
                val resolvedName = seriesCatMap[ser.categoryId] ?: ser.categoryName.takeIf { it.isNotBlank() } ?: "General"
                ser.copy(categoryName = resolvedName)
            }
        } else listOf(
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

    var isSearchModalVisible by remember { mutableStateOf(false) }
    var isEpgModalVisible by remember { mutableStateOf(false) }
    var isAdminModalVisible by remember { mutableStateOf(false) }
    var isCategoryManagerVisible by remember { mutableStateOf(false) }
    var categoryManagerInitialScope by remember { mutableStateOf(CategoryScope.LIVE) }
    var isUpdateModalVisible by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val hiddenPrefs = remember { context.getSharedPreferences("iptv_hidden_categories", android.content.Context.MODE_PRIVATE) }
    var hiddenLiveCategories by remember {
        mutableStateOf(hiddenPrefs.getStringSet("live", emptySet())?.toSet() ?: emptySet())
    }
    var hiddenMovieCategories by remember {
        mutableStateOf(hiddenPrefs.getStringSet("movies", emptySet())?.toSet() ?: emptySet())
    }
    var hiddenSeriesCategories by remember {
        mutableStateOf(hiddenPrefs.getStringSet("series", emptySet())?.toSet() ?: emptySet())
    }

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

    val visibleChannels = remember(displayChannels, hiddenLiveCategories) {
        displayChannels.filter { ch ->
            ch.categoryId !in hiddenLiveCategories && ch.categoryName !in hiddenLiveCategories
        }
    }
    val visibleMovies = remember(displayMovies, hiddenMovieCategories) {
        displayMovies.filter { mov ->
            mov.categoryId !in hiddenMovieCategories && mov.categoryName !in hiddenMovieCategories
        }
    }
    val visibleSeries = remember(displaySeries, hiddenSeriesCategories) {
        displaySeries.filter { ser ->
            ser.categoryId !in hiddenSeriesCategories && ser.categoryName !in hiddenSeriesCategories
        }
    }

    var focusedChannelIndex by remember { mutableIntStateOf(0) }
    var focusedChannelId by remember { mutableStateOf<String?>(null) }
    var focusedChannel by remember { mutableStateOf<ChannelUiModel?>(null) }

    // Elementos destacados en el Spotlight Hero
    var focusedHeroMovie by remember(visibleMovies) { mutableStateOf<VodMovie?>(visibleMovies.firstOrNull()) }
    var focusedHeroSeries by remember(visibleSeries) { mutableStateOf<Series?>(visibleSeries.firstOrNull()) }
    var activeDetailMedia by remember { mutableStateOf<MediaDetailUiModel?>(null) }

    var isFullscreen by remember { mutableStateOf(false) }
    var isHudVisible by remember { mutableStateOf(false) }
    var isQuickZappingOpen by remember { mutableStateOf(false) }
    var isPlayingLive by remember { mutableStateOf(true) }
    var currentPlayingTitle by remember { mutableStateOf<String?>(null) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    val focusTracker = remember { FocusTracker() }
    var focusedTopTab by remember { mutableIntStateOf(1) }
    var selectedTopTab by remember { mutableIntStateOf(1) } // 0:Buscar, 1:Inicio, 2:En Vivo, 3:Películas, 4:Series, 5:Favoritos, 6:Ajustes
    val sidebarRequesters = remember { List(8) { FocusRequester() } }
    val contentFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(350)
        try {
            contentFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(selectedTopTab) {
        focusTracker.currentScreen = when (selectedTopTab) {
            0 -> "SEARCH"
            1 -> "HOME"
            2 -> "LIVE"
            3 -> "MOVIES"
            4 -> "SERIES"
            5 -> "FAVORITES"
            else -> "ADMIN"
        }
    }

    var lastFullscreenEntryTime by remember { mutableLongStateOf(0L) }

    // Modelos para el gestor de visibilidad con conteo exacto de elementos (Paso 9: Sin iterar catálogo Live)
    val liveCategoryItemModels = remember(liveCategories) {
        if (liveCategories.isNotEmpty()) {
            liveCategories.map { cat ->
                val isAdult = cat.isAdult ||
                        cat.categoryName.contains("+18", ignoreCase = true) ||
                        cat.categoryName.contains("XXX", ignoreCase = true) ||
                        cat.categoryName.contains("adult", ignoreCase = true)
                CategoryItemUiModel(
                    id = cat.categoryId,
                    name = cat.categoryName,
                    itemCount = cat.itemCount,
                    isAdult = isAdult
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            emptyList()
        }
    }


    val movieCategoryItemModels = remember(vodCategories) {
        if (vodCategories.isNotEmpty()) {
            vodCategories.map { cat ->
                val isAdult = cat.isAdult ||
                        cat.categoryName.contains("+18", ignoreCase = true) ||
                        cat.categoryName.contains("XXX", ignoreCase = true) ||
                        cat.categoryName.contains("adult", ignoreCase = true)
                CategoryItemUiModel(
                    id = cat.categoryId,
                    name = cat.categoryName,
                    itemCount = cat.itemCount,
                    isAdult = isAdult
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            emptyList()
        }
    }

    val seriesCategoryItemModels = remember(seriesCategories) {
        if (seriesCategories.isNotEmpty()) {
            seriesCategories.map { cat ->
                val isAdult = cat.isAdult ||
                        cat.categoryName.contains("+18", ignoreCase = true) ||
                        cat.categoryName.contains("XXX", ignoreCase = true) ||
                        cat.categoryName.contains("adult", ignoreCase = true)
                CategoryItemUiModel(
                    id = cat.categoryId,
                    name = cat.categoryName,
                    itemCount = cat.itemCount,
                    isAdult = isAdult
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            emptyList()
        }
    }

    // Categorías y filtrado por categoría para Canales, Películas y Series (respetando categorías ocultas)
    val channelCategoryList = remember(liveCategoryItemModels, hiddenLiveCategories) {
        val visibleCats = liveCategoryItemModels
            .filter { it.id !in hiddenLiveCategories && it.name !in hiddenLiveCategories }
            .map { CategoryUiItem(id = it.id, name = it.name) }
        listOf(CategoryUiItem(id = "all", name = "Todos")) + visibleCats
    }
    var selectedChannelCategoryId by remember { mutableStateOf("all") }
    val activeChannelCatName = remember(channelCategoryList, selectedChannelCategoryId) {
        channelCategoryList.find { it.id == selectedChannelCategoryId }?.name ?: "Todos"
    }
    val liveChannelsPagingFlow = remember(
        channelRepository,
        activeSource?.id,
        hiddenLiveCategories,
        liveCatMap
    ) {
        val srcId = activeSource?.id.orEmpty()
        if (channelRepository != null && srcId.isNotBlank()) {
            snapshotFlow { selectedChannelCategoryId }
                .flatMapLatest { catId ->
                    channelRepository.getLiveChannelsPaging(
                        sourceId = srcId,
                        categoryId = catId.takeIf { it != "all" },
                        hiddenCategoryIds = hiddenLiveCategories.toList()
                    )
                }.map { pagingData ->
                    pagingData.map { channel ->
                        val resolvedCategoryName = liveCatMap[channel.categoryId]
                            ?: channel.categoryName.takeIf { it.isNotBlank() }
                            ?: "General"
                        val streamUrl = StreamUrlResolver.resolve(
                            directUrl = channel.streamUrl,
                            source = activeSource,
                            streamId = channel.streamId,
                            kind = StreamUrlResolver.Kind.LIVE,
                            extension = channel.containerExtension.ifBlank { "m3u8" }
                        )
                        ChannelUiModel(
                            id = channel.id,
                            streamId = channel.streamId,
                            name = channel.name,
                            num = channel.num,
                            categoryName = resolvedCategoryName,
                            categoryId = channel.categoryId,
                            streamIcon = channel.streamIcon,
                            currentProgram = channel.epgChannelId ?: "En Directo",
                            streamUrl = streamUrl,
                            isFavorite = channel.isFavorite,
                            sourceId = channel.sourceId
                        )
                    }
                }.cachedIn(coroutineScope)
        } else {
            emptyFlow()
        }
    }
    val pagedChannels = liveChannelsPagingFlow.collectAsLazyPagingItems()

    LaunchedEffect(pagedChannels.itemCount) {
        if (focusedChannel == null && pagedChannels.itemCount > 0) {
            val first = pagedChannels[0]
            if (first != null) {
                focusedChannel = first
                focusedChannelId = first.id
            }
        }
    }

    LaunchedEffect(hiddenLiveCategories) {
        if (selectedChannelCategoryId != "all" &&
            (selectedChannelCategoryId in hiddenLiveCategories || activeChannelCatName in hiddenLiveCategories)
        ) {
            selectedChannelCategoryId = "all"
        }
    }

    val movieCategoryList = remember(movieCategoryItemModels, hiddenMovieCategories) {
        val visibleCats = movieCategoryItemModels
            .filter { it.id !in hiddenMovieCategories && it.name !in hiddenMovieCategories }
            .map { CategoryUiItem(id = it.id, name = it.name) }
        listOf(CategoryUiItem(id = "all", name = "Todas")) + visibleCats
    }
    var selectedMovieCategoryId by remember { mutableStateOf("all") }
    val activeMovieCatName = remember(movieCategoryList, selectedMovieCategoryId) {
        movieCategoryList.find { it.id == selectedMovieCategoryId }?.name ?: "Todas"
    }
    val liveMoviesPagingFlow = remember(
        vodRepository,
        activeSource?.id,
        hiddenMovieCategories,
        vodCatMap
    ) {
        val srcId = activeSource?.id.orEmpty()
        if (vodRepository != null && srcId.isNotBlank()) {
            snapshotFlow { selectedMovieCategoryId }
                .flatMapLatest { catId ->
                    vodRepository.getMoviesPaging(
                        sourceId = srcId,
                        categoryId = catId.takeIf { it != "all" },
                        hiddenCategoryIds = hiddenMovieCategories.toList()
                    )
                }.map { pagingData ->
                    pagingData.map { movie ->
                        val resolvedCategoryName = vodCatMap[movie.categoryId]
                            ?: movie.categoryName.takeIf { it.isNotBlank() }
                            ?: "General"
                        val ext = movie.containerExtension.trimStart('.').ifEmpty { "mp4" }
                        val streamUrl = StreamUrlResolver.resolve(
                            directUrl = movie.streamUrl,
                            source = activeSource,
                            streamId = movie.streamId,
                            kind = StreamUrlResolver.Kind.VOD,
                            extension = ext
                        )
                        MovieUiModel(
                            id = movie.id,
                            streamId = movie.streamId,
                            name = movie.name,
                            categoryName = resolvedCategoryName,
                            categoryId = movie.categoryId,
                            streamIcon = movie.streamIcon,
                            streamUrl = streamUrl,
                            rating = movie.rating ?: 0.0,
                            year = movie.year,
                            containerExtension = ext,
                            isFavorite = movie.isFavorite,
                            backdropPath = movie.backdropPath,
                            plot = movie.plot,
                            genre = movie.genre
                        )
                    }
                }.cachedIn(coroutineScope)
        } else {
            emptyFlow()
        }
    }
    val pagedMovies = liveMoviesPagingFlow.collectAsLazyPagingItems()

    var focusedMovieId by remember { mutableStateOf<String?>(null) }
    var focusedMovie by remember { mutableStateOf<MovieUiModel?>(null) }

    LaunchedEffect(pagedMovies.itemCount) {
        if (focusedMovie == null && pagedMovies.itemCount > 0) {
            val first = pagedMovies[0]
            if (first != null) {
                focusedMovie = first
                focusedMovieId = first.id
            }
        }
    }
    LaunchedEffect(hiddenMovieCategories) {
        if (selectedMovieCategoryId != "all" &&
            (selectedMovieCategoryId in hiddenMovieCategories || activeMovieCatName in hiddenMovieCategories)
        ) {
            selectedMovieCategoryId = "all"
        }
    }

    val seriesCategoryList = remember(seriesCategoryItemModels, hiddenSeriesCategories) {
        val visibleCats = seriesCategoryItemModels
            .filter { it.id !in hiddenSeriesCategories && it.name !in hiddenSeriesCategories }
            .map { CategoryUiItem(id = it.id, name = it.name) }
        listOf(CategoryUiItem(id = "all", name = "Todas")) + visibleCats
    }
    var selectedSeriesCategoryId by remember { mutableStateOf("all") }
    val activeSeriesCatName = remember(seriesCategoryList, selectedSeriesCategoryId) {
        seriesCategoryList.find { it.id == selectedSeriesCategoryId }?.name ?: "Todas"
    }
    val liveSeriesPagingFlow = remember(
        seriesRepository,
        activeSource?.id,
        hiddenSeriesCategories,
        seriesCatMap
    ) {
        val srcId = activeSource?.id.orEmpty()
        if (seriesRepository != null && srcId.isNotBlank()) {
            snapshotFlow { selectedSeriesCategoryId }
                .flatMapLatest { catId ->
                    seriesRepository.getSeriesPaging(
                        sourceId = srcId,
                        categoryId = catId.takeIf { it != "all" },
                        hiddenCategoryIds = hiddenSeriesCategories.toList()
                    )
                }.map { pagingData ->
                    pagingData.map { series ->
                        val resolvedCategoryName = seriesCatMap[series.categoryId]
                            ?: series.categoryName.takeIf { it.isNotBlank() }
                            ?: "General"
                        SeriesUiModel(
                            id = series.id,
                            seriesId = series.seriesId,
                            name = series.name,
                            categoryName = resolvedCategoryName,
                            categoryId = series.categoryId,
                            cover = series.cover,
                            backdropPath = series.backdropPath,
                            rating = series.rating ?: 0.0,
                            releaseDate = series.releaseDate,
                            seasonsCount = 0,
                            plot = series.plot,
                            genre = series.genre,
                            isFavorite = series.isFavorite,
                            sourceId = series.sourceId
                        )
                    }
                }.cachedIn(coroutineScope)
        } else {
            emptyFlow()
        }
    }
    val pagedSeries = liveSeriesPagingFlow.collectAsLazyPagingItems()

    var focusedSeriesId by remember { mutableStateOf<String?>(null) }
    var focusedHeroSeriesModel by remember { mutableStateOf<SeriesUiModel?>(null) }

    LaunchedEffect(pagedSeries.itemCount) {
        if (focusedHeroSeriesModel == null && pagedSeries.itemCount > 0) {
            val first = pagedSeries[0]
            if (first != null) {
                focusedHeroSeriesModel = first
                focusedSeriesId = first.id
            }
        }
    }

    LaunchedEffect(hiddenSeriesCategories) {
        if (selectedSeriesCategoryId != "all" &&
            (selectedSeriesCategoryId in hiddenSeriesCategories || activeSeriesCatName in hiddenSeriesCategories)
        ) {
            selectedSeriesCategoryId = "all"
        }
    }

    // Lista de canales activa para la reproducción: cuando el usuario entra a ver canales
    // de una categoría específica (ej. Guatemala), el zapping y el D-Pad quedan aislados
    // exclusivamente a esa categoría. Se actualiza al hacer click en un canal de la cuadrícula.
    var activePlaybackChannels by remember { mutableStateOf<List<ChannelUiModel>>(emptyList()) }
    var activePlaybackChannelIndex by remember { mutableIntStateOf(0) }
    // Inicializar con visibleChannels por defecto
    LaunchedEffect(visibleChannels) {
        if (activePlaybackChannels.isEmpty() && visibleChannels.isNotEmpty()) {
            activePlaybackChannels = visibleChannels
        }
    }

    // FASE 33: Anticaídas de señal y Omisión Automática de canales en TV Box
    DisposableEffect(playerEngine, visibleChannels, activePlaybackChannels, activePlaybackChannelIndex, isPlayingLive) {
        playerEngine.onChannelUnavailable = {
            if (isPlayingLive) {
                val channels = if (activePlaybackChannels.isNotEmpty()) activePlaybackChannels else visibleChannels
                if (channels.isNotEmpty()) {
                    val nextIndex = if (activePlaybackChannelIndex in 0 until channels.size - 1) activePlaybackChannelIndex + 1 else 0
                    activePlaybackChannelIndex = nextIndex
                    val nextCh = channels[nextIndex]
                    focusedChannelIndex = visibleChannels.indexOfFirst { it.streamId == nextCh.streamId }.coerceAtLeast(0)
                    playerEngine.playStream(nextCh.streamUrl, isLive = true)
                    isHudVisible = true
                }
            }
        }
        onDispose {
            playerEngine.onChannelUnavailable = null
        }
    }

    val playerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            lastFullscreenEntryTime = System.currentTimeMillis()
            playerFocusRequester.requestFocus()
        }
    }

    fun playMovie(movie: VodMovie) {
        isPlayingLive = false
        currentPlayingTitle = movie.name
        val ext = movie.containerExtension.trimStart('.').ifEmpty { "mp4" }
        val movieUrl = StreamUrlResolver.resolve(
            directUrl = movie.streamUrl,
            source = activeSource,
            streamId = movie.streamId,
            kind = StreamUrlResolver.Kind.VOD,
            extension = ext
        )
        if (movieUrl.isNotBlank()) {
            playerEngine.playStream(movieUrl, isLive = false)
            isFullscreen = true
            activeDetailMedia = null
        }
    }

    fun playMovie(movie: MovieUiModel) {
        isPlayingLive = false
        currentPlayingTitle = movie.name
        val ext = movie.containerExtension.trimStart('.').ifEmpty { "mp4" }
        val movieUrl = StreamUrlResolver.resolve(
            directUrl = movie.streamUrl,
            source = activeSource,
            streamId = movie.streamId,
            kind = StreamUrlResolver.Kind.VOD,
            extension = ext
        )
        if (movieUrl.isNotBlank()) {
            playerEngine.playStream(movieUrl, isLive = false)
            isFullscreen = true
            activeDetailMedia = null
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

    fun openSeriesDetails(series: SeriesUiModel) {
        focusedSeriesId = series.id
        focusedHeroSeriesModel = series
        activeDetailMedia = MediaDetailUiModel(
            id = series.seriesId,
            title = series.name,
            posterUrl = series.cover,
            backdropUrl = series.backdropPath,
            rating = series.rating,
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
    val currentBackdropUrl = remember(selectedTopTab, focusedHeroMovie, focusedHeroSeries, focusedHeroSeriesModel, focusedChannel) {
        when (selectedTopTab) {
            3 -> focusedHeroMovie?.backdropPath ?: focusedHeroMovie?.streamIcon
            4 -> focusedHeroSeriesModel?.backdropPath ?: focusedHeroSeriesModel?.cover ?: focusedHeroSeries?.backdropPath ?: focusedHeroSeries?.cover
            else -> {
                if (focusedHeroMovie != null && selectedTopTab == 1) {
                    focusedHeroMovie?.backdropPath ?: focusedHeroMovie?.streamIcon
                } else if (focusedHeroSeriesModel != null && selectedTopTab == 1) {
                    focusedHeroSeriesModel?.backdropPath ?: focusedHeroSeriesModel?.cover
                } else if (focusedHeroSeries != null && selectedTopTab == 1) {
                    focusedHeroSeries?.backdropPath ?: focusedHeroSeries?.cover
                } else {
                    focusedChannel?.streamIcon
                }
            }
        }
    }

    // Live Background Zapping con Debounce de 500ms para no saturar el decodificador de hardware
    // al navegar rápido por la lista de canales (fix de rendimiento en TV Box).
    // Si el usuario se mueve rápido por 5 canales, solo sintoniza el último donde se detiene.
    LaunchedEffect(focusedChannel?.streamUrl, selectedTopTab, visibleChannels.isEmpty()) {
        if (visibleChannels.isEmpty()) {
            isPlayingLive = false
            playerEngine.pause()
        } else if (selectedTopTab == 1 || selectedTopTab == 2) {
            val streamUrl = focusedChannel?.streamUrl
            if (!streamUrl.isNullOrEmpty() && !isFullscreen) {
                delay(500L) // Debounce: esperar 500ms para no iniciar stream en cada canal de paso
                isPlayingLive = true
                playerEngine.playStream(streamUrl, isLive = true)
            }
        } else if (!isFullscreen) {
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

    // Manejo de tecla BACK: regresa a Inicio de forma segura en vez de salir de la app
    BackHandler(enabled = isFullscreen || isQuickZappingOpen || isHudVisible || selectedTopTab != 1) {
        when {
            isQuickZappingOpen -> isQuickZappingOpen = false
            isHudVisible -> isHudVisible = false
            isFullscreen -> isFullscreen = false
            selectedTopTab != 1 -> {
                selectedTopTab = 1
                try {
                    contentFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground)
    ) {
        // Capa de control D-pad para Pantalla Completa
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(playerFocusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val isLiveStream = isPlayingLive
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_CHANNEL_UP,
                            KeyEvent.KEYCODE_PAGE_UP -> {
                                if (isLiveStream && activePlaybackChannels.isNotEmpty()) {
                                    val nextIndex = if (activePlaybackChannelIndex < activePlaybackChannels.lastIndex) activePlaybackChannelIndex + 1 else 0
                                    activePlaybackChannelIndex = nextIndex
                                    val ch = activePlaybackChannels[nextIndex]
                                    // Actualizar focusedChannelIndex en la lista global para coherencia visual
                                    focusedChannelIndex = visibleChannels.indexOfFirst { it.streamId == ch.streamId }.coerceAtLeast(0)
                                    isPlayingLive = true
                                    playerEngine.playStream(ch.streamUrl, isLive = true)
                                    isHudVisible = true
                                    true
                                } else {
                                    isHudVisible = true
                                    true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_CHANNEL_DOWN,
                            KeyEvent.KEYCODE_PAGE_DOWN -> {
                                if (isLiveStream && activePlaybackChannels.isNotEmpty()) {
                                    val prevIndex = if (activePlaybackChannelIndex > 0) activePlaybackChannelIndex - 1 else activePlaybackChannels.lastIndex
                                    activePlaybackChannelIndex = prevIndex
                                    val ch = activePlaybackChannels[prevIndex]
                                    focusedChannelIndex = visibleChannels.indexOfFirst { it.streamId == ch.streamId }.coerceAtLeast(0)
                                    isPlayingLive = true
                                    playerEngine.playStream(ch.streamUrl, isLive = true)
                                    isHudVisible = true
                                    true
                                } else {
                                    isHudVisible = true
                                    true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                if (isLiveStream) {
                                    isQuickZappingOpen = !isQuickZappingOpen
                                    isHudVisible = true
                                } else {
                                    val isRewindKey = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                                    val offsetMs = if (isRewindKey) -30_000L else -10_000L
                                    playerEngine.seekBy(offsetMs)
                                    seekFeedbackText = if (isRewindKey) "⏪ -30s" else "⏪ -10s"
                                    isHudVisible = true
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                if (isLiveStream) {
                                    isHudVisible = !isHudVisible
                                } else {
                                    val isFfKey = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                                    val offsetMs = if (isFfKey) 30_000L else 10_000L
                                    playerEngine.seekBy(offsetMs)
                                    seekFeedbackText = if (isFfKey) "⏩ +30s" else "⏩ +10s"
                                    isHudVisible = true
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (System.currentTimeMillis() - lastFullscreenEntryTime < 600L) {
                                    true
                                } else {
                                    if (playbackState is PlaybackState.Playing) playerEngine.pause()
                                    else playerEngine.resume()
                                    true
                                }
                            }
                            KeyEvent.KEYCODE_PROG_YELLOW,
                            KeyEvent.KEYCODE_MENU,
                            KeyEvent.KEYCODE_INFO,
                            KeyEvent.KEYCODE_BUTTON_Y,
                            KeyEvent.KEYCODE_STAR -> {
                                // Tecla Amarilla / Menú / Info / Y / *: toggle Favorito del canal en reproducción actual
                                if (isLiveStream && focusedChannel != null) {
                                    val isFav = focusedChannel?.isFavorite == true
                                    onToggleFavoriteChannel(focusedChannel!!.streamId, !isFav)
                                    seekFeedbackText = if (!isFav) "⭐ Agregado a Favoritos" else "✖ Quitado de Favoritos"
                                }
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
            // Reproductor de video nativo para canales en vivo (Inicio y En Vivo) o cuando está en pantalla completa
            val canShowLivePlayer = (selectedTopTab == 1 || selectedTopTab == 2) && (pagedChannels.itemCount > 0 || visibleChannels.isNotEmpty())
            if (canShowLivePlayer || isFullscreen) {
                LelouchVideoPlayer(
                    playerEngine = playerEngine,
                    modifier = Modifier.fillMaxSize(),
                    resizeMode = if (isPlayingLive) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                )
            }

            // Portada Cinemática / Backdrop Artístico HD
            // Se muestra de fondo si estamos en VOD (Películas/Series) y NO en pantalla completa, o si el video en vivo está cargando/pausado
            val showBackdrop = !isFullscreen && (selectedTopTab != 1 && selectedTopTab != 2 || playbackState !is PlaybackState.Playing)
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

        // CAPA 3: Interfaz Principal Unificada a Pantalla Completa (Portal Dashboard EveryCine Style)
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedTopTab) {
                        0 -> {
                            // 🔍 BUSCADOR NATIVO FLUIDO (Sin teclado virtual bloqueante, compatible con voz y control remoto)
                            TvSearchContent(
                                activeSourceId = activeSource?.id,
                                channelRepository = channelRepository,
                                vodRepository = vodRepository,
                                seriesRepository = seriesRepository,
                                hiddenLiveCategories = hiddenLiveCategories,
                                hiddenMovieCategories = hiddenMovieCategories,
                                hiddenSeriesCategories = hiddenSeriesCategories,
                                popularMovies = visibleMovies.ifEmpty { recentMovies }.take(10),
                                onSelectChannel = { ch ->
                                    val idx = visibleChannels.indexOfFirst { it.streamId == ch.streamId }
                                    if (idx >= 0) focusedChannelIndex = idx
                                    selectedTopTab = 2
                                    isFullscreen = true
                                },
                                onSelectMovie = { mov ->
                                    activeDetailMedia = MediaDetailUiModel(
                                        id = mov.streamId,
                                        title = mov.name,
                                        posterUrl = mov.streamIcon,
                                        backdropUrl = mov.backdropPath,
                                        rating = mov.rating ?: 0.0,
                                        year = mov.year,
                                        synopsis = mov.plot ?: "Película en catálogo.",
                                        genre = mov.categoryName,
                                        containerExtension = mov.containerExtension.ifEmpty { "mp4" },
                                        streamUrl = mov.streamUrl,
                                        isSeries = false,
                                        isFavorite = mov.isFavorite
                                    )
                                },
                                onSelectSeries = { ser ->
                                    openSeriesDetails(ser)
                                },
                                searchFocusRequester = searchFocusRequester,
                                sidebarRequester = sidebarRequesters[0]
                            )
                        }

                        1 -> {
                            // 🏠 INICIO (EveryCine Style: Hero Spotlight + Canales + Películas + Series)
                            TvLazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
                            ) {
                                item {
                                    val sportsCount = remember(liveCategories) {
                                        liveCategories.filter { cat ->
                                            val name = cat.categoryName.lowercase()
                                            name.contains("deporte") || name.contains("sport") || name.contains("espn") || name.contains("fox sport") || name.contains("dazn")
                                        }.sumOf { it.itemCount }
                                    }
                                    val totalLiveChannelsCount = remember(liveCategories, visibleChannels) {
                                        val catCount = liveCategories.sumOf { it.itemCount }
                                        if (catCount > 0) catCount else visibleChannels.size
                                    }
                                    val totalMoviesCount = remember(vodCategories, visibleMovies) {
                                        val catCount = vodCategories.sumOf { it.itemCount }
                                        if (catCount > 0) catCount else visibleMovies.size
                                    }
                                    val totalSeriesCount = remember(seriesCategories, visibleSeries) {
                                        val catCount = seriesCategories.sumOf { it.itemCount }
                                        if (catCount > 0) catCount else visibleSeries.size
                                    }

                                    TvPortalDashboard(
                                        activeSource = activeSource,
                                        liveChannelsCount = totalLiveChannelsCount,
                                        moviesCount = totalMoviesCount,
                                        seriesCount = totalSeriesCount,
                                        sportsCount = sportsCount,
                                        onNavigateToLive = { selectedTopTab = 2 },
                                        onNavigateToMovies = { selectedTopTab = 3 },
                                        onNavigateToSeries = { selectedTopTab = 4 },
                                        onNavigateToSports = {
                                            val sportsCat = channelCategoryList.firstOrNull {
                                                it.name.contains("deporte", ignoreCase = true) || it.name.contains("sport", ignoreCase = true)
                                            }
                                            if (sportsCat != null) selectedChannelCategoryId = sportsCat.id
                                            selectedTopTab = 2
                                        },
                                        onDownloadM3U = {
                                            android.widget.Toast.makeText(context, "Lista M3U lista para exportar", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onReloadCatalog = { onForceSync() },
                                        onOpenSettings = { isAdminModalVisible = true },
                                        onOpenDiagnostics = { selectedTopTab = 6 },
                                        onNavigateToSearch = { selectedTopTab = 0 },
                                        onNavigateToFavorites = { selectedTopTab = 5 },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 24.dp)
                                    )
                                }

                                if (pagedChannels.itemCount > 0) {
                                    item {
                                        ContentSectionTitle("🔴 Canales en Directo")
                                        TvLazyRow(
                                            contentPadding = PaddingValues(horizontal = 32.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            val previewCount = pagedChannels.itemCount.coerceAtMost(30)
                                            items(
                                                count = previewCount,
                                                key = pagedChannels.itemKey { it.id },
                                                contentType = pagedChannels.itemContentType { "channel" }
                                            ) { idx ->
                                                val ch = pagedChannels[idx] ?: return@items
                                                val isSelected = (ch.id == focusedChannelId || (focusedChannelId == null && idx == 0))
                                                TvChannelCard(
                                                    channel = ch,
                                                    isSelected = isSelected,
                                                    onFocused = {
                                                        focusedChannelId = ch.id
                                                        focusedChannel = ch
                                                        focusedChannelIndex = idx
                                                        focusedHeroMovie = null
                                                    },
                                                    onClick = { 
                                                        focusedChannelId = ch.id
                                                        focusedChannel = ch
                                                        focusedChannelIndex = idx
                                                        isPlayingLive = true
                                                        playerEngine.playStream(ch.streamUrl, isLive = true)
                                                        isFullscreen = true 
                                                    },
                                                    onToggleFavorite = {
                                                        onToggleFavoriteChannel(ch.streamId, !ch.isFavorite)
                                                    },
                                                    isFavorite = ch.isFavorite,
                                                    cardWidth = 215.dp,
                                                    modifier = if (idx == 0) Modifier.focusProperties { left = sidebarRequesters[1] } else Modifier
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(28.dp))
                                    }
                                } else if (visibleChannels.isNotEmpty()) {
                                    item {
                                        ContentSectionTitle("🔴 Canales en Directo")
                                        TvLazyRow(
                                            contentPadding = PaddingValues(horizontal = 32.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            itemsIndexed(visibleChannels, key = { idx, ch -> "home_ch_${ch.streamId}_$idx" }) { idx, ch ->
                                                TvChannelCard(
                                                    channel = ch,
                                                    isSelected = (idx == focusedChannelIndex),
                                                    onFocused = {
                                                        focusedChannelIndex = idx
                                                        focusedHeroMovie = null
                                                    },
                                                    onClick = { 
                                                        focusedChannelIndex = idx
                                                        isPlayingLive = true
                                                        playerEngine.playStream(ch.streamUrl, isLive = true)
                                                        isFullscreen = true 
                                                    },
                                                    onToggleFavorite = {
                                                        onToggleFavoriteChannel(ch.streamId, !ch.isFavorite)
                                                    },
                                                    isFavorite = ch.isFavorite,
                                                    cardWidth = 215.dp,
                                                    modifier = if (idx == 0) Modifier.focusProperties { left = sidebarRequesters[1] } else Modifier
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(28.dp))
                                    }
                                }

                                val homeRailMovies = if (recentMovies.isNotEmpty()) recentMovies else visibleMovies
                                if (homeRailMovies.isNotEmpty()) {
                                    item {
                                        ContentSectionTitle("🎬 Películas Recientemente Añadidas")
                                        TvLazyRow(
                                            contentPadding = PaddingValues(horizontal = 32.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            itemsIndexed(homeRailMovies.take(20), key = { idx, mov -> "home_mov_${mov.streamId}_$idx" }) { idx, mov ->
                                                TvPosterCard(
                                                    title = mov.name,
                                                    posterUrl = mov.streamIcon,
                                                    rating = mov.rating ?: 0.0,
                                                    year = mov.year,
                                                    onFocused = { focusedHeroMovie = mov },
                                                    onClick = {
                                                        playMovie(mov)
                                                    },
                                                    modifier = if (idx == 0) Modifier.focusProperties { left = sidebarRequesters[1] } else Modifier
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(28.dp))
                                    }
                                }

                                if (visibleSeries.isNotEmpty()) {
                                    item {
                                        ContentSectionTitle("📺 Series Populares")
                                        TvLazyRow(
                                            contentPadding = PaddingValues(horizontal = 32.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            itemsIndexed(visibleSeries, key = { idx, ser -> "home_ser_${ser.seriesId}_$idx" }) { idx, ser ->
                                                TvPosterCard(
                                                    title = ser.name,
                                                    posterUrl = ser.cover,
                                                    rating = ser.rating ?: 0.0,
                                                    year = ser.releaseDate?.take(4),
                                                    onFocused = { focusedHeroSeries = ser },
                                                    onClick = { openSeriesDetails(ser) },
                                                    modifier = if (idx == 0) Modifier.focusProperties { left = sidebarRequesters[1] } else Modifier
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(28.dp))
                                    }
                                }
                            }
                        }

                        2 -> {
                            // 🔴 EN VIVO (GRID VERTICAL continuo sin carruseles molestos, bajando como Películas)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 32.dp, end = 32.dp, top = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "🔴 CANALES EN VIVO",
                                            color = LelouchCyanAccent,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                        val displayCount = remember(selectedChannelCategoryId, liveCategoryItemModels, liveCategories) {
                                            if (selectedChannelCategoryId == "all") {
                                                val total = liveCategories.sumOf { it.itemCount }
                                                if (total > 0) total else pagedChannels.itemCount
                                            } else {
                                                liveCategoryItemModels.find { it.id == selectedChannelCategoryId }?.itemCount ?: pagedChannels.itemCount
                                            }
                                        }
                                        Text(
                                            text = if (displayCount > 0) "$displayCount señales en directo • Categoría: $activeChannelCatName" else "${pagedChannels.itemCount} señales cargadas • Categoría: $activeChannelCatName",
                                            color = LelouchTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    HomeHeaderBar(
                                        activeSource = activeSource,
                                        videoInfo = videoInfo,
                                        playbackState = playbackState,
                                        onOpenAdmin = { selectedTopTab = 6 }
                                    )
                                }

                                TvCategorySelectorBar(
                                    categories = channelCategoryList,
                                    selectedCategoryId = selectedChannelCategoryId,
                                    onSelectCategory = { selectedChannelCategoryId = it.id },
                                    firstItemRequester = contentFocusRequester,
                                    sidebarRequester = sidebarRequesters[2],
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                if (pagedChannels.itemCount == 0 && pagedChannels.loadState.refresh !is androidx.paging.LoadState.Loading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 64.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            onClick = {
                                                categoryManagerInitialScope = CategoryScope.LIVE
                                                isCategoryManagerVisible = true
                                            },
                                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(18.dp)),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = LelouchSurfaceVariant.copy(alpha = 0.85f),
                                                focusedContainerColor = LelouchCyanAccent
                                            ),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "📺",
                                                    fontSize = 36.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "No hay canales en '$activeChannelCatName'",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Presiona OK para gestionar y activar categorías visibles",
                                                    color = LelouchTextSecondary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 205.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(bottom = 64.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            count = pagedChannels.itemCount,
                                            key = pagedChannels.itemKey { it.id },
                                            contentType = pagedChannels.itemContentType { "channel" }
                                        ) { index ->
                                            val ch = pagedChannels[index] ?: return@items
                                            val isSelected = (ch.id == focusedChannelId || (focusedChannelId == null && index == 0))
                                            val cardModifier = if (index == 0) {
                                                Modifier
                                                    .fillMaxWidth()
                                                    .focusProperties { left = sidebarRequesters[2] }
                                            } else {
                                                Modifier.fillMaxWidth()
                                            }

                                            TvChannelCard(
                                                channel = ch,
                                                isSelected = isSelected,
                                                onFocused = {
                                                    focusedChannelId = ch.id
                                                    focusedChannel = ch
                                                    focusedChannelIndex = index
                                                },
                                                onClick = {
                                                    focusedChannelId = ch.id
                                                    focusedChannel = ch
                                                    focusedChannelIndex = index
                                                    isPlayingLive = true
                                                    playerEngine.playStream(ch.streamUrl, isLive = true)
                                                    isFullscreen = true
                                                },
                                                onToggleFavorite = {
                                                    onToggleFavoriteChannel(ch.streamId, !ch.isFavorite)
                                                },
                                                isFavorite = ch.isFavorite,
                                                modifier = cardModifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // 🎬 PELÍCULAS EN GRID VERTICAL CON SELECTOR DE CATEGORÍAS
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 32.dp, end = 32.dp, top = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "🎬 CATÁLOGO DE PELÍCULAS",
                                            color = LelouchCyanAccent,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                        val activeMovieCatCount = remember(selectedMovieCategoryId, movieCategoryItemModels) {
                                            if (selectedMovieCategoryId == "all") {
                                                movieCategoryItemModels.sumOf { it.itemCount }
                                            } else {
                                                movieCategoryItemModels.find { it.id == selectedMovieCategoryId }?.itemCount ?: 0
                                            }
                                        }
                                        Text(
                                            text = "$activeMovieCatCount películas disponibles • Categoría: $activeMovieCatName",
                                            color = LelouchTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    HomeHeaderBar(
                                        activeSource = activeSource,
                                        videoInfo = videoInfo,
                                        playbackState = playbackState,
                                        onOpenAdmin = { selectedTopTab = 6 }
                                    )
                                }

                                TvCategorySelectorBar(
                                    categories = movieCategoryList,
                                    selectedCategoryId = selectedMovieCategoryId,
                                    onSelectCategory = { selectedMovieCategoryId = it.id },
                                    firstItemRequester = contentFocusRequester,
                                    sidebarRequester = sidebarRequesters[3],
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                if (pagedMovies.itemCount == 0 && pagedMovies.loadState.refresh !is androidx.paging.LoadState.Loading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            onClick = {
                                                categoryManagerInitialScope = CategoryScope.MOVIES
                                                isCategoryManagerVisible = true
                                            },
                                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(18.dp)),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = LelouchSurfaceVariant.copy(alpha = 0.85f),
                                                focusedContainerColor = LelouchCyanAccent
                                            ),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(text = "🎬", fontSize = 36.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "No hay películas en '$activeMovieCatName'",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Presiona OK para gestionar categorías de películas",
                                                    color = LelouchTextSecondary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 145.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp),
                                        contentPadding = PaddingValues(bottom = 48.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            count = pagedMovies.itemCount,
                                            key = pagedMovies.itemKey { it.id },
                                            contentType = pagedMovies.itemContentType { "movie" }
                                        ) { idx ->
                                            val movie = pagedMovies[idx] ?: return@items
                                            val cardModifier = if (idx == 0) {
                                                Modifier.focusProperties { left = sidebarRequesters[3] }
                                            } else {
                                                Modifier
                                            }

                                            TvPosterCard(
                                                title = movie.name,
                                                posterUrl = movie.streamIcon,
                                                rating = movie.rating,
                                                year = movie.year,
                                                onFocused = {
                                                    focusedMovie = movie
                                                    focusedMovieId = movie.id
                                                },
                                                onClick = {
                                                    playMovie(movie)
                                                },
                                                modifier = cardModifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            // 📺 SERIES EN GRID VERTICAL CON SELECTOR DE CATEGORÍAS
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 32.dp, end = 32.dp, top = 16.dp)
                            ) {
                                val activeSeriesCatCount = remember(selectedSeriesCategoryId, seriesCategoryItemModels, seriesCategories) {
                                    if (selectedSeriesCategoryId == "all") {
                                        seriesCategories.sumOf { it.itemCount }
                                    } else {
                                        seriesCategoryItemModels.find { it.id == selectedSeriesCategoryId }?.itemCount ?: 0
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "📺 SERIES & TEMPORADAS",
                                            color = LelouchCyanAccent,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "$activeSeriesCatCount series completas • Categoría: $activeSeriesCatName",
                                            color = LelouchTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    HomeHeaderBar(
                                        activeSource = activeSource,
                                        videoInfo = videoInfo,
                                        playbackState = playbackState,
                                        onOpenAdmin = { selectedTopTab = 6 }
                                    )
                                }

                                TvCategorySelectorBar(
                                    categories = seriesCategoryList,
                                    selectedCategoryId = selectedSeriesCategoryId,
                                    onSelectCategory = { selectedSeriesCategoryId = it.id },
                                    firstItemRequester = contentFocusRequester,
                                    sidebarRequester = sidebarRequesters[4],
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                if (pagedSeries.itemCount == 0 && pagedSeries.loadState.refresh !is androidx.paging.LoadState.Loading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            onClick = {
                                                categoryManagerInitialScope = CategoryScope.SERIES
                                                isCategoryManagerVisible = true
                                            },
                                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(18.dp)),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = LelouchSurfaceVariant.copy(alpha = 0.85f),
                                                focusedContainerColor = LelouchCyanAccent
                                            ),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(text = "📺", fontSize = 36.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = if (seriesCategoryList.size <= 1) "No hay series visibles activadas" else "No hay series en '$activeSeriesCatName'",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Presiona OK para gestionar categorías de series",
                                                    color = LelouchTextSecondary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 145.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp),
                                        contentPadding = PaddingValues(bottom = 48.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            count = pagedSeries.itemCount,
                                            key = pagedSeries.itemKey { it.id },
                                            contentType = pagedSeries.itemContentType { "series" }
                                        ) { idx ->
                                            val series = pagedSeries[idx] ?: return@items
                                            val cardModifier = if (idx == 0) {
                                                Modifier.focusProperties { left = sidebarRequesters[4] }
                                            } else {
                                                Modifier
                                            }

                                            TvPosterCard(
                                                title = series.name,
                                                posterUrl = series.cover,
                                                rating = series.rating,
                                                year = series.releaseDate?.take(4),
                                                onFocused = {
                                                    focusedHeroSeriesModel = series
                                                    focusedSeriesId = series.id
                                                },
                                                onClick = { openSeriesDetails(series) },
                                                modifier = cardModifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        5 -> {
                            // ⭐ FAVORITOS
                            TvLazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 48.dp)
                            ) {
                                item {
                                    Text(
                                        text = "⭐ TUS FAVORITOS",
                                        color = LelouchCyanAccent,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (favoriteChannels.isNotEmpty()) {
                                    item {
                                        ContentSectionTitle("⭐ Canales Favoritos")
                                        TvLazyRow(
                                            contentPadding = PaddingValues(horizontal = 0.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            itemsIndexed(favoriteChannels) { idx, stream ->
                                                val ch = ChannelUiModel(
                                                    streamId = stream.streamId,
                                                    name = stream.name,
                                                    num = stream.num,
                                                    categoryName = stream.categoryName ?: "Favoritos",
                                                    streamIcon = stream.streamIcon,
                                                    currentProgram = "Canal Favorito",
                                                    streamUrl = StreamUrlResolver.resolveLive(
                                                        stream.streamUrl, activeSource, stream.streamId
                                                    )
                                                )
                                                TvChannelCard(
                                                    channel = ch,
                                                    isSelected = false,
                                                    onFocused = {},
                                                    onClick = {
                                                        playerEngine.playStream(ch.streamUrl, isLive = true)
                                                        isFullscreen = true
                                                    },
                                                    onToggleFavorite = {
                                                        onToggleFavoriteChannel(ch.streamId, false)
                                                    },
                                                    isFavorite = true,
                                                    cardWidth = 215.dp,
                                                    modifier = if (idx == 0) Modifier.focusRequester(contentFocusRequester).focusProperties { left = sidebarRequesters[5] } else Modifier
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(28.dp))
                                    }
                                }

                                if (favoriteMovies.isNotEmpty()) {
                                    item {
                                        ContentSectionTitle("🎬 Películas Favoritas")
                                        TvLazyRow(
                                            contentPadding = PaddingValues(horizontal = 0.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            itemsIndexed(favoriteMovies) { idx, mov ->
                                                TvPosterCard(
                                                    title = mov.name,
                                                    posterUrl = mov.streamIcon,
                                                    rating = mov.rating ?: 0.0,
                                                    year = mov.year,
                                                    onClick = {
                                                        activeDetailMedia = MediaDetailUiModel(
                                                            id = mov.streamId,
                                                            title = mov.name,
                                                            posterUrl = mov.streamIcon,
                                                            backdropUrl = mov.backdropPath,
                                                            rating = mov.rating ?: 0.0,
                                                            year = mov.year,
                                                            synopsis = mov.plot ?: "",
                                                            genre = mov.categoryName,
                                                            containerExtension = mov.containerExtension.ifEmpty { "mp4" },
                                                            streamUrl = mov.streamUrl,
                                                            isSeries = false,
                                                            isFavorite = mov.isFavorite
                                                        )
                                                    },
                                                    modifier = if (idx == 0 && favoriteChannels.isEmpty()) Modifier.focusRequester(contentFocusRequester).focusProperties { left = sidebarRequesters[5] } else if (idx == 0) Modifier.focusProperties { left = sidebarRequesters[5] } else Modifier
                                                )
                                            }
                                        }
                                    }
                                }

                                if (favoriteChannels.isEmpty() && favoriteMovies.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
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
                                                text = "Marca canales o películas como favoritos para tenerlos siempre a mano.",
                                                color = LelouchTextSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        6 -> {
                            // ⚙️ PANEL ADMINISTRADOR & AJUSTES
                            TvLazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 32.dp, end = 48.dp, top = 16.dp, bottom = 48.dp)
                            ) {
                                item {
                                    Text(
                                        text = "⚙️ PANEL ADMINISTRADOR & AJUSTES",
                                        color = LelouchCyanAccent,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Gestiona tus listas IPTV, sincroniza con la nube o cambia de cuenta.",
                                        color = LelouchTextSecondary,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        ActionButton(
                                            icon = Icons.Default.CloudSync,
                                            label = "☁️ Sincronizar Supabase",
                                            modifier = Modifier
                                                .focusRequester(contentFocusRequester)
                                                .focusProperties { left = sidebarRequesters[6] },
                                            onClick = onSyncCloudSources
                                        )
                                        ActionButton(
                                            icon = Icons.Default.Refresh,
                                            label = "🔄 Re-sincronizar Catálogo",
                                            onClick = onForceSync
                                        )
                                        ActionButton(
                                            icon = Icons.Default.Logout,
                                            label = "🚪 Cerrar Sesión",
                                            isDanger = true,
                                            onClick = onLogout
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // 🩺 FASE 33: DIAGNÓSTICO DE REPRODUCCIÓN
                                    // Deja de adivinar: muestra la fuente activa, qué hay en Room
                                    // para ese sourceId, cuántos canales carecen de URL y qué URL
                                    // final recibiría el reproductor.
                                    TvDiagnosticsSection(
                                        activeSource = activeSource,
                                        liveCount = liveChannels.size,
                                        movieCount = movies.size,
                                        seriesCount = seriesCategories.sumOf { it.itemCount }.takeIf { it > 0 } ?: seriesList.size,
                                        blankUrlCount = liveChannels.count { it.streamUrl.isBlank() },
                                        resolvedUrl = focusedChannel?.let { ch ->
                                            StreamUrlResolver.resolveLive(ch.streamUrl, activeSource, ch.streamId)
                                        },
                                        focusedChannelName = focusedChannel?.name,
                                        playbackState = playbackState,
                                        syncStateText = syncStateText
                                    )

                                    // 🎯 GESTIÓN DE CATEGORÍAS VISIBLES (Exacto al diseño Web solicitado)
                                    TvCategoryVisibilitySection(
                                        liveTotal = liveCategoryItemModels.size,
                                        liveHiddenCount = liveCategoryItemModels.count { hiddenLiveCategories.contains(it.name) || hiddenLiveCategories.contains(it.id) },
                                        moviesTotal = movieCategoryItemModels.size,
                                        moviesHiddenCount = movieCategoryItemModels.count { hiddenMovieCategories.contains(it.name) || hiddenMovieCategories.contains(it.id) },
                                        seriesTotal = seriesCategoryItemModels.size,
                                        seriesHiddenCount = seriesCategoryItemModels.count { hiddenSeriesCategories.contains(it.name) || hiddenSeriesCategories.contains(it.id) },
                                        onOpenManager = { scope ->
                                            categoryManagerInitialScope = scope
                                            isCategoryManagerVisible = true
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(28.dp))
                                    Text(
                                        text = "📋 LISTAS IPTV DISPONIBLES (${allSources.size}):",
                                        color = LelouchTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                itemsIndexed(allSources) { _, source ->
                                    val isActive = (source.id == activeSource?.id || source.isActive)
                                    var isCardFocused by remember { mutableStateOf(false) }
                                    Surface(
                                        onClick = {
                                            if (!isActive) {
                                                onActivateSource(source.id)
                                                selectedTopTab = 1
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .focusProperties { left = sidebarRequesters[6] }
                                            .onFocusChanged { isCardFocused = it.isFocused },
                                        shape = ClickableSurfaceDefaults.shape(
                                            shape = RoundedCornerShape(12.dp),
                                            focusedShape = RoundedCornerShape(12.dp)
                                        ),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = if (isActive) LelouchSurfaceVariant else LelouchSurface,
                                            focusedContainerColor = LelouchCardFocused,
                                            pressedContainerColor = LelouchCardFocused
                                        ),
                                        border = ClickableSurfaceDefaults.border(
                                            border = Border(androidx.compose.foundation.BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) Color(0xFF10B981) else LelouchBorder)),
                                            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, LelouchCyanAccent))
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(source.name, color = if (isCardFocused) LelouchCyanAccent else LelouchTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    if (isActive) {
                                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF10B981)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                                            Text("🟢 ACTIVA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Servidor: ${source.serverUrl}  •  Usuario: ${source.username}", color = LelouchTextSecondary, fontSize = 12.sp)
                                            }

                                            if (!isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isCardFocused) Color.White else LelouchCyanAccent)
                                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text("⚡ ACTIVAR", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
                if (isPlayingLive && focusedChannel != null) {
                    val currentHudCh = focusedChannel!!
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
                                        text = "CH ${currentHudCh.num}  •  ${currentHudCh.categoryName}",
                                        color = LelouchCyanAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentHudCh.name,
                                    color = LelouchTextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = currentHudCh.currentProgram,
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
                                    text = "Siguiente: ${currentHudCh.nextProgram}",
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
                                items = visibleChannels,
                                key = { idx, channel -> "hud_ch_${channel.streamId}_$idx" }
                            ) { index, channel ->
                                val isSelected = (index == focusedChannelIndex)
                                var isCardFocused by remember { mutableStateOf(false) }

                                val hudCardInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .width(185.dp)
                                        .height(72.dp)
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
                                        .onKeyEvent { keyEvent ->
                                            val keyCode = keyEvent.nativeKeyEvent.keyCode
                                            val isCenterKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                              keyCode == KeyEvent.KEYCODE_ENTER ||
                                                              keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                                              keyCode == KeyEvent.KEYCODE_BUTTON_A

                                            if (isCenterKey) {
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                                                    focusedChannelIndex = index
                                                    isPlayingLive = true
                                                    playerEngine.playStream(channel.streamUrl, isLive = true)
                                                    true
                                                } else if (keyEvent.type == KeyEventType.KeyUp) {
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                        .clickable(
                                            interactionSource = hudCardInteractionSource,
                                            indication = null
                                        ) {
                                            focusedChannelIndex = index
                                            isPlayingLive = true
                                            playerEngine.playStream(channel.streamUrl, isLive = true)
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
                } else {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusBadgeText = when (playbackState) {
                                        is PlaybackState.Playing -> "REPRODUCIENDO"
                                        is PlaybackState.Buffering -> "CARGANDO..."
                                        is PlaybackState.Error -> "ERROR"
                                        is PlaybackState.Paused -> "EN PAUSA"
                                        else -> "DETENIDO"
                                    }
                                    val statusBadgeBg = when (playbackState) {
                                        is PlaybackState.Playing -> LelouchCyanAccent
                                        is PlaybackState.Buffering -> Color(0xFFFFB300)
                                        is PlaybackState.Error -> Color(0xFFFF5252)
                                        else -> Color(0xFF888888)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusBadgeBg)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = statusBadgeText,
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "VOD 4K UHD",
                                        color = LelouchTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentPlayingTitle ?: "Reproducción VOD",
                                    color = LelouchTextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = videoInfo.resolutionLabel.ifEmpty { "1080p FHD" },
                                    color = LelouchCyanAccent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val posMs = (playbackState as? PlaybackState.Playing)?.positionMs
                                    ?: (playbackState as? PlaybackState.Paused)?.positionMs ?: 0L
                                val durMs = (playbackState as? PlaybackState.Playing)?.durationMs
                                    ?: (playbackState as? PlaybackState.Paused)?.durationMs ?: 0L
                                val posMin = (posMs / 1000) / 60
                                val posSec = (posMs / 1000) % 60
                                val durMin = (durMs / 1000) / 60
                                val durSec = (durMs / 1000) % 60
                                Text(
                                    text = String.format("%02d:%02d / %02d:%02d", posMin, posSec, durMin, durSec),
                                    color = LelouchTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val posMs = (playbackState as? PlaybackState.Playing)?.positionMs
                            ?: (playbackState as? PlaybackState.Paused)?.positionMs ?: 0L
                        val durMs = (playbackState as? PlaybackState.Playing)?.durationMs
                            ?: (playbackState as? PlaybackState.Paused)?.durationMs ?: 0L
                        val progress = if (durMs > 0L) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(LelouchSurfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(LelouchCyanAccent)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "D-Pad ◄ / ► : -10s / +10s  •  OK : Pausa / Reanudar  •  BACK : Salir al menú",
                            color = LelouchTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
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
                    val idx = visibleChannels.indexOfFirst { it.streamId == channel.streamId }
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
                        containerExtension = movie.containerExtension.ifEmpty { "mp4" },
                        streamUrl = movie.streamUrl,
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
                    val idx = visibleChannels.indexOfFirst { it.streamId == channel.streamId }
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
                onCheckUpdates = { isUpdateModalVisible = true },
                onDismiss = { isAdminModalVisible = false }
            )
        }

        // CAPA 8.2: Modal de Actualización Over-The-Air (OTA)
        if (isUpdateModalVisible) {
            TvUpdateModal(
                onDismiss = { isUpdateModalVisible = false }
            )
        }

        // CAPA 8.5: Modal de Gestión de Categorías Visibles
        if (isCategoryManagerVisible) {
            TvCategoryManagerModal(
                initialScope = categoryManagerInitialScope,
                liveCategories = liveCategoryItemModels,
                hiddenLiveCategoryNames = hiddenLiveCategories,
                movieCategories = movieCategoryItemModels,
                hiddenMovieCategoryNames = hiddenMovieCategories,
                seriesCategories = seriesCategoryItemModels,
                hiddenSeriesCategoryNames = hiddenSeriesCategories,
                onToggleLiveCategory = { cat, isVis ->
                    val next = if (isVis) {
                        hiddenLiveCategories - cat.id - cat.name
                    } else {
                        hiddenLiveCategories + cat.id + cat.name
                    }
                    saveHiddenLive(next)
                },
                onToggleMovieCategory = { cat, isVis ->
                    val next = if (isVis) {
                        hiddenMovieCategories - cat.id - cat.name
                    } else {
                        hiddenMovieCategories + cat.id + cat.name
                    }
                    saveHiddenMovies(next)
                },
                onToggleSeriesCategory = { cat, isVis ->
                    val next = if (isVis) {
                        hiddenSeriesCategories - cat.id - cat.name
                    } else {
                        hiddenSeriesCategories + cat.id + cat.name
                    }
                    saveHiddenSeries(next)
                },
                onShowAllLive = { saveHiddenLive(emptySet()) },
                onHideAllLive = { saveHiddenLive(liveCategoryItemModels.flatMap { listOf(it.id, it.name) }.toSet()) },
                onShowAllMovies = { saveHiddenMovies(emptySet()) },
                onHideAllMovies = { saveHiddenMovies(movieCategoryItemModels.flatMap { listOf(it.id, it.name) }.toSet()) },
                onShowAllSeries = { saveHiddenSeries(emptySet()) },
                onHideAllSeries = { saveHiddenSeries(seriesCategoryItemModels.flatMap { listOf(it.id, it.name) }.toSet()) },
                onDismiss = { isCategoryManagerVisible = false }
            )
        }

        // CAPA 9: Modal de Detalle de Película / Serie y Selector de Episodios
        if (activeDetailMedia != null) {
            TvMediaDetailModal(
                media = activeDetailMedia!!,
                onPlayClick = { episodeId ->
                    val media = activeDetailMedia ?: return@TvMediaDetailModal
                    isPlayingLive = false
                    if (media.isSeries) {
                        val ep = (if (episodeId != null) media.episodes.find { it.episodeId == episodeId } else null)
                            ?: media.episodes.firstOrNull()
                        val sTitle = media.title
                        val epTitle = ep?.title ?: "Episodio 1"
                        currentPlayingTitle = "$sTitle: $epTitle"
                        if (ep != null && ep.streamUrl.isNotEmpty()) {
                            playerEngine.playStream(ep.streamUrl, isLive = false)
                        } else {
                            // FASE 33: el resolvedor decide; en fuentes M3U devuelve "" en vez
                            // de una URL de Vercel que devolvería 404.
                            val targetId = ep?.episodeId ?: episodeId ?: media.id
                            val ext = ep?.containerExtension?.ifEmpty { "mp4" } ?: "mp4"
                            val fallbackUrl = StreamUrlResolver.resolve(
                                directUrl = null,
                                source = activeSource,
                                streamId = targetId,
                                kind = StreamUrlResolver.Kind.SERIES,
                                extension = ext
                            )
                            playerEngine.playStream(fallbackUrl, isLive = false)
                        }
                    } else {
                        currentPlayingTitle = media.title
                        val ext = media.containerExtension.ifEmpty { "mp4" }
                        val movieUrl = StreamUrlResolver.resolve(
                            directUrl = media.streamUrl,
                            source = activeSource,
                            streamId = media.id,
                            kind = StreamUrlResolver.Kind.VOD,
                            extension = ext
                        )
                        if (movieUrl.isNotBlank()) {
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
                        itemsIndexed(activePlaybackChannels.ifEmpty { visibleChannels }) { idx, ch ->
                            // El Zapping Drawer usa activePlaybackChannels (la categoria activa),
                            // no visibleChannels global. Asi si estas en Guatemala, solo ves Guatemala.
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
                                        activePlaybackChannelIndex = idx
                                        focusedChannelIndex = visibleChannels.indexOfFirst { it.streamId == ch.streamId }.coerceAtLeast(0)
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

        // HUD de depuración deshabilitado para mantener la pantalla de la TV limpia y despejada
        val showDebugHud = false
        if (showDebugHud && !isFullscreen) {
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
 * Soporta selección instantánea con botón OK / Centro del control Xiaomi.
 * Favorito: cuando está enfocada, muestra estrella ⭐ para agregar/quitar.
 */
@Composable
fun TvChannelCard(
    channel: ChannelUiModel,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    // Una sola animación via TvMaterial3 (eliminada la doble animación redundante que sobrecargaba GPU del TV Box)

    val widthModifier = if (cardWidth != androidx.compose.ui.unit.Dp.Unspecified) {
        Modifier.width(cardWidth)
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        onClick = onClick,
        onLongClick = onToggleFavorite,
        modifier = modifier
            .then(widthModifier)
            .height(125.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MENU,
                        android.view.KeyEvent.KEYCODE_INFO,
                        android.view.KeyEvent.KEYCODE_PROG_YELLOW,
                        android.view.KeyEvent.KEYCODE_BUTTON_Y,
                        android.view.KeyEvent.KEYCODE_STAR -> {
                            if (onToggleFavorite != null) {
                                onToggleFavorite()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp),
            focusedShape = RoundedCornerShape(12.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) LelouchSurfaceVariant else LelouchSurface,
            focusedContainerColor = LelouchCardFocused,
            pressedContainerColor = LelouchCardFocused
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) LelouchCyanAccent.copy(alpha = 0.5f) else LelouchBorder)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, if (isFavorite) Color(0xFFFFD700) else LelouchCyanAccent))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
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
                        // Tamaño fijo 124x76px: evita cargar imágenes gigantes en RAM del TV Box
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(channel.streamIcon)
                                .size(124, 76)
                                .crossfade(true)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build(),
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

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Badge / Indicador de Favorito para TV Box y Móvil
                    if (isFavorite) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFD700).copy(alpha = 0.25f))
                                .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(6.dp))
                                .clickable(enabled = onToggleFavorite != null) { onToggleFavorite?.invoke() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isFocused) "⭐ FAV (OK largo)" else "⭐",
                                color = Color(0xFFFFD700),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isFocused && onToggleFavorite != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .clickable { onToggleFavorite.invoke() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "☆ +FAV",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
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

// ══════════════════════════════════════════════════════════════════════
// 🩺 FASE 33 — PANEL DE DIAGNÓSTICO DE REPRODUCCIÓN
// Muestra la cadena completa: fuente activa -> ítems en Room -> URL final
// -> estado de ExoPlayer. Pensado para dejar de adivinar por qué no suena.
// ══════════════════════════════════════════════════════════════════════

/** Oculta credenciales de rutas Xtream (`/live/USER/PASS/`) y parámetros sensibles. */
private fun redactUrl(url: String?): String {
    if (url.isNullOrBlank()) return "— (vacía)"
    var out = url
    out = Regex("/(live|movie|series)/[^/]+/[^/]+/")
        .replace(out) { "/${it.groupValues[1]}/***/***/" }
    out = Regex("([?&](?:password|pass|pwd|username)=)[^&]+")
        .replace(out) { "${it.groupValues[1]}***" }
    out = Regex("([?&]token=)([a-zA-Z0-9_-]{8})[a-zA-Z0-9_-]*")
        .replace(out) { "${it.groupValues[1]}${it.groupValues[2]}…" }
    return out
}

@Composable
private fun TvDiagnosticsSection(
    activeSource: SourceConfig?,
    liveCount: Int,
    movieCount: Int,
    seriesCount: Int,
    blankUrlCount: Int,
    resolvedUrl: String?,
    focusedChannelName: String?,
    playbackState: PlaybackState,
    syncStateText: String
) {
    val stateLabel = when (val s = playbackState) {
        is PlaybackState.Idle -> "En reposo"
        is PlaybackState.Buffering -> "⏳ Cargando búfer…"
        is PlaybackState.Playing -> "▶ Reproduciendo"
        is PlaybackState.Paused -> "⏸ Pausado"
        is PlaybackState.Ended -> "Fin del stream"
        is PlaybackState.Error -> "✖ ${s.errorMessage} (código ${s.errorCode})"
    }

    val isBroken = resolvedUrl.isNullOrBlank() ||
        resolvedUrl.contains("vercel.app", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LelouchSurfaceVariant, RoundedCornerShape(14.dp))
            .border(1.dp, LelouchBorder, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "🩺 DIAGNÓSTICO DE REPRODUCCIÓN",
            color = LelouchCyanAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        DiagnosticRow("Fuente activa", activeSource?.name ?: "— ninguna —")
        DiagnosticRow("ID de fuente (Room)", activeSource?.id ?: "—")
        DiagnosticRow("Tipo", activeSource?.type?.name ?: "—")
        DiagnosticRow("Servidor", redactUrl(activeSource?.serverUrl))
        DiagnosticRow(
            "Usuario",
            activeSource?.username?.takeIf { it.isNotBlank() } ?: "(vacío — es lista por token)"
        )

        Spacer(modifier = Modifier.height(10.dp))
        DiagnosticRow("Canales en Room", liveCount.toString())
        DiagnosticRow("Películas en Room", movieCount.toString())
        DiagnosticRow("Series en Room", seriesCount.toString())
        DiagnosticRow("Canales SIN URL", blankUrlCount.toString())

        Spacer(modifier = Modifier.height(10.dp))
        DiagnosticRow("Canal enfocado", focusedChannelName ?: "—")
        DiagnosticRow("Última sincronización", syncStateText.ifBlank { "— sin datos —" })
        DiagnosticRow("Estado del reproductor", stateLabel)

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "URL que recibiría el reproductor",
            color = LelouchTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = redactUrl(resolvedUrl),
            color = if (isBroken) LelouchError else LelouchSuccess,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        if (isBroken) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "⚠️ La URL está vacía o apunta a Vercel, que no transmite vídeo. " +
                    "Pulsa «🔄 Re-sincronizar Catálogo»; si persiste, la lista no tiene " +
                    "streamUrl guardado para este elemento.",
                color = LelouchWarning,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        } else if (liveCount == 0 && movieCount == 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "⚠️ Room está vacío para este sourceId. La app debería re-sincronizar " +
                    "al arrancar; si no lo hace, usa «🔄 Re-sincronizar Catálogo».",
                color = LelouchWarning,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = LelouchTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = LelouchTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f)
        )
    }
}
