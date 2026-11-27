package com.lelouch.feature.mobile

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import com.lelouch.core.network.StreamUrlResolver
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.ContinueWatchingItem
import com.lelouch.core.model.CuratedMediaItem
import com.lelouch.core.model.CuratedSectionType
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.PortalFeedCalculator
import com.lelouch.core.model.TmdbHeroMatch
import com.lelouch.core.model.PortalFeedData
import com.lelouch.core.model.Series
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.VodMovie
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * FASE 35/36: Tipos de sección curada para navegación dedicada (patrón TopicNavKey de Google).
 */
enum class CuratedSectionType(
    val title: String,
    val barColorHex: Long,
    val isMoviesOnly: Boolean = false
) {
    RECENT("RECIÉN AGREGADAS", 0xFF00E5C9, isMoviesOnly = true),
    TOP_RATED("MEJOR VALORADAS", 0xFFFFB800),
    HORROR("TERROR Y SUSPENSO", 0xFFFF5252),
    SCIFI("CIENCIA FICCIÓN", 0xFF00E5C9),
    TRUE_EVENTS("BASADO EN HECHOS REALES", 0xFFE5A93C),
    ACTION("ACCIÓN Y AVENTURA", 0xFFFF6D00),
    ANIMATION("MUNDO ANIMADO", 0xFFFF4081)
}

/**
 * FASE 35: Modelos de datos para el feed curado y desacoplado del Portal Dashboard.
 * Permite calcular las secciones en segundo plano (Dispatchers.Default) sin bloquear el Main Thread
 * y conservarlas instantáneamente al navegar entre pestañas.
 */
data class CuratedMediaItem(
    val title: String,
    val imageUrl: String?,
    val rating: Double?,
    val year: String?,
    val movie: VodMovie? = null,
    val series: Series? = null
)

data class PortalFeedData(
    val sportsCount: Int = 0,
    val topTenMovies: List<VodMovie> = emptyList(),
    val liveMatches: List<LiveStream> = emptyList(),
    val recentMovies: List<VodMovie> = emptyList(),
    val mostViewedItems: List<CuratedMediaItem> = emptyList(),
    val terrorItems: List<CuratedMediaItem> = emptyList(),
    val sciFiItems: List<CuratedMediaItem> = emptyList(),
    val realHistoryItems: List<CuratedMediaItem> = emptyList(),
    val actionItems: List<CuratedMediaItem> = emptyList(),
    val animItems: List<CuratedMediaItem> = emptyList()
)

object PortalFeedCalculator {

    fun isAnimation(name: String, category: String, genre: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = category.lowercase()
        val n = name.lowercase()
        return g.contains("animaci") || g.contains("animation") || g.contains("anime") || g.contains("manga") ||
               c.contains("animaci") || c.contains("anime") || c.contains("caricatura") || c.contains("cartoon") ||
               n.contains("anime") || n.contains("dragon ball") || n.contains("naruto") || n.contains("one piece") || n.contains("demon slayer")
    }

    private val terrorKeywords = listOf("terror", "horror", "miedo", "suspenso", "thriller", "psicologico", "paranormal", "pesadilla", "gore", "fantasma", "demonio", "siniestro", "misterio", "slasher")
    private val terrorExcludes = listOf("animaci", "anime", "infantil", "kids", "disney", "cartoon", "comedia")
    fun isTerror(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasGenreMatch = terrorKeywords.any { g.contains(it) || c.contains(it) }
        val hasPlotMatch = g.isBlank() && terrorKeywords.any { p.contains(it) || n.contains(it) }
        val isExcluded = terrorExcludes.any { g.contains(it) || c.contains(it) }
        return (hasGenreMatch || hasPlotMatch) && !isExcluded
    }

    private val sciFiKeywords = listOf("ciencia ficci", "sci-fi", "scifi", "espacio", "galax", "extraterrestre", "alien", "cyber", "robot", "apocalip", "distop", "mutante", "universo", "interestelar", "matrix", "star wars", "star trek", "marte")
    private val sciFiExcludes = listOf("animaci", "anime", "infantil", "kids", "disney")
    fun isSciFi(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasGenreMatch = sciFiKeywords.any { g.contains(it) || c.contains(it) }
        val hasPlotMatch = g.isBlank() && sciFiKeywords.any { p.contains(it) || n.contains(it) }
        val isExcluded = sciFiExcludes.any { g.contains(it) || c.contains(it) }
        return (hasGenreMatch || hasPlotMatch) && !isExcluded
    }

    private val realKeywords = listOf("hechos reales", "historia real", "true story", "biograf", "biopic", "documental", "vida real", "veridica", "crimen real", "testimonio")
    private val realExcludes = listOf("novela", "libro", "comic", "superhero", "marvel", "dc comics", "fantasia", "sci-fi", "animaci", "anime")
    fun isRealHistory(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasExactMatch = realKeywords.any { n.contains(it) || g.contains(it) || c.contains(it) } ||
                            p.contains("basada en hechos reales") || p.contains("basado en hechos reales") ||
                            p.contains("basada en la historia real") || p.contains("basado en la historia real")
        val isExcluded = realExcludes.any { g.contains(it) || c.contains(it) } || p.contains("basada en la novela") || p.contains("basado en el libro")
        return hasExactMatch && !isExcluded
    }

    private val actionKeywords = listOf("acci", "action", "aventura", "adventure", "belic", "guerra", "marcial", "policiac", "espia", "mision", "combate", "venganza", "fast & furious", "john wick")
    private val actionExcludes = listOf("animaci", "anime", "infantil", "kids", "disney", "terror", "horror")
    fun isAction(name: String, cat: String, genre: String?, plot: String?): Boolean {
        val g = (genre ?: "").lowercase()
        val c = cat.lowercase()
        val p = (plot ?: "").lowercase()
        val n = name.lowercase()
        val hasGenreMatch = actionKeywords.any { g.contains(it) || c.contains(it) }
        val hasPlotMatch = g.isBlank() && actionKeywords.any { p.contains(it) || n.contains(it) }
        val isExcluded = actionExcludes.any { g.contains(it) || c.contains(it) }
        return (hasGenreMatch || hasPlotMatch) && !isExcluded
    }

    fun calculate(
        liveChannels: List<LiveStream>,
        movies: List<VodMovie>,
        seriesList: List<Series>
    ): PortalFeedData {
        val sportsKeywords = listOf(
            "sport", "deporte", "futbol", "fútbol", "liga", "champions", "premier",
            "laliga", "nba", "mlb", "nfl", "ufc", "box", "f1", "formula 1",
            "motogp", "espn", "fox sport", "dazn", "tudn", "directv sport",
            "win sport", "gol", "partido", "event", "cricket", "beisbol", "tenis", "tennis"
        )
        val sportsCount = liveChannels.count { ch ->
            val cat = ch.categoryName.lowercase()
            val name = ch.name.lowercase()
            sportsKeywords.any { cat.contains(it) || name.contains(it) }
        }

        val liveMatches = liveChannels.asSequence().filter { ch ->
            if (ch.isAdult || com.lelouch.core.model.AdultContentFilter.isAdult(name = ch.name, categoryName = ch.categoryName, streamIcon = ch.streamIcon, streamUrl = ch.streamUrl)) return@filter false
            val cat = ch.categoryName.lowercase()
            val name = ch.name.lowercase()
            val isSportCat = sportsKeywords.any { cat.contains(it) || name.contains(it) }
            val hasVs = name.contains(" vs ") || name.contains(" vs. ") || name.contains(" v ")
            isSportCat || hasVs
        }.take(20).toList()

        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentYearStr = currentYear.toString()
        val previousYearStr = (currentYear - 1).toString()

        fun isMovieFromYear(movie: VodMovie, year: String): Boolean {
            return movie.year?.contains(year) == true ||
                   movie.added?.startsWith(year) == true ||
                   movie.name.contains(year)
        }

        val yearMovies = movies.asSequence().filter { m ->
            (isMovieFromYear(m, currentYearStr) || isMovieFromYear(m, previousYearStr)) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.sortedByDescending { it.rating ?: 0.0 }.take(10).toList()

        val topTenMovies = if (yearMovies.isNotEmpty()) yearMovies else movies.take(10)

        // 1. RECIÉN AGREGADAS: Ordenadas por fecha de añadido (added) desc, excluyendo anime y adultos
        val recentMovies = movies.asSequence().filter { m ->
            !isAnimation(m.name, m.categoryName, m.genre) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.sortedWith(
            compareByDescending<VodMovie> { m -> m.added?.toLongOrNull() ?: 0L }.thenByDescending { it.streamId }
        ).take(15).toList()

        // 2. MEJOR VALORADAS: Películas y series con rating >= 6.5 ordenadas globalmente por nota, balanceadas
        val highMovies = movies.asSequence().filter { m ->
            !isAnimation(m.name, m.categoryName, m.genre) &&
            (m.rating ?: 0.0) >= 6.5 &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.map {
            CuratedMediaItem(
                title = it.name,
                imageUrl = it.streamIcon,
                rating = it.rating,
                year = it.year,
                movie = it
            )
        }.toList().sortedByDescending { it.rating ?: 0.0 }

        val highSeries = seriesList.asSequence().filter { s ->
            !isAnimation(s.name, s.categoryName, s.genre) &&
            ((s.rating5based?.times(2.0)) ?: (s.rating ?: 0.0)) >= 6.5 &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.map {
            CuratedMediaItem(
                title = it.name,
                imageUrl = it.cover,
                rating = it.rating5based?.times(2.0) ?: (it.rating ?: 0.0),
                year = it.releaseDate?.take(4),
                series = it
            )
        }.toList().sortedByDescending { it.rating ?: 0.0 }

        val mostViewedItems = (highMovies.take(8) + highSeries.take(7)).sortedByDescending { it.rating ?: 0.0 }

        // 3. TERROR Y SUSPENSO: Prioridad de género oficial
        val terrorMovies = movies.asSequence().filter { m ->
            isTerror(m.name, m.categoryName, m.genre, m.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val terrorSeries = seriesList.asSequence().filter { s ->
            isTerror(s.name, s.categoryName, s.genre, s.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val terrorItems = (terrorMovies.take(8) + terrorSeries.take(7)).take(15)

        // 4. CIENCIA FICCIÓN: Prioridad de género oficial
        val sfMovies = movies.asSequence().filter { m ->
            isSciFi(m.name, m.categoryName, m.genre, m.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val sfSeries = seriesList.asSequence().filter { s ->
            isSciFi(s.name, s.categoryName, s.genre, s.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val sciFiItems = (sfMovies.take(8) + sfSeries.take(7)).take(15)

        // 5. BASADO EN HECHOS REALES: Términos reales excluyendo novelas/libros
        val realMovies = movies.asSequence().filter { m ->
            isRealHistory(m.name, m.categoryName, m.genre, m.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val realSeries = seriesList.asSequence().filter { s ->
            isRealHistory(s.name, s.categoryName, s.genre, s.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val realHistoryItems = (realMovies.take(8) + realSeries.take(7)).take(15)

        // 6. ACCIÓN Y AVENTURA: Prioridad de género oficial
        val actMovies = movies.asSequence().filter { m ->
            isAction(m.name, m.categoryName, m.genre, m.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val actSeries = seriesList.asSequence().filter { s ->
            isAction(s.name, s.categoryName, s.genre, s.plot) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val actionItems = (actMovies.take(8) + actSeries.take(7)).take(15)

        // 7. MUNDO ANIMADO: Exclusivo género de animación / anime
        val animMovies = movies.asSequence().filter { m ->
            isAnimation(m.name, m.categoryName, m.genre) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = m.name, categoryName = m.categoryName, categoryId = m.categoryId, streamIcon = m.streamIcon, streamUrl = m.streamUrl)
        }.take(10).map { CuratedMediaItem(it.name, it.streamIcon, it.rating, it.year, movie = it) }.toList()

        val animSeries = seriesList.asSequence().filter { s ->
            isAnimation(s.name, s.categoryName, s.genre) &&
            !com.lelouch.core.model.AdultContentFilter.isAdult(name = s.name, categoryName = s.categoryName, categoryId = s.categoryId, streamIcon = s.cover)
        }.take(10).map { CuratedMediaItem(it.name, it.cover, it.rating5based?.times(2.0) ?: it.rating, it.releaseDate?.take(4), series = it) }.toList()

        val animItems = (animMovies.take(8) + animSeries.take(7)).take(15)

        return PortalFeedData(
            sportsCount = sportsCount,
            topTenMovies = topTenMovies,
            liveMatches = liveMatches,
            recentMovies = recentMovies,
            mostViewedItems = mostViewedItems,
            terrorItems = terrorItems,
            sciFiItems = sciFiItems,
            realHistoryItems = realHistoryItems,
            actionItems = actionItems,
            animItems = animItems
        )
    }
}

/**
 * Feed de Inicio Móvil Fiel a las Capturas de Referencia:
 * 1. Cabecera superior con Logo LELOUCH PLUS y acceso directo a TV En Vivo.
 * 2. Barra de búsqueda integrada tipo píldora ovalada.
 * 3. Sección EXPLORAR con píldoras horizontales [ 🎬 PELÍCULAS ] [ 🎞️ SERIES ] [ 📺 EN VIVO ].
 * 4. CARRUSEL HERO TOP 10 (Captura 1):
 *    - Píldora Cyan `TOP 10 • 0X`.
 *    - Título blanco en negrita grande.
 *    - Subtítulo `Película • Año • ★ Rating`.
 *    - Sinopsis recortada a 2 líneas.
 *    - Botones de acción: [ ▶ Descubrir ] Cyan y [ Explorar → ] Delineado.
 *    - Indicador de página `05 / 10` y barras dash interactivas.
 * 5. CARRILES HORIZONTALES CURADOS (Captura 2):
 *    - "Mejores películas de la semana" con `VER TODO →` (cyan).
 *    - "Mejores series de la semana" con `VER TODO →` (cyan).
 *    - "Anime y Animación" con `VER TODO →` (cyan).
 *    - "En Vivo & Deportes" con `VER TODO →`.
 */
@Composable
fun MobilePortalDashboard(
    activeSource: SourceConfig?,
    liveChannelsCount: Int,
    moviesCount: Int,
    seriesCount: Int,
    sportsCount: Int,
    onNavigateToLive: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToSports: () -> Unit,
    onDownloadM3U: () -> Unit,
    onReloadCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountSwitcher: () -> Unit = onOpenSettings,
    onSearchClick: () -> Unit = {},
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    onSeeAllAnime: () -> Unit = {},
    onOpenSection: (CuratedSectionType) -> Unit = {},
    onEnrichHeroTmdb: (suspend (title: String, isSeries: Boolean, year: String?) -> TmdbHeroMatch?)? = null,
    onOpenFavorites: () -> Unit = {},
    modifier: Modifier = Modifier,
    liveChannels: List<LiveStream> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    seriesList: List<Series> = emptyList(),
    liveCatMap: Map<String, String> = emptyMap(),
    vodCatMap: Map<String, String> = emptyMap(),
    seriesCatMap: Map<String, String> = emptyMap(),
    portalFeedData: PortalFeedData? = null,
    onChannelClick: (String, String) -> Unit = { _, _ -> },
    onMovieClick: (VodMovie) -> Unit = {},
    onSeriesClick: (Series) -> Unit = {},
    onOpenPlatform: ((String) -> Unit)? = null,
    continueWatchingItems: List<ContinueWatchingItem> = emptyList(),
    onResumeContinueWatching: (ContinueWatchingItem) -> Unit = {},
    onRemoveContinueWatching: (ContinueWatchingItem) -> Unit = {}
) {
    // FASE 35: Consumo directo de feed precalculado en segundo plano (0ms bloqueo en Main Thread)
    val topTenMovies = portalFeedData?.topTenMovies ?: emptyList()
    val liveMatches = portalFeedData?.liveMatches ?: emptyList()
    val recentMovies = portalFeedData?.recentMovies ?: emptyList()
    val mostViewedItems = portalFeedData?.mostViewedItems ?: emptyList()
    val terrorItems = portalFeedData?.terrorItems ?: emptyList()
    val sciFiItems = portalFeedData?.sciFiItems ?: emptyList()
    val realHistoryItems = portalFeedData?.realHistoryItems ?: emptyList()
    val actionItems = portalFeedData?.actionItems ?: emptyList()
    val animItems = portalFeedData?.animItems ?: emptyList()

    var isSearchExpanded by remember { mutableStateOf(false) }

    val platformHubItems = remember(movies, seriesList, liveChannels, vodCatMap, seriesCatMap, liveCatMap) {
        buildPlatformHubSpecs(movies, seriesList, liveChannels, vodCatMap, seriesCatMap, liveCatMap)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BixDarkBackground)
    ) {
        val context = LocalContext.current
        val voiceLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    onSearchChange(spokenText)
                }
            }
        }

        if (isSearchExpanded || searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF14161F))
                        .border(1.dp, if (searchQuery.isNotEmpty()) BixOrangeAccent else Color(0xFF252938), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = BixOrangeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(BixOrangeAccent),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Buscar películas, series o canales...",
                                        color = BixTextMuted,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar",
                                    tint = LelouchTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para buscar canales, películas o series...")
                                    }
                                    voiceLauncher.launch(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Búsqueda por voz no disponible", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voz",
                                tint = BixOrangeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = {
                        onSearchChange("")
                        isSearchExpanded = false
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14161F))
                        .border(1.dp, Color(0xFF252938), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            val matchingChannels = remember(liveChannels, liveCatMap, q) {
                liveChannels.asSequence().filter {
                    val cat = liveCatMap[it.categoryId].orEmpty().ifBlank { it.categoryName }
                    it.name.contains(q, ignoreCase = true) || cat.contains(q, ignoreCase = true)
                }.take(25).toList()
            }
            val matchingMovies = remember(movies, vodCatMap, q) {
                movies.asSequence().filter {
                    val cat = vodCatMap[it.categoryId].orEmpty().ifBlank { it.categoryName }
                    it.name.contains(q, ignoreCase = true) || cat.contains(q, ignoreCase = true)
                }.take(25).toList()
            }
            val matchingSeries = remember(seriesList, seriesCatMap, q) {
                seriesList.asSequence().filter {
                    val cat = seriesCatMap[it.categoryId].orEmpty().ifBlank { it.categoryName }
                    it.name.contains(q, ignoreCase = true) || cat.contains(q, ignoreCase = true)
                }.take(25).toList()
            }
            val totalResults = matchingChannels.size + matchingMovies.size + matchingSeries.size

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Resultados para \"$q\"",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalResults encontrados",
                        color = LelouchCyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (totalResults == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔍", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Sin resultados para \"$q\"",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Intenta con otro título o presiona el micrófono 🎤",
                                color = BixTextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Canales
                    if (matchingChannels.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "📺 Canales en Vivo (${matchingChannels.size})",
                            color = Color(0xFF00E5C9),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchingChannels) { ch ->
                                val resolvedUrl = StreamUrlResolver.resolveLive(ch.streamUrl, activeSource, ch.streamId)
                                NativeLiveStreamCard(
                                    channel = ch,
                                    onClick = { onChannelClick(ch.name, resolvedUrl) }
                                )
                            }
                        }
                    }

                    // Películas
                    if (matchingMovies.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "🎬 Películas (${matchingMovies.size})",
                            color = Color(0xFF00E5C9),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchingMovies) { movie ->
                                CuratedPosterItem(
                                    title = movie.name,
                                    imageUrl = movie.streamIcon,
                                    onClick = { onMovieClick(movie) }
                                )
                            }
                        }
                    }

                    // Series
                    if (matchingSeries.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "🎞️ Series (${matchingSeries.size})",
                            color = Color(0xFF00E5C9),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchingSeries) { series ->
                                CuratedPosterItem(
                                    title = series.name,
                                    imageUrl = series.cover,
                                    onClick = { onSeriesClick(series) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            // ══════════════════════════════════════════════════════════════════════
            // 4. HERO CAROUSEL FULL-BLEED + CABECERA FLOTANTE TRANSLÚCIDA
            // ══════════════════════════════════════════════════════════════════════
            Box(modifier = Modifier.fillMaxWidth()) {
                if (topTenMovies.isNotEmpty()) {
                    MobileTopTenHeroCarousel(
                        topMovies = topTenMovies,
                        onMovieClick = onMovieClick,
                        onExploreClick = onNavigateToMovies,
                        onEnrichHeroTmdb = onEnrichHeroTmdb,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(modifier = Modifier.height(76.dp))
                }

                // Cabecera flotante sobre el Hero
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF090A0E).copy(alpha = 0.86f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenAccountSwitcher() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BixOrangeAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("L", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "LELOUCH",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                letterSpacing = 0.6.sp
                            )
                            if (activeSource != null) {
                                Text(
                                    text = activeSource.name,
                                    color = BixOrangeAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF14161F).copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                .clickable { isSearchExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF14161F).copy(alpha = 0.85f))
                                .border(1.dp, Color(0xFFFF4081).copy(alpha = 0.45f), CircleShape)
                                .clickable { onOpenFavorites() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Favoritos", tint = Color(0xFFFF4081), modifier = Modifier.size(18.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF14161F).copy(alpha = 0.85f))
                                .border(1.dp, BixOrangeAccent.copy(alpha = 0.45f), CircleShape)
                                .clickable { onOpenAccountSwitcher() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Cuentas", tint = BixOrangeAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════════════════════
            // 🕘 CONTINUAR VIENDO (HISTORIAL DE REPRODUCCIÓN EN MÓVIL)
            // ══════════════════════════════════════════════════════════════════════
            if (continueWatchingItems.isNotEmpty()) {
                MobileContinueWatchingRail(
                    items = continueWatchingItems,
                    onResume = onResumeContinueWatching,
                    onRemove = onRemoveContinueWatching
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 🏷️ HUB VISUAL POR PLATAFORMAS / CATEGORÍAS REALES
            // ══════════════════════════════════════════════════════════════════════
            if (platformHubItems.isNotEmpty()) {
                MobilePlatformHubSection(
                    platforms = platformHubItems,
                    onPlatformClick = { spec -> onOpenPlatform?.invoke(spec.id) }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 1. 🔴 EN VIVO AHORA (PARTIDOS DEPORTIVOS ACTIVOS - CAPTURA 3)
            // ══════════════════════════════════════════════════════════════════════
            if (liveMatches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigateToLive() }
                    ) {
                        Text(
                            text = "🔴 EN VIVO AHORA",
                            color = Color(0xFFE50914),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(${liveMatches.size} partidos activos)",
                            color = BixTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "VER TODOS >",
                        color = Color(0xFF00E5C9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToLive() }
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(liveMatches) { channel ->
                        val resolvedUrl = StreamUrlResolver.resolveLive(channel.streamUrl, activeSource, channel.streamId)
                        LiveMatchCard(
                            channel = channel,
                            onClick = { onChannelClick(channel.name, resolvedUrl) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 2. ▌ RECIÉN AGREGADAS (CAPTURA 4 CON TARJETA + VER MÁS)
            // ══════════════════════════════════════════════════════════════════════
            if (recentMovies.isNotEmpty()) {
                CuratedSectionHeader(
                    title = "RECIÉN AGREGADAS",
                    actionText = "VER MÁS >",
                    barColor = Color(0xFF00E5C9),
                    onActionClick = { onOpenSection(CuratedSectionType.RECENT) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentMovies) { movie ->
                        CuratedPosterItem(
                            title = movie.name,
                            imageUrl = movie.streamIcon,
                            rating = movie.rating,
                            year = movie.year,
                            onClick = { onMovieClick(movie) }
                        )
                    }
                    item {
                        SeeMorePosterCard(
                            remainingCount = recentMovies.size,
                            onClick = { onOpenSection(CuratedSectionType.RECENT) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 3. ▌ MEJOR VALORADAS
            // ══════════════════════════════════════════════════════════════════════
            if (mostViewedItems.isNotEmpty()) {
                CuratedSectionHeader(
                    title = "MEJOR VALORADAS",
                    actionText = "VER MÁS >",
                    barColor = Color(0xFFFFB800),
                    onActionClick = { onOpenSection(CuratedSectionType.TOP_RATED) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(mostViewedItems) { item ->
                        CuratedPosterItem(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            rating = item.rating,
                            year = item.year,
                            onClick = {
                                item.movie?.let(onMovieClick)
                                item.series?.let(onSeriesClick)
                            }
                        )
                    }
                    item {
                        SeeMorePosterCard(
                            remainingCount = mostViewedItems.size,
                            onClick = { onOpenSection(CuratedSectionType.TOP_RATED) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 4. ▌ TERROR Y SUSPENSO
            // ══════════════════════════════════════════════════════════════════════
            if (terrorItems.isNotEmpty()) {
                CuratedSectionHeader(
                    title = "TERROR Y SUSPENSO",
                    actionText = "VER MÁS >",
                    barColor = Color(0xFFFF5252),
                    onActionClick = { onOpenSection(CuratedSectionType.HORROR) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(terrorItems) { item ->
                        CuratedPosterItem(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            rating = item.rating,
                            year = item.year,
                            onClick = {
                                item.movie?.let(onMovieClick)
                                item.series?.let(onSeriesClick)
                            }
                        )
                    }
                    item {
                        SeeMorePosterCard(
                            remainingCount = terrorItems.size,
                            onClick = { onOpenSection(CuratedSectionType.HORROR) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 5. ▌ CIENCIA FICCIÓN
            // ══════════════════════════════════════════════════════════════════════
            if (sciFiItems.isNotEmpty()) {
                CuratedSectionHeader(
                    title = "CIENCIA FICCIÓN",
                    actionText = "VER MÁS >",
                    barColor = Color(0xFF00E5C9),
                    onActionClick = { onOpenSection(CuratedSectionType.SCIFI) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sciFiItems) { item ->
                        CuratedPosterItem(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            rating = item.rating,
                            year = item.year,
                            onClick = {
                                item.movie?.let(onMovieClick)
                                item.series?.let(onSeriesClick)
                            }
                        )
                    }
                    item {
                        SeeMorePosterCard(
                            remainingCount = sciFiItems.size,
                            onClick = { onOpenSection(CuratedSectionType.SCIFI) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 6. ▌ BASADO EN HECHOS REALES
            // ══════════════════════════════════════════════════════════════════════
            if (realHistoryItems.isNotEmpty()) {
                CuratedSectionHeader(
                    title = "BASADO EN HECHOS REALES",
                    actionText = "VER MÁS >",
                    barColor = Color(0xFFE5A93C),
                    onActionClick = { onOpenSection(CuratedSectionType.TRUE_EVENTS) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(realHistoryItems) { item ->
                        CuratedPosterItem(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            rating = item.rating,
                            year = item.year,
                            onClick = {
                                item.movie?.let(onMovieClick)
                                item.series?.let(onSeriesClick)
                            }
                        )
                    }
                    item {
                        SeeMorePosterCard(
                            remainingCount = realHistoryItems.size,
                            onClick = { onOpenSection(CuratedSectionType.TRUE_EVENTS) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 7. ▌ ACCIÓN Y AVENTURA
            // ══════════════════════════════════════════════════════════════════════
            if (actionItems.isNotEmpty()) {
                CuratedSectionHeader(
                    title = "ACCIÓN Y AVENTURA",
                    actionText = "VER MÁS >",
                    barColor = Color(0xFFFF6D00),
                    onActionClick = { onOpenSection(CuratedSectionType.ACTION) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(actionItems) { item ->
                        CuratedPosterItem(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            rating = item.rating,
                            year = item.year,
                            onClick = {
                                item.movie?.let(onMovieClick)
                                item.series?.let(onSeriesClick)
                            }
                        )
                    }
                    item {
                        SeeMorePosterCard(
                            remainingCount = actionItems.size,
                            onClick = { onOpenSection(CuratedSectionType.ACTION) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ══════════════════════════════════════════════════════════════════════
            // 8. ▌ MUNDO ANIMADO
            // ══════════════════════════════════════════════════════════════════════
            if (animItems.isNotEmpty()) {
                CuratedSectionHeader(
                    title = "MUNDO ANIMADO",
                    actionText = "VER MÁS >",
                    barColor = Color(0xFFFF4081),
                    onActionClick = { onOpenSection(CuratedSectionType.ANIMATION) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(animItems) { item ->
                        CuratedPosterItem(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            rating = item.rating,
                            year = item.year,
                            onClick = {
                                item.movie?.let(onMovieClick)
                                item.series?.let(onSeriesClick)
                            }
                        )
                    }
                    item {
                        SeeMorePosterCard(
                            remainingCount = animItems.size,
                            onClick = { onOpenSection(CuratedSectionType.ANIMATION) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLES VISUALES NATIVOS 1:1 CON LAS CAPTURAS DE REFERENCIA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MobileContinueWatchingRail(
    items: List<ContinueWatchingItem>,
    onResume: (ContinueWatchingItem) -> Unit,
    onRemove: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🕘 CONTINUAR VIENDO",
                    color = Color(0xFF00E5C9),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "(${items.size})",
                    color = BixTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items, key = { it.contentId }) { item ->
                MobileContinueWatchingCard(
                    item = item,
                    onResume = { onResume(item) },
                    onRemove = { onRemove(item) }
                )
            }
        }
    }
}

@Composable
private fun MobileContinueWatchingCard(
    item: ContinueWatchingItem,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14161F))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable { onResume() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(Color(0xFF0A0C10))
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Botón central translúcido de Play
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, BixOrangeAccent, CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Reanudar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Badge tipo de contenido
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BixOrangeAccent.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (item.episodeLabel != null) "SERIE" else "PELÍCULA",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Botón quitar en esquina superior derecha
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quitar",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Barra de progreso horizontal inferior
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.fraction.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(BixOrangeAccent)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = listOfNotNull(
                    item.episodeLabel,
                    item.remainingLabel.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        color = Color(0xFF2DD4BF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Hero Carousel del TOP 10 (Captura 1):
 * - Banner vertical grande con imagen de fondo (backdrop o poster).
 * - Gradiente oscuro con difuminado suave.
 * - Píldora TOP 10 • 0X en color Cyan (#00E5C9).
 * - Título, metadatos y sinopsis corta.
 * - Botones [ ▶ Descubrir ] y [ Explorar → ].
 * - Paginador "05 / 10" con barras de estado interactivas.
 */
@Composable
private fun MobileTopTenHeroCarousel(
    topMovies: List<VodMovie>,
    onMovieClick: (VodMovie) -> Unit,
    onExploreClick: () -> Unit,
    onEnrichHeroTmdb: (suspend (title: String, isSeries: Boolean, year: String?) -> TmdbHeroMatch?)? = null,
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentMovie = topMovies.getOrNull(currentIndex) ?: return
    val enrichedHeroMap = remember { mutableStateMapOf<String, TmdbHeroMatch?>() }

    LaunchedEffect(currentMovie.streamId, onEnrichHeroTmdb) {
        if (onEnrichHeroTmdb != null && !enrichedHeroMap.containsKey(currentMovie.name)) {
            enrichedHeroMap[currentMovie.name] = onEnrichHeroTmdb(currentMovie.name, false, currentMovie.year)
        }
    }
    val tmdbHero = enrichedHeroMap[currentMovie.name]

    // Auto-avance suave cada 6 segundos si el usuario no interactúa
    LaunchedEffect(topMovies) {
        while (isActive) {
            delay(6000L)
            if (topMovies.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % topMovies.size
            }
        }
    }

    val imageUrl = tmdbHero?.backdropUrl?.ifBlank { null }
        ?: currentMovie.backdropPath?.ifBlank { null }
        ?: tmdbHero?.posterUrl?.ifBlank { null }
        ?: currentMovie.streamIcon?.ifBlank { null }

    val formattedIndex = String.format("%02d", currentIndex + 1)
    val totalCount = String.format("%02d", topMovies.size)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(410.dp)
            .background(Color(0xFF090A0E))
    ) {
        // Imagen de Fondo del Hero
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = currentMovie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Degradado Scrim Negro Cinematográfico
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color(0xFF090A0E).copy(alpha = 0.85f),
                            Color(0xFF090A0E)
                        )
                    )
                )
        )

        // Contenido Textual y Acciones (Alineado en la parte inferior)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Píldora Naranja DESTACADO
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(BixOrangeAccent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "DESTACADO • $formattedIndex",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // Título de la Película
            Text(
                text = currentMovie.name,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 28.sp
            )

            // Subtítulo con Metadatos: Película • Año • ★ Rating
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Película",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "•", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)

                val yearText = tmdbHero?.year?.ifBlank { null } ?: currentMovie.year?.ifBlank { "2026" } ?: "2026"
                Text(
                    text = yearText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "•", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)

                val rating = tmdbHero?.rating?.takeIf { it > 0.0 } ?: currentMovie.rating ?: 0.0
                val ratingFormatted = if (rating > 0.0) "★ %.1f".format(rating) else "★ 6.5"
                Text(
                    text = ratingFormatted,
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sinopsis corta (2 líneas máx)
            val synopsis = tmdbHero?.overview?.ifBlank { null }
                ?: currentMovie.plot?.ifBlank { null }
                ?: "Descubre esta película recién agregada con la máxima calidad y fluidez del reproductor Lelouch."
            Text(
                text = synopsis,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(2.dp))

            // Fila de Botones: [ ▶ Ver Ahora ] y [ Siguiente → ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onMovieClick(currentMovie) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BixOrangeAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Ver Ahora",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        currentIndex = (currentIndex + 1) % topMovies.size
                    },
                    modifier = Modifier.weight(0.75f).height(42.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Siguiente →",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Fila Indicadora de Página "05 / 10" y Barras Dash Interactivas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$formattedIndex / $totalCount",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topMovies.indices.forEach { index ->
                        val isSelected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .height(3.dp)
                                .weight(if (isSelected) 1.8f else 1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected) BixOrangeAccent
                                    else Color.White.copy(alpha = 0.25f)
                                )
                                .clickable { currentIndex = index }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cabecera limpia de Carril Curado (Captura 4):
 * Barra de color vertical `▌ `, título a la izquierda en blanco negrita, y `VER MÁS >` en Cyan (#00E5C9).
 */
@Composable
private fun CuratedSectionHeader(
    title: String,
    actionText: String = "VER MÁS >",
    barColor: Color = Color(0xFFFFB800),
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onActionClick)
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.2.sp
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = ">",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Ver todo >",
            color = BixOrangeAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onActionClick)
        )
    }
}

/**
 * Tarjeta de Cartel Curado (Captura 4):
 * Imagen de portada vertical con esquinas redondeadas, título debajo y valoración ★ + Año.
 * Soporta carteles de reemplazo elegantes para títulos sin portada HTTP válida.
 */
@Composable
internal fun CuratedPosterItem(
    title: String,
    imageUrl: String?,
    rating: Double? = null,
    year: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(115.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E2230), Color(0xFF0F111A))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
        ) {
            val hasValidHttp = imageUrl?.trim().orEmpty().startsWith("http")
            if (hasValidHttp) {
                val context = LocalContext.current
                val imageRequest = remember(imageUrl) {
                    coil.request.ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(240, 360)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Cartel estilizado de reemplazo con icono y título
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF00E5C9).copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Línea de Calificación y Año (Captura 4: ★ 6.754 • 2026)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val r = rating ?: 6.5
            Text(
                text = "★ ${"%.1f".format(r)}",
                color = Color(0xFFFFD700),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (!year.isNullOrBlank()) {
                Text(text = "•", color = BixTextMuted, fontSize = 10.sp)
                Text(
                    text = year,
                    color = BixTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Tarjeta "+ VER MÁS" al final del carrusel (Captura 4):
 * Mismo tamaño que CuratedPosterItem (115dp x 168dp), borde sutil, icono + central y conteo de títulos.
 */
@Composable
private fun SeeMorePosterCard(
    remainingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(115.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14161F))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Ver Más",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "VER MÁS",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
            if (remainingCount > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "+$remainingCount títulos",
                    color = Color(0xFFFFB800),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private enum class SportTheme {
    CRICKET, BASEBALL, BOXING, BASKETBALL, DEFAULT
}

private fun getSportTheme(name: String, categoryName: String): SportTheme {
    val text = "$name $categoryName".lowercase()
    return when {
        text.contains("cricket") || text.contains("pakist") || text.contains("sri lanka") || text.contains("ipl") || text.contains("cric") -> SportTheme.CRICKET
        text.contains("baseball") || text.contains("béisbol") || text.contains("mlb") || text.contains("fall league") || text.contains("lidom") || text.contains("lmp") -> SportTheme.BASEBALL
        text.contains("box") || text.contains("ufc") || text.contains("mma") || text.contains("ring") || text.contains("peso ligero") || text.contains("combate") || text.contains("lucha") || text.contains("wwe") -> SportTheme.BOXING
        text.contains("nba") || text.contains("basket") || text.contains("baloncesto") || text.contains("ncaab") -> SportTheme.BASKETBALL
        else -> SportTheme.DEFAULT
    }
}

@Composable
private fun SportStadiumBackground(
    sportTheme: SportTheme,
    modifier: Modifier = Modifier
) {
    when (sportTheme) {
        SportTheme.CRICKET -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF09141D), Color(0xFF0F2624), Color(0xFF133626))
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF8AE0FF).copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(70.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF8F7A50).copy(0.35f), Color(0xFFB09865).copy(0.45f))
                            ),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        SportTheme.BASEBALL -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF5A2A14),
                                Color(0xFF1F4225),
                                Color(0xFF0C1710)
                            ),
                            radius = 420f
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFDF9E).copy(alpha = 0.18f), Color.Transparent)
                            )
                        )
                )
            }
        }
        SportTheme.BOXING -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A0D14), Color(0xFF151924), Color(0xFF0B0E17))
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF00E5C9).copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color(0xFFE50914).copy(alpha = 0.5f)))
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(alpha = 0.4f)))
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color(0xFF0072FF).copy(alpha = 0.5f)))
                }
            }
        }
        SportTheme.BASKETBALL -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF6B3A17), Color(0xFF381C0B), Color(0xFF120A04)),
                            radius = 400f
                        )
                    )
            )
        }
        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF174228),
                                Color(0xFF0D2518),
                                Color(0xFF08120D)
                            ),
                            radius = 420f
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF00E5C9).copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

/**
 * Tarjeta de Partido Deportivo en Vivo (Captura 3):
 * Fondo estadio según deporte, badge "🔴 EN VIVO" arriba a la izquierda,
 * escudos de rivales con insignia "VS" en el centro, y panel oscuro con título y torneo abajo.
 */
@Composable
private fun LiveMatchCard(
    channel: LiveStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vsRegex = Regex("(?i)\\s+(?:vs\\.?|v)\\s+")
    val cleanName = channel.name.replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "").trim()
    val parts = cleanName.split(vsRegex)
    val hasOpponents = parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
    val teamA = if (hasOpponents) parts[0].trim().take(18) else ""
    val teamB = if (hasOpponents) parts[1].trim().take(18) else ""

    val sportTheme = remember(channel.name, channel.categoryName) {
        getSportTheme(channel.name, channel.categoryName)
    }

    Box(
        modifier = modifier
            .width(240.dp)
            .height(138.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D111A))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        // Fondo temático de estadio / cancha
        SportStadiumBackground(sportTheme = sportTheme)

        // Arriba Izquierda: Badge 🔴 EN VIVO
        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE50914).copy(alpha = 0.95f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "EN VIVO",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // Centro: Rivales vs Rival o Logo del Partido
        if (hasOpponents) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Escudo Equipo A
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B2234))
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = teamA.take(3).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }

                // Insignia Neón VS
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00E5C9), Color(0xFF0072FF))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "VS",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }

                // Escudo Equipo B
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B2234))
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = teamB.take(3).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            // Evento o Canal Deportivo
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 26.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF162030))
                    .border(1.5.dp, Color(0xFF00E5C9).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("⚽", fontSize = 20.sp)
                }
            }
        }

        // Panel inferior oscuro
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF0B0E17).copy(alpha = 0.94f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = cleanName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.categoryName.ifBlank { "Deportes en Vivo" },
                    color = BixTextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BixExplorePill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF14161F))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun NativeLiveStreamCard(
    channel: LiveStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(145.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14161F))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
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
                        text = channel.name.take(3).uppercase(),
                        color = Color(0xFF00E5C9),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE50914))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel.categoryName.ifBlank { "Canal en Vivo" },
                color = BixTextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// HUB VISUAL POR PLATAFORMAS / CATEGORÍAS REALES + VISTA DEDICADA
// ══════════════════════════════════════════════════════════════════════════════

internal data class PlatformHubSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val movies: List<VodMovie>,
    val series: List<Series>,
    val channels: List<LiveStream>
) {
    val totalCount: Int get() = movies.size + series.size + channels.size
}

private data class PlatformMatchRule(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val keywords: List<String>
)

private val platformRules = listOf(
    PlatformMatchRule("netflix", "Netflix", "Series y Películas", Color(0xFFE50914), listOf("netflix", "nfx")),
    PlatformMatchRule("prime", "Prime Video", "Amazon Originals", Color(0xFF00A8E1), listOf("prime", "amazon")),
    PlatformMatchRule("disney", "Disney+", "Familia y Marvel", Color(0xFF1E54E8), listOf("disney", "pixar", "marvel", "star wars")),
    PlatformMatchRule("max", "Max", "HBO y Warner", Color(0xFF7B2CBF), listOf("hbo", "max", "warner")),
    PlatformMatchRule("deportes", "Deportes", "En Vivo y Eventos", Color(0xFF10B981), listOf("deporte", "sport", "futbol", "fútbol", "liga", "espn", "fox sports", "tnt sports", "nba", "ufc")),
    PlatformMatchRule("anime", "Anime", "Series y Películas", Color(0xFFFF6B00), listOf("anime", "crunchyroll", "animación", "animacion"))
)

private fun isCurrentYearRelease(yearOrDate: String?, title: String, currentYearStr: String): Boolean {
    return yearOrDate?.trim()?.startsWith(currentYearStr) == true || title.contains(currentYearStr)
}

internal fun buildPlatformHubSpecs(
    movies: List<VodMovie>,
    seriesList: List<Series>,
    liveChannels: List<LiveStream>,
    vodCatMap: Map<String, String> = emptyMap(),
    seriesCatMap: Map<String, String> = emptyMap(),
    liveCatMap: Map<String, String> = emptyMap()
): List<PlatformHubSpec> {
    val currentYearStr = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
    return platformRules.mapNotNull { rule ->
        val matchedMovies = movies.filter { m ->
            val cat = vodCatMap[m.categoryId].orEmpty().ifBlank { m.categoryName }.lowercase()
            val name = m.name.lowercase()
            val genre = m.genre.orEmpty().lowercase()
            rule.keywords.any { kw -> cat.contains(kw) || name.contains(kw) || genre.contains(kw) }
        }.sortedWith(compareByDescending { isCurrentYearRelease(it.year, it.name, currentYearStr) })
        val matchedSeries = seriesList.filter { s ->
            val cat = seriesCatMap[s.categoryId].orEmpty().ifBlank { s.categoryName }.lowercase()
            val name = s.name.lowercase()
            val genre = s.genre.orEmpty().lowercase()
            rule.keywords.any { kw -> cat.contains(kw) || name.contains(kw) || genre.contains(kw) }
        }.sortedWith(compareByDescending { isCurrentYearRelease(it.releaseDate, it.name, currentYearStr) })
        val matchedChannels = liveChannels.filter { ch ->
            val cat = liveCatMap[ch.categoryId].orEmpty().ifBlank { ch.categoryName }.lowercase()
            val name = ch.name.lowercase()
            rule.keywords.any { kw -> cat.contains(kw) || name.contains(kw) }
        }

        val total = matchedMovies.size + matchedSeries.size + matchedChannels.size
        if (total == 0) null
        else PlatformHubSpec(
            id = rule.id,
            title = rule.title,
            subtitle = "$total títulos",
            accentColor = rule.accentColor,
            movies = matchedMovies,
            series = matchedSeries,
            channels = matchedChannels
        )
    }
}

@Composable
private fun MobilePlatformHubSection(
    platforms: List<PlatformHubSpec>,
    onPlatformClick: (PlatformHubSpec) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Explorar por Plataforma",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "${platforms.size} hubs",
                color = BixOrangeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(platforms, key = { it.id }) { spec ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onPlatformClick(spec) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(118.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (spec.id) {
                            "netflix" -> Text(
                                text = "NETFLIX",
                                color = Color(0xFFE50914),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            "prime" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "prime video",
                                    color = Color(0xFF00A8E1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF00A8E1))
                                )
                            }
                            "disney" -> Text(
                                text = "Disney+",
                                color = Color(0xFF113CCF),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )
                            "max" -> Text(
                                text = "max",
                                color = Color(0xFF002BE7),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            "deportes" -> Text(
                                text = "⚽ DEPORTES",
                                color = Color(0xFF059669),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            else -> Text(
                                text = "ANIME",
                                color = BixOrangeAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Text(
                        text = spec.title,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun MobilePlatformDetailView(
    spec: PlatformHubSpec,
    activeSource: SourceConfig?,
    onBack: () -> Unit,
    onMovieClick: (VodMovie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onChannelClick: (String, String) -> Unit
) {
    var filterTab by remember(spec.id) { mutableStateOf("all") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BixDarkBackground)
    ) {
        // Cabecera FIJA superior (nunca se pierde al hacer scroll en Netflix / Prime / Disney+ / Max)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0D13))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF14161F))
                        .border(1.dp, BixOrangeAccent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = BixOrangeAccent, modifier = Modifier.size(16.dp))
                    Text("Regresar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        text = "${spec.totalCount} títulos disponibles",
                        color = BixOrangeAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Pills fijas de filtro rápido dentro de la plataforma
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabs = buildList {
                    add("all" to "Todo (${spec.totalCount})")
                    if (spec.movies.isNotEmpty()) add("movies" to "Películas (${spec.movies.size})")
                    if (spec.series.isNotEmpty()) add("series" to "Series (${spec.series.size})")
                    if (spec.channels.isNotEmpty()) add("channels" to "Canales (${spec.channels.size})")
                }
                items(tabs) { (id, label) ->
                    val active = filterTab == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) BixOrangeAccent else Color(0xFF14161F))
                            .border(1.dp, if (active) BixOrangeAccent else Color(0xFF252938), RoundedCornerShape(20.dp))
                            .clickable { filterTab = id }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F2330)))

        // Lista reciclable independiente debajo de la cabecera fija
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if ((filterTab == "all" || filterTab == "channels") && spec.channels.isNotEmpty()) {
                item(key = "channels_section") {
                    Column(modifier = Modifier.padding(bottom = 14.dp)) {
                        Text("Canales (${spec.channels.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(spec.channels) { ch ->
                                val resolvedUrl = StreamUrlResolver.resolveLive(ch.streamUrl, activeSource, ch.streamId)
                                NativeLiveStreamCard(
                                    channel = ch,
                                    onClick = { onChannelClick(ch.name, resolvedUrl) }
                                )
                            }
                        }
                    }
                }
            }

            if ((filterTab == "all" || filterTab == "movies") && spec.movies.isNotEmpty()) {
                item(key = "movies_header") {
                    Text(
                        "Películas (${spec.movies.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                val movieRows = spec.movies.chunked(3)
                items(movieRows.size, key = { idx -> "mov_row_$idx" }) { rowIdx ->
                    val rowItems = movieRows[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { movie ->
                            Box(modifier = Modifier.weight(1f)) {
                                CuratedPosterItem(
                                    title = movie.name,
                                    imageUrl = movie.streamIcon,
                                    rating = movie.rating,
                                    year = movie.year,
                                    onClick = { onMovieClick(movie) }
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if ((filterTab == "all" || filterTab == "series") && spec.series.isNotEmpty()) {
                item(key = "series_header") {
                    Text(
                        "Series (${spec.series.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    )
                }
                val seriesRows = spec.series.chunked(3)
                items(seriesRows.size, key = { idx -> "ser_row_$idx" }) { rowIdx ->
                    val rowItems = seriesRows[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { series ->
                            Box(modifier = Modifier.weight(1f)) {
                                CuratedPosterItem(
                                    title = series.name,
                                    imageUrl = series.cover,
                                    rating = series.rating,
                                    onClick = { onSeriesClick(series) }
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "bottom_pad") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
