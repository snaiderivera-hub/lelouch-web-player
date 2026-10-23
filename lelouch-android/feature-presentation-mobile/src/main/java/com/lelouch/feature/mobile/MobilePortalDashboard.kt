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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/**
 * Feed de Inicio Móvil 100% Fiel a la Captura 3 de Bix Plus:
 * 1. Fondo degradé cálido tenue rojizo/ámbar superior (#2C0B05 -> #160603 -> #090A0E).
 * 2. Cabecera superior con Logo LELOUCH PLUS, subtítulo en mayúsculas y botón circular TV.
 * 3. Barra de búsqueda integrada tipo píldora ovalada.
 * 4. Sección EXPLORAR con píldoras horizontales redondeadas [ 🎬 PELÍCULAS ] [ 🎞️ SERIES ] [ 📺 EN VIVO ].
 * 5. Sección LO MAS POPULAR con Hero Card destacado, badges dorados IMDb y botón circular Play grande.
 * 6. Carruseles horizontales de En Vivo & Deportes, Series Populares y Películas.
 * 7. Eliminada la tarjeta inferior redundante de LionTV.
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
    onSearchClick: () -> Unit = onNavigateToMovies,
    modifier: Modifier = Modifier,
    liveChannels: List<LiveStream> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    seriesList: List<Series> = emptyList(),
    onChannelClick: (String, String) -> Unit = { _, _ -> },
    onMovieClick: (VodMovie) -> Unit = {},
    onSeriesClick: (Series) -> Unit = {}
) {
    // Selección dinámica del ítem destacado para el Hero Banner
    val featuredMovie = remember(movies) {
        movies.firstOrNull { !it.backdropPath.isNullOrBlank() || !it.streamIcon.isNullOrBlank() }
            ?: movies.firstOrNull()
    }
    val featuredSeries = remember(seriesList) {
        seriesList.firstOrNull { !it.backdropPath.isNullOrBlank() || !it.cover.isNullOrBlank() }
            ?: seriesList.firstOrNull()
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
        // 1. CABECERA SUPERIOR ESTILO BIX PLUS (CAPTURA 3)
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
                    Text(
                        text = "PELICULAS, SERIES, TV EN VIVO",
                        color = BixTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
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
        // 2. BARRA DE BÚSQUEDA INTEGRADA TIPO PÍLDORA (CAPTURA 3)
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
                    text = "Buscar...",
                    color = BixTextMuted,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 3. SECCIÓN EXPLORAR POR CATEGORÍA (CAPTURA 3)
        // ══════════════════════════════════════════════════════════════════════
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "EXPLORAR",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Explorar por categoria",
                color = BixTextMuted,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3 Píldoras Horizontales Redondeadas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BixExplorePill(
                icon = Icons.Default.Movie,
                label = "PELÍCULAS",
                onClick = onNavigateToMovies,
                modifier = Modifier.weight(1f)
            )
            BixExplorePill(
                icon = Icons.Default.Tv,
                label = "SERIES",
                onClick = onNavigateToSeries,
                modifier = Modifier.weight(1f)
            )
            BixExplorePill(
                icon = Icons.Default.LiveTv,
                label = "EN VIVO",
                onClick = onNavigateToLive,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 4. SECCIÓN LO MAS POPULAR (HERO CARD BIX PLUS - CAPTURA 3)
        // ══════════════════════════════════════════════════════════════════════
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "LO MAS POPULAR",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Top de películas mas votadas",
                color = BixTextMuted,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (featuredMovie != null) {
            BixHeroMovieCard(
                movie = featuredMovie,
                onPlayClick = { onMovieClick(featuredMovie) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        } else if (featuredSeries != null) {
            BixHeroSeriesCard(
                series = featuredSeries,
                onPlayClick = { onSeriesClick(featuredSeries) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 5. CARRUSEL HORIZONTAL: EN VIVO & DEPORTES
        // ══════════════════════════════════════════════════════════════════════
        if (liveChannels.isNotEmpty()) {
            SwimlaneHeader(
                title = "En Vivo & Deportes",
                actionText = "Ver todos >",
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

            Spacer(modifier = Modifier.height(22.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 6. CARRUSEL HORIZONTAL: SERIES POPULARES
        // ══════════════════════════════════════════════════════════════════════
        if (seriesList.isNotEmpty()) {
            SwimlaneHeader(
                title = "Series Populares",
                actionText = "Ver todas >",
                onActionClick = onNavigateToSeries
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(seriesList.take(15)) { series ->
                    val seasonText = if (series.seasonsCount > 0) "${series.seasonsCount} Temp" else null
                    NativePosterCard(
                        title = series.name,
                        imageUrl = series.cover,
                        rating = series.rating,
                        subtitle = seasonText,
                        onClick = { onSeriesClick(series) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 7. CARRUSEL HORIZONTAL: PELÍCULAS RECOMENDADAS
        // ══════════════════════════════════════════════════════════════════════
        if (movies.isNotEmpty()) {
            SwimlaneHeader(
                title = "Películas Recomendadas",
                actionText = "Ver todas >",
                onActionClick = onNavigateToMovies
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(movies.take(15)) { movie ->
                    NativePosterCard(
                        title = movie.name,
                        imageUrl = movie.streamIcon,
                        rating = movie.rating,
                        subtitle = movie.year,
                        onClick = { onMovieClick(movie) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLES VISUALES NATIVOS DE APOYO ESTILO BIX PLUS
// ─────────────────────────────────────────────────────────────────────────────

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
private fun BixHeroMovieCard(
    movie: VodMovie,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = movie.backdropPath?.ifBlank { null } ?: movie.streamIcon
    Box(
        modifier = modifier
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF16171F))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable(onClick = onPlayClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradiente oscuro inferior y superior
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                            Color(0xFF090A0E).copy(alpha = 0.85f),
                            Color(0xFF090A0E)
                        )
                    )
                )
        )

        // Botón circular grande de Play a la derecha inferior
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF262836).copy(alpha = 0.95f))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable(onClick = onPlayClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Reproducir",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // Información de texto
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = movie.name,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Badges: IMDb, Año, Duración
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val rating = movie.rating ?: 0.0
                if (rating > 0.0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE5A93C))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "IMDb %.1f".format(rating),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (!movie.year.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = movie.year!!,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val genre = movie.genre?.ifBlank { null } ?: movie.categoryName.ifBlank { null }
                if (!genre.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = genre.take(12),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            if (!movie.plot.isNullOrBlank()) {
                Text(
                    text = movie.plot!!,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BixHeroSeriesCard(
    series: Series,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = series.backdropPath?.ifBlank { null } ?: series.cover
    Box(
        modifier = modifier
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF16171F))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable(onClick = onPlayClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                            Color(0xFF090A0E).copy(alpha = 0.85f),
                            Color(0xFF090A0E)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF262836).copy(alpha = 0.95f))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable(onClick = onPlayClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Reproducir",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = series.name,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val rating = series.rating5based ?: 0.0
                if (rating > 0.0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE5A93C))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ %.1f".format(rating),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (series.seasonsCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${series.seasonsCount} Temp",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!series.plot.isNullOrBlank()) {
                Text(
                    text = series.plot!!,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SwimlaneHeader(
    title: String,
    actionText: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
        TextButton(
            onClick = onActionClick,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text(
                text = actionText,
                color = BixOrangeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
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
                        color = BixOrangeAccent,
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

@Composable
private fun NativePosterCard(
    title: String,
    imageUrl: String?,
    rating: Double?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(115.dp)
            .height(175.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14161F))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1F2B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.take(2).uppercase(),
                    color = BixOrangeAccent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Rating
        if (rating != null && rating > 0.0) {
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

        // Información al pie
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = BixTextMuted,
                    fontSize = 9.sp
                )
            }
        }
    }
}
