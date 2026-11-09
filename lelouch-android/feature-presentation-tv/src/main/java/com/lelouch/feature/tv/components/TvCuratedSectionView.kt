package com.lelouch.feature.tv.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.CuratedMediaItem
import com.lelouch.core.model.CuratedSectionType
import com.lelouch.core.model.PortalFeedCalculator
import com.lelouch.core.model.Series
import com.lelouch.core.model.VodMovie

private sealed class TvCuratedItem {
    data class MovieItem(val movie: VodMovie) : TvCuratedItem()
    data class SeriesItem(val series: Series) : TvCuratedItem()
}

/**
 * FASE 36: Vista de pantalla completa para secciones curadas en Android TV (D-Pad compatible).
 * Implementa el patrón TopicNavKey / TopicViewModel para TV Box:
 * - Filtro aislado por género/sección.
 * - Subtabs navegables por control remoto: [ Todo ] [ Series ] [ Películas ].
 * - Cuadrícula de 5 columnas con foco Skia nativo sin saturar la memoria GPU.
 * - Tecla BACK del control remoto retorna al punto exacto del Dashboard.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCuratedSectionView(
    section: CuratedSectionType,
    movies: List<VodMovie>,
    seriesList: List<Series>,
    onMovieClick: (VodMovie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val backRequester = remember { FocusRequester() }

    val matchingMovies = remember(section, movies) {
        when (section) {
            CuratedSectionType.RECENT -> {
                movies.filter { !PortalFeedCalculator.isAnimation(it.name, it.categoryName, it.genre) }
                    .sortedByDescending { it.added ?: "" }
            }
            CuratedSectionType.TOP_RATED -> {
                movies.filter { (it.rating ?: 0.0) >= 6.5 }
                    .sortedByDescending { it.rating ?: 0.0 }
            }
            CuratedSectionType.HORROR -> {
                movies.filter { PortalFeedCalculator.isTerror(it.name, it.categoryName, it.genre, it.plot) }
            }
            CuratedSectionType.SCIFI -> {
                movies.filter { PortalFeedCalculator.isSciFi(it.name, it.categoryName, it.genre, it.plot) }
            }
            CuratedSectionType.TRUE_EVENTS -> {
                movies.filter { PortalFeedCalculator.isRealHistory(it.name, it.categoryName, it.genre, it.plot) }
            }
            CuratedSectionType.ACTION -> {
                movies.filter { PortalFeedCalculator.isAction(it.name, it.categoryName, it.genre, it.plot) }
            }
            CuratedSectionType.ANIMATION -> {
                movies.filter { PortalFeedCalculator.isAnimation(it.name, it.categoryName, it.genre) }
            }
        }
    }

    val matchingSeries = remember(section, seriesList) {
        if (section.isMoviesOnly) {
            emptyList()
        } else {
            when (section) {
                CuratedSectionType.RECENT -> emptyList()
                CuratedSectionType.TOP_RATED -> {
                    seriesList.filter {
                        val r = (it.rating5based?.times(2.0)) ?: (it.rating ?: 0.0)
                        r >= 6.5
                    }.sortedByDescending { (it.rating5based?.times(2.0)) ?: (it.rating ?: 0.0) }
                }
                CuratedSectionType.HORROR -> {
                    seriesList.filter { PortalFeedCalculator.isTerror(it.name, it.categoryName ?: "", it.genre, it.plot) }
                }
                CuratedSectionType.SCIFI -> {
                    seriesList.filter { PortalFeedCalculator.isSciFi(it.name, it.categoryName ?: "", it.genre, it.plot) }
                }
                CuratedSectionType.TRUE_EVENTS -> {
                    seriesList.filter { PortalFeedCalculator.isRealHistory(it.name, it.categoryName ?: "", it.genre, it.plot) }
                }
                CuratedSectionType.ACTION -> {
                    seriesList.filter { PortalFeedCalculator.isAction(it.name, it.categoryName ?: "", it.genre, it.plot) }
                }
                CuratedSectionType.ANIMATION -> {
                    seriesList.filter { PortalFeedCalculator.isAnimation(it.name, it.categoryName ?: "", it.genre) }
                }
            }
        }
    }

    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Todo, 1: Series, 2: Películas

    val displayItems: List<TvCuratedItem> = remember(selectedFilter, matchingMovies, matchingSeries) {
        if (section.isMoviesOnly) {
            matchingMovies.map { TvCuratedItem.MovieItem(it) }
        } else {
            when (selectedFilter) {
                1 -> matchingSeries.map { TvCuratedItem.SeriesItem(it) }
                2 -> matchingMovies.map { TvCuratedItem.MovieItem(it) }
                else -> {
                    val combined = matchingMovies.map { TvCuratedItem.MovieItem(it) } +
                                   matchingSeries.map { TvCuratedItem.SeriesItem(it) }
                    when (section) {
                        CuratedSectionType.TOP_RATED -> combined.sortedByDescending {
                            when (it) {
                                is TvCuratedItem.MovieItem -> it.movie.rating ?: 0.0
                                is TvCuratedItem.SeriesItem -> (it.series.rating5based?.times(2.0)) ?: (it.series.rating ?: 0.0)
                            }
                        }
                        else -> combined
                    }
                }
            }
        }
    }

    val barColor = Color(section.barColorHex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B11))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Cabecera con botón Volver, indicador y subtabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var isBackFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backRequester)
                        .onFocusChanged { isBackFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF1E293B),
                        focusedContainerColor = barColor
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0xFF334155))),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    )
                ) {
                    Text(
                        text = "◀ VOLVER",
                        color = if (isBackFocused) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )

                Text(
                    text = section.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor.copy(alpha = 0.2f))
                        .border(1.dp, barColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${displayItems.size} TÍTULOS",
                        color = barColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Subtabs D-Pad (Todo / Series / Películas)
            if (!section.isMoviesOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tabs = listOf(
                        "Todo (${matchingMovies.size + matchingSeries.size})",
                        "Series (${matchingSeries.size})",
                        "Películas (${matchingMovies.size})"
                    )
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedFilter == index
                        var isPillFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { selectedFilter = index },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) barColor.copy(alpha = 0.3f) else Color(0xFF131822),
                                focusedContainerColor = barColor.copy(alpha = 0.6f)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, if (isSelected) barColor else Color(0xFF1E293B))),
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            )
                        ) {
                            Text(
                                text = title,
                                color = if (isPillFocused || isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .onFocusChanged { isPillFocused = it.isFocused }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Grilla D-Pad de contenido
        if (displayItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No se encontraron títulos disponibles en esta sección.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    displayItems,
                    key = { item ->
                        when (item) {
                            is TvCuratedItem.MovieItem -> "curated_mov_${item.movie.id}_${item.movie.streamId}"
                            is TvCuratedItem.SeriesItem -> "curated_ser_${item.series.id}_${item.series.seriesId}"
                        }
                    }
                ) { item ->
                    when (item) {
                        is TvCuratedItem.MovieItem -> {
                            TvCuratedPosterCard(
                                title = item.movie.name,
                                imageUrl = item.movie.streamIcon,
                                rating = item.movie.rating ?: 0.0,
                                year = item.movie.year,
                                isSeries = false,
                                onClick = { onMovieClick(item.movie) }
                            )
                        }
                        is TvCuratedItem.SeriesItem -> {
                            TvCuratedPosterCard(
                                title = item.series.name,
                                imageUrl = item.series.cover,
                                rating = (item.series.rating5based?.times(2.0)) ?: (item.series.rating ?: 0.0),
                                year = item.series.releaseDate?.take(4),
                                isSeries = true,
                                onClick = { onSeriesClick(item.series) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCuratedPosterCard(
    title: String,
    imageUrl: String?,
    rating: Double,
    year: String?,
    isSeries: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0F172A)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF1E293B))),
            focusedBorder = Border(BorderStroke(2.dp, LelouchCyanAccent))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!imageUrl.isNullOrBlank()) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(imageUrl) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(false)
                            .size(300, 450)
                            .build()
                    },
                    contentDescription = title,
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LelouchSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = if (isSeries) "🎞️" else "🎬",
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title.take(14),
                            color = LelouchCyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Badge de rating
            if (rating > 0.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(0.5.dp, Color(0xFFFFB800).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ %.1f".format(rating),
                        color = Color(0xFFFFD700),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Badge de tipo (SERIE)
            if (isSeries) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.9f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SERIE",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!year.isNullOrBlank()) {
                    Text(
                        text = year,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

