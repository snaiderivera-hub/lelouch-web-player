package com.lelouch.feature.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig
import com.lelouch.feature.tv.ChannelUiModel
import com.lelouch.feature.tv.MovieUiModel
import com.lelouch.feature.tv.SeriesUiModel

/**
 * Experiencia 100% Nativa y Cinemática para Android TV Box (D-Pad Compatible).
 * Combina el flujo de los mejores repositorios de streaming:
 * 1. Hero Featured Banner (Póster/Backdrop con reproducción directa mediante control remoto).
 * 2. Píldoras de Categoría con navegación D-Pad y contadores en tiempo real.
 * 3. Carruseles Horizontales de Contenido (En Vivo, Películas y Series) con foco D-Pad fluido.
 * 4. Tarjeta de Estado y Acciones Rápidas (Ajustes, Recargar, M3U).
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvPortalDashboard(
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
    onOpenDiagnostics: () -> Unit,
    onOpenCategoryFilter: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    modifier: Modifier = Modifier,
    sidebarRequester: FocusRequester? = null,
    firstItemRequester: FocusRequester? = null,
    visibleChannels: List<ChannelUiModel> = emptyList(),
    visibleMovies: List<MovieUiModel> = emptyList(),
    visibleSeriesList: List<SeriesUiModel> = emptyList(),
    onChannelClick: ((ChannelUiModel) -> Unit)? = null,
    onMovieClick: ((MovieUiModel) -> Unit)? = null,
    onSeriesClick: ((SeriesUiModel) -> Unit)? = null
) {
    val heroBtnRequester = remember { firstItemRequester ?: FocusRequester() }
    val heroInfoRequester = remember { FocusRequester() }

    val pillRequesters = remember { List(4) { FocusRequester() } }

    val featuredMovie = remember(visibleMovies) {
        val pool = visibleMovies.filter { !it.name.isBlank() }
        val withBackdrop = pool.filter { !it.backdropPath.isNullOrBlank() }
        val topCandidates = withBackdrop.filter { it.rating >= 6.0 || (it.plot?.length ?: 0) > 40 }
        when {
            topCandidates.isNotEmpty() -> topCandidates.shuffled().first()
            withBackdrop.isNotEmpty() -> withBackdrop.shuffled().first()
            pool.isNotEmpty() -> pool.shuffled().first()
            else -> null
        }
    }
    val featuredSeries = remember(visibleSeriesList) {
        visibleSeriesList.firstOrNull { !it.backdropPath.isNullOrBlank() || !it.cover.isNullOrBlank() }
            ?: visibleSeriesList.firstOrNull()
    }

    LaunchedEffect(Unit) {
        try {
            heroBtnRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val headerFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        // ══════════════════════════════════════════════════════════════════════
        // 0. CABECERA NATIVA TV: ICONO CIRCULAR + LELOUCH PLUS EN LETRAS + BADGE D-PAD
        // ══════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icono Circular estilizado Lelouch
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFF6B00), Color(0xFFD9480F))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "L",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LELOUCH",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PLUS",
                            color = Color(0xFFFF6B00), // Naranja Neón Bix Plus
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PELICULAS, SERIES, TV EN VIVO",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // Botones Circulares de Acceso Rápido: Lupa (Buscar) y Engranaje (Ajustes)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Lupa (Buscar)
                Surface(
                    onClick = onNavigateToSearch,
                    modifier = Modifier
                        .size(44.dp)
                        .focusRequester(headerFocusRequester)
                        .focusProperties {
                            right = settingsFocusRequester
                            down = heroBtnRequester
                        },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF1A1C23),
                        focusedContainerColor = Color(0xFFFF6B00)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0xFF334155))),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    )
                ) {
                    var isBtnFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { isBtnFocused = it.isFocused },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔍",
                            fontSize = 18.sp
                        )
                    }
                }

                // 2. Engranaje (Ajustes & Opciones)
                Surface(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .focusRequester(settingsFocusRequester)
                        .focusProperties {
                            left = headerFocusRequester
                            down = heroBtnRequester
                        },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF1A1C23),
                        focusedContainerColor = Color(0xFFFF6B00)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0xFF334155))),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    )
                ) {
                    var isBtnFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { isBtnFocused = it.isFocused },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚙️",
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // 1. HERO FEATURED BANNER TV (CINEMÁTICO CON D-PAD)
        // ══════════════════════════════════════════════════════════════════════
        if (featuredMovie != null) {
            TvHeroBanner(
                title = featuredMovie.name,
                subtitle = featuredMovie.genre?.ifBlank { null } ?: featuredMovie.categoryName.ifBlank { "Película Destacada" },
                rating = featuredMovie.rating,
                year = featuredMovie.year,
                plot = featuredMovie.plot,
                imageUrl = featuredMovie.backdropPath?.ifBlank { null } ?: featuredMovie.streamIcon,
                onPlayClick = { onMovieClick?.invoke(featuredMovie) ?: onNavigateToMovies() },
                onInfoClick = { onMovieClick?.invoke(featuredMovie) ?: onNavigateToMovies() },
                playFocusRequester = heroBtnRequester,
                infoFocusRequester = heroInfoRequester,
                sidebarRequester = sidebarRequester,
                downRequester = pillRequesters[0],
                upRequester = headerFocusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )
        } else if (featuredSeries != null) {
            TvHeroBanner(
                title = featuredSeries.name,
                subtitle = featuredSeries.genre?.ifBlank { null } ?: featuredSeries.categoryName.ifBlank { "Serie Destacada" },
                rating = featuredSeries.rating,
                year = featuredSeries.releaseDate?.take(4),
                plot = featuredSeries.plot,
                imageUrl = featuredSeries.backdropPath?.ifBlank { null } ?: featuredSeries.cover,
                onPlayClick = { onSeriesClick?.invoke(featuredSeries) ?: onNavigateToSeries() },
                onInfoClick = { onSeriesClick?.invoke(featuredSeries) ?: onNavigateToSeries() },
                playFocusRequester = heroBtnRequester,
                infoFocusRequester = heroInfoRequester,
                sidebarRequester = sidebarRequester,
                downRequester = pillRequesters[0],
                upRequester = headerFocusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 2. PÍLDORAS DE CATEGORÍA NATIVAS CON FOCO D-PAD
        // ══════════════════════════════════════════════════════════════════════
        Text(
            text = "EXPLORAR CATÁLOGO",
            color = LelouchTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvCategoryPill(
                icon = "📺",
                label = "En Vivo",
                count = liveChannelsCount,
                onClick = onNavigateToLive,
                focusRequester = pillRequesters[0],
                modifier = Modifier.weight(1f).focusProperties {
                    left = sidebarRequester ?: FocusRequester.Cancel
                    right = pillRequesters[1]
                    up = heroBtnRequester
                }
            )
            TvCategoryPill(
                icon = "🎬",
                label = "Películas",
                count = moviesCount,
                onClick = onNavigateToMovies,
                focusRequester = pillRequesters[1],
                modifier = Modifier.weight(1f).focusProperties {
                    left = pillRequesters[0]
                    right = pillRequesters[2]
                    up = heroBtnRequester
                }
            )
            TvCategoryPill(
                icon = "🎞️",
                label = "Series",
                count = seriesCount,
                onClick = onNavigateToSeries,
                focusRequester = pillRequesters[2],
                modifier = Modifier.weight(1f).focusProperties {
                    left = pillRequesters[1]
                    right = pillRequesters[3]
                    up = heroInfoRequester
                }
            )
            TvCategoryPill(
                icon = "⚽",
                label = "Deportes",
                count = sportsCount,
                onClick = onNavigateToSports,
                focusRequester = pillRequesters[3],
                modifier = Modifier.weight(1f).focusProperties {
                    left = pillRequesters[2]
                    right = FocusRequester.Cancel
                    up = heroInfoRequester
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 3. CARRUSEL HORIZONTAL TV: CANALES EN VIVO & DEPORTES
        // ══════════════════════════════════════════════════════════════════════
        if (visibleChannels.isNotEmpty()) {
            TvSwimlaneHeader(
                title = "🔴 CANALES EN VIVO & DEPORTES",
                actionText = "Ver todos [>]",
                onActionClick = onNavigateToLive
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(visibleChannels.take(12)) { channel ->
                    TvStreamCard(
                        channel = channel,
                        onClick = { onChannelClick?.invoke(channel) ?: onNavigateToLive() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 4. CARRUSEL HORIZONTAL TV: PELÍCULAS DE ESTRENO
        // ══════════════════════════════════════════════════════════════════════
        if (visibleMovies.isNotEmpty()) {
            TvSwimlaneHeader(
                title = "🎬 PELÍCULAS RECOMENDADAS",
                actionText = "Ver todas [>]",
                onActionClick = onNavigateToMovies
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(visibleMovies.take(12)) { movie ->
                    TvPosterMovieCard(
                        movie = movie,
                        onClick = { onMovieClick?.invoke(movie) ?: onNavigateToMovies() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 5. CARRUSEL HORIZONTAL TV: SERIES POPULARES
        // ══════════════════════════════════════════════════════════════════════
        if (visibleSeriesList.isNotEmpty()) {
            TvSwimlaneHeader(
                title = "🎞️ SERIES POPULARES",
                actionText = "Ver todas [>]",
                onActionClick = onNavigateToSeries
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(visibleSeriesList.take(12)) { series ->
                    TvPosterSeriesCard(
                        series = series,
                        onClick = { onSeriesClick?.invoke(series) ?: onNavigateToSeries() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLES TV DE APOYO CON COMPATIBILIDAD 100% D-PAD
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvHeroBanner(
    title: String,
    subtitle: String,
    rating: Double?,
    year: String?,
    plot: String?,
    imageUrl: String?,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    playFocusRequester: FocusRequester,
    infoFocusRequester: FocusRequester,
    sidebarRequester: FocusRequester?,
    downRequester: FocusRequester?,
    upRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LelouchSurfaceVariant)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Degradado cinematográfico
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF02070D).copy(alpha = 0.95f),
                            Color(0xFF02070D).copy(alpha = 0.80f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1200f
                    )
                )
        )

        // Contenido Superpuesto
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.55f)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LelouchCyanAccent.copy(alpha = 0.2f))
                        .border(1.dp, LelouchCyanAccent.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⭐ DESTACADO TV",
                        color = LelouchCyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                if (rating != null && rating > 0.0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "★ %.1f".format(rating),
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!year.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = year,
                        color = LelouchTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                color = LelouchCyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!plot.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = plot,
                    color = LelouchTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botones D-Pad
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .focusRequester(playFocusRequester)
                        .focusProperties {
                            left = sidebarRequester ?: FocusRequester.Cancel
                            right = infoFocusRequester
                            if (downRequester != null) down = downRequester
                            if (upRequester != null) up = upRequester
                        },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = LelouchCyanAccent,
                        focusedContainerColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ver Ahora",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Surface(
                    onClick = onInfoClick,
                    modifier = Modifier
                        .focusRequester(infoFocusRequester)
                        .focusProperties {
                            left = playFocusRequester
                            if (downRequester != null) down = downRequester
                            if (upRequester != null) up = upRequester
                        },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF0B131E),
                        focusedContainerColor = LelouchSurfaceVariant
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                        focusedBorder = Border(BorderStroke(1.5.dp, LelouchCyanAccent))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Detalles",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCategoryPill(
    icon: String,
    label: String,
    count: Int,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0B131E),
            focusedContainerColor = LelouchCyanAccent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF1E293B))),
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) Color.Black.copy(alpha = 0.2f) else LelouchCyanAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (count > 999) "%,d".format(count) else "$count",
                        color = if (isFocused) Color.Black else LelouchCyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSwimlaneHeader(
    title: String,
    actionText: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Text(
            text = actionText,
            color = LelouchCyanAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvStreamCard(
    channel: ChannelUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .height(98.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0B131E),
            focusedContainerColor = LelouchSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF1E293B))),
            focusedBorder = Border(BorderStroke(2.dp, LelouchCyanAccent))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
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
                            color = LelouchCyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LelouchLiveRed)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.categoryName.ifBlank { "Canal en Vivo" },
                    color = LelouchTextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPosterMovieCard(
    movie: MovieUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(120.dp)
            .height(180.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0B131E)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF1E293B))),
            focusedBorder = Border(BorderStroke(2.dp, LelouchCyanAccent))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!movie.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = movie.streamIcon,
                    contentDescription = movie.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(LelouchSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = movie.name.take(2).uppercase(),
                        color = LelouchCyanAccent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            val rating = movie.rating
            if (rating > 0.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ %.1f".format(rating),
                        color = Color(0xFFFFD700),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = movie.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val year = movie.year
                if (!year.isNullOrBlank()) {
                    Text(
                        text = year,
                        color = LelouchTextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPosterSeriesCard(
    series: SeriesUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(120.dp)
            .height(180.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0B131E)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF1E293B))),
            focusedBorder = Border(BorderStroke(2.dp, LelouchCyanAccent))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!series.cover.isNullOrBlank()) {
                AsyncImage(
                    model = series.cover,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(LelouchSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = series.name.take(2).uppercase(),
                        color = LelouchCyanAccent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (series.seasonsCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${series.seasonsCount}T",
                        color = LelouchCyanAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = series.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
