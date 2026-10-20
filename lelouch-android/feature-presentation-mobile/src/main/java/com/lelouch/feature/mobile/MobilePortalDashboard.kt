package com.lelouch.feature.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Experiencia 100% Nativa de Streaming Móvil (Material 3 & Jetpack Compose).
 * Reemplaza el dashboard estático tipo web por un feed dinámico:
 * 1. Hero Featured Banner (Póster/Backdrop con reproducción directa).
 * 2. Píldoras de Acceso Rápido con contadores en tiempo real.
 * 3. Carruseles Horizontales de Contenido (En Vivo, Películas y Series).
 * 4. Tarjeta de Estado y Gestión de Cuenta nativa.
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
            .background(LelouchBackground)
    ) {
        // ══════════════════════════════════════════════════════════════════════
        // 1. HERO FEATURED BANNER (ESTILO STREAMING NATIVO)
        // ══════════════════════════════════════════════════════════════════════
        if (featuredMovie != null) {
            MobileHeroBanner(
                title = featuredMovie.name,
                subtitle = featuredMovie.genre?.ifBlank { null } ?: featuredMovie.categoryName.ifBlank { "Película Destacada" },
                rating = featuredMovie.rating,
                imageUrl = featuredMovie.backdropPath?.ifBlank { null } ?: featuredMovie.streamIcon,
                onPlayClick = { onMovieClick(featuredMovie) },
                onInfoClick = { onMovieClick(featuredMovie) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else if (featuredSeries != null) {
            MobileHeroBanner(
                title = featuredSeries.name,
                subtitle = featuredSeries.genre?.ifBlank { null } ?: featuredSeries.categoryName.ifBlank { "Serie Destacada" },
                rating = featuredSeries.rating,
                imageUrl = featuredSeries.backdropPath?.ifBlank { null } ?: featuredSeries.cover,
                onPlayClick = { onSeriesClick(featuredSeries) },
                onInfoClick = { onSeriesClick(featuredSeries) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 2. PÍLDORAS DE ACCESO RÁPIDO CON CONTADORES NEÓN
        // ══════════════════════════════════════════════════════════════════════
        Text(
            text = "EXPLORAR CATEGORÍAS",
            color = LelouchTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                NativeCategoryPill(
                    icon = "📺",
                    label = "En Vivo",
                    count = liveChannelsCount,
                    onClick = onNavigateToLive
                )
            }
            item {
                NativeCategoryPill(
                    icon = "🎬",
                    label = "Películas",
                    count = moviesCount,
                    onClick = onNavigateToMovies
                )
            }
            item {
                NativeCategoryPill(
                    icon = "🎞️",
                    label = "Series",
                    count = seriesCount,
                    onClick = onNavigateToSeries
                )
            }
            item {
                NativeCategoryPill(
                    icon = "⚽",
                    label = "Deportes",
                    count = sportsCount,
                    onClick = onNavigateToSports
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 3. CARRUSEL HORIZONTAL: EN VIVO & DEPORTES
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

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 4. CARRUSEL HORIZONTAL: PELÍCULAS DE ESTRENO
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

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 5. CARRUSEL HORIZONTAL: SERIES POPULARES
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

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ══════════════════════════════════════════════════════════════════════
        // 6. TARJETA NATIVA DE ESTADO Y GESTIÓN DE CUENTA (FOOTER)
        // ══════════════════════════════════════════════════════════════════════
        NativeAccountStatusCard(
            activeSource = activeSource,
            onReloadCatalog = onReloadCatalog,
            onOpenSettings = onOpenSettings,
            onDownloadM3U = onDownloadM3U,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLES VISUALES NATIVOS DE APOYO
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MobileHeroBanner(
    title: String,
    subtitle: String,
    rating: Double?,
    imageUrl: String?,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(210.dp)
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

        // Degradado fluido estilo streaming
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color(0xFF02070D).copy(alpha = 0.85f),
                            Color(0xFF02070D)
                        )
                    )
                )
        )

        // Contenido superpuesto
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Badge Destacado
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LelouchCyanAccent.copy(alpha = 0.2f))
                        .border(1.dp, LelouchCyanAccent.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⭐ DESTACADO",
                        color = LelouchCyanAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
                if (rating != null && rating > 0.0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "★ %.1f".format(rating),
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                color = LelouchTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Botones de acción táctiles
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LelouchCyanAccent,
                        contentColor = Color(0xFF02070D)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ver Ahora",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = onInfoClick,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Detalles",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Detalles",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeCategoryPill(
    icon: String,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(LelouchSurfaceVariant)
            .border(1.dp, LelouchCyanAccent.copy(alpha = 0.3f), RoundedCornerShape(19.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(LelouchCyanAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (count > 999) "%,d".format(count) else "$count",
                        color = LelouchCyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
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
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.3.sp
        )
        TextButton(
            onClick = onActionClick,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text(
                text = actionText,
                color = LelouchCyanAccent,
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
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LelouchSurfaceVariant)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
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
                        color = LelouchCyanAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(LelouchLiveRed)
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
                color = LelouchTextMuted,
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
            .clip(RoundedCornerShape(12.dp))
            .background(LelouchSurface)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
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
                    .background(LelouchSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.take(2).uppercase(),
                    color = LelouchCyanAccent,
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
                .padding(6.dp)
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
                    color = LelouchTextSecondary,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun NativeAccountStatusCard(
    activeSource: SourceConfig?,
    onReloadCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onDownloadM3U: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LelouchSurfaceVariant)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Servidor Activo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(LelouchSuccess)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeSource?.name ?: "Servidor Conectado",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val expiryText = activeSource?.formattedExpireDate ?: "13/10/2026"
                Text(
                    text = "Vence: $expiryText",
                    color = LelouchTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botones de acción inferior
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReloadCatalog,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, LelouchCyanAccent.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LelouchCyanAccent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recargar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajustes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onDownloadM3U,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text("📄 M3U", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
