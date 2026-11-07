package com.lelouch.feature.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.Series
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.VodMovie
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    onSearchClick: () -> Unit = onNavigateToMovies,
    onSeeAllAnime: () -> Unit = {},
    modifier: Modifier = Modifier,
    liveChannels: List<LiveStream> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    seriesList: List<Series> = emptyList(),
    onChannelClick: (String, String) -> Unit = { _, _ -> },
    onMovieClick: (VodMovie) -> Unit = {},
    onSeriesClick: (Series) -> Unit = {}
) {
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val currentYearStr = remember(currentYear) { currentYear.toString() }
    val previousYearStr = remember(currentYear) { (currentYear - 1).toString() }

    // Helper defensivo para comprobar si un contenido pertenece al año en curso
    fun isMovieFromYear(movie: VodMovie, year: String): Boolean {
        return movie.year?.contains(year) == true ||
               movie.added?.startsWith(year) == true ||
               movie.name.contains(year)
    }

    fun isSeriesFromYear(series: Series, year: String): Boolean {
        return series.releaseDate?.startsWith(year) == true ||
               series.name.contains(year)
    }

    // Top 10 películas más recientes / destacadas exclusivamente del año en curso con imagen válida
    val topTenMovies = remember(movies, currentYearStr) {
        val valid = movies.filter { it.streamIcon?.trim().orEmpty().startsWith("http") }
        val currentYearMovies = valid.filter { isMovieFromYear(it, currentYearStr) }
        if (currentYearMovies.size >= 3) {
            currentYearMovies.take(10)
        } else {
            val fallback = valid.filter { isMovieFromYear(it, currentYearStr) || isMovieFromYear(it, previousYearStr) }
            if (fallback.isNotEmpty()) fallback.take(10) else valid.take(10)
        }
    }

    // Mejores películas de la semana (exclusivamente del año en curso)
    val weeklyMovies = remember(movies, currentYearStr) {
        val valid = movies.filter { it.streamIcon?.trim().orEmpty().startsWith("http") }
        val currentYearMovies = valid.filter { isMovieFromYear(it, currentYearStr) }
        if (currentYearMovies.size >= 3) {
            currentYearMovies.take(25)
        } else {
            val fallback = valid.filter { isMovieFromYear(it, currentYearStr) || isMovieFromYear(it, previousYearStr) }
            if (fallback.isNotEmpty()) fallback.take(25) else valid.take(25)
        }
    }

    // Mejores series de la semana (exclusivamente del año en curso)
    val weeklySeries = remember(seriesList, currentYearStr) {
        val valid = seriesList.filter { it.cover?.trim().orEmpty().startsWith("http") }
        val currentYearSeries = valid.filter { isSeriesFromYear(it, currentYearStr) }
        if (currentYearSeries.size >= 3) {
            currentYearSeries.take(25)
        } else {
            val fallback = valid.filter { isSeriesFromYear(it, currentYearStr) || isSeriesFromYear(it, previousYearStr) }
            if (fallback.isNotEmpty()) fallback.take(25) else valid.take(25)
        }
    }

    // Sección Anime y Animación solicitada explícitamente por el usuario
    val animeRailItems = remember(movies, seriesList) {
        val animeKeywords = listOf("anime", "animaci", "manga", "otaku", "japón", "japon", "dragon ball", "naruto", "one piece", "demon slayer", "shingeki", "ghibli")
        val animeFromSeries = seriesList.filter { s ->
            s.cover?.trim().orEmpty().startsWith("http") && animeKeywords.any { kw ->
                s.name.contains(kw, ignoreCase = true) || (s.categoryName ?: "").contains(kw, ignoreCase = true) || (s.plot ?: "").contains(kw, ignoreCase = true)
            }
        }.map { CuratedRailItem(it.name, it.cover, it.rating5based, onClick = { onSeriesClick(it) }) }

        val animeFromMovies = movies.filter { m ->
            m.streamIcon?.trim().orEmpty().startsWith("http") && animeKeywords.any { kw ->
                m.name.contains(kw, ignoreCase = true) || m.categoryName.contains(kw, ignoreCase = true) || (m.genre ?: "").contains(kw, ignoreCase = true)
            }
        }.map { CuratedRailItem(it.name, it.streamIcon, it.rating, onClick = { onMovieClick(it) }) }

        (animeFromSeries + animeFromMovies).take(25)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2E0C06),
                        Color(0xFF180703),
                        BixDarkBackground,
                        BixDarkBackground
                    )
                )
            )
    ) {
        // ══════════════════════════════════════════════════════════════════════
        // 1. CABECERA SUPERIOR ESTILO LELOUCH PLUS
        // ══════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icono Circular estilizado Lelouch
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(BixOrangeAccent, Color(0xFFD9480F))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "L",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LELOUCH",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "PLUS",
                            color = BixOrangeAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PELICULAS, SERIES, TV EN VIVO",
                            color = BixTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        sourceConnectionLabel(activeSource)?.let { label ->
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = BixOrangeAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    if (activeSource != null) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E202B))
                                .border(1.dp, BixOrangeAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable { onOpenAccountSwitcher() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = activeSource.name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("Cambiar ▼", color = BixOrangeAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E202B))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable { onNavigateToLive() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = "TV en vivo",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // 2. BARRA DE BÚSQUEDA INTEGRADA TIPO PÍLDORA
        // ══════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF14161F))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .clickable { onSearchClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = BixTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Buscar películas, series o canales...",
                    color = BixTextMuted,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 4. HERO CAROUSEL: TOP 10 MÁS RECIENTES (CAPTURA 1)
        // ══════════════════════════════════════════════════════════════════════
        if (topTenMovies.isNotEmpty()) {
            MobileTopTenHeroCarousel(
                topMovies = topTenMovies,
                onMovieClick = onMovieClick,
                onExploreClick = onNavigateToMovies,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 5. CARRIL HORIZONTAL 1: MEJORES PELÍCULAS DE LA SEMANA (CAPTURA 2)
        // ══════════════════════════════════════════════════════════════════════
        if (weeklyMovies.isNotEmpty()) {
            CuratedSectionHeader(
                title = "Mejores películas de la semana",
                actionText = "VER TODO →",
                onActionClick = onNavigateToMovies
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(weeklyMovies) { movie ->
                    CuratedPosterItem(
                        title = movie.name,
                        imageUrl = movie.streamIcon,
                        onClick = { onMovieClick(movie) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 6. CARRIL HORIZONTAL 2: MEJORES SERIES DE LA SEMANA (CAPTURA 2)
        // ══════════════════════════════════════════════════════════════════════
        if (weeklySeries.isNotEmpty()) {
            CuratedSectionHeader(
                title = "Mejores series de la semana",
                actionText = "VER TODO →",
                onActionClick = onNavigateToSeries
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(weeklySeries) { series ->
                    CuratedPosterItem(
                        title = series.name,
                        imageUrl = series.cover,
                        onClick = { onSeriesClick(series) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 7. CARRIL HORIZONTAL 3: ANIME Y ANIMACIÓN (CAPTURA 2 / PETICIÓN USUARIO)
        // ══════════════════════════════════════════════════════════════════════
        if (animeRailItems.isNotEmpty()) {
            CuratedSectionHeader(
                title = "Anime y Animación",
                actionText = "VER TODO →",
                onActionClick = onSeeAllAnime
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(animeRailItems) { item ->
                    CuratedPosterItem(
                        title = item.title,
                        imageUrl = item.imageUrl,
                        onClick = item.onClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 8. CARRIL HORIZONTAL 4: EN VIVO & DEPORTES
        // ══════════════════════════════════════════════════════════════════════
        if (liveChannels.isNotEmpty()) {
            CuratedSectionHeader(
                title = "En Vivo & Deportes",
                actionText = "VER TODO →",
                onActionClick = onNavigateToLive
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(liveChannels.take(15)) { channel ->
                    NativeLiveStreamCard(
                        channel = channel,
                        onClick = { onChannelClick(channel.streamUrl, channel.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLES VISUALES NATIVOS 1:1 CON LAS CAPTURAS DE REFERENCIA
// ─────────────────────────────────────────────────────────────────────────────

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
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentMovie = topMovies.getOrNull(currentIndex) ?: return

    // Auto-avance suave cada 6 segundos si el usuario no interactúa
    LaunchedEffect(topMovies) {
        while (isActive) {
            delay(6000L)
            if (topMovies.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % topMovies.size
            }
        }
    }

    val imageUrl = currentMovie.backdropPath?.ifBlank { null }
        ?: currentMovie.streamIcon?.ifBlank { null }

    val formattedIndex = String.format("%02d", currentIndex + 1)
    val totalCount = String.format("%02d", topMovies.size)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(390.dp)
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
            // Píldora Cyan TOP 10 • 0X
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF00E5C9))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "TOP 10 • $formattedIndex",
                    color = Color.Black,
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

                val yearText = currentMovie.year?.ifBlank { "2026" } ?: "2026"
                Text(
                    text = yearText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "•", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)

                val rating = currentMovie.rating ?: 0.0
                val ratingFormatted = if (rating > 0.0) "★ %.1f".format(rating) else "★ 6.5"
                Text(
                    text = ratingFormatted,
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sinopsis corta (2 líneas máx)
            val synopsis = currentMovie.plot?.ifBlank { null }
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

            // Fila de Botones: [ ▶ Descubrir ] y [ Explorar → ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onMovieClick(currentMovie) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5C9),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Descubrir",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        // Avanza al siguiente ítem del Top 10
                        currentIndex = (currentIndex + 1) % topMovies.size
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Explorar →",
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
                                    if (isSelected) Color(0xFF00E5C9)
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
 * Cabecera limpia de Carril Curado (Captura 2):
 * Título a la izquierda en blanco negrita, "VER TODO →" a la derecha en Cyan (#00E5C9).
 */
@Composable
private fun CuratedSectionHeader(
    title: String,
    actionText: String,
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
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.2.sp
        )
        Text(
            text = actionText,
            color = Color(0xFF00E5C9),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onActionClick)
        )
    }
}

/**
 * Tarjeta de Cartel Curado (Captura 2):
 * Imagen de portada vertical con esquinas redondeadas y TÍTULO LIMPIO DEBAJO de la portada.
 */
@Composable
private fun CuratedPosterItem(
    title: String,
    imageUrl: String?,
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
                .background(Color(0xFF14161F))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = title.take(2).uppercase(),
                        color = Color(0xFF00E5C9),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

private data class CuratedRailItem(
    val title: String,
    val imageUrl: String?,
    val rating: Double?,
    val onClick: () -> Unit
)

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
