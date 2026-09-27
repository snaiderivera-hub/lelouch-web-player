package com.lelouch.feature.tv.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.Episode

data class MediaDetailUiModel(
    val id: Int,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val synopsis: String = "",
    val rating: Double = 0.0,
    val year: String? = null,
    val duration: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val isSeries: Boolean = false,
    val isFavorite: Boolean = false,
    val seasons: List<Int> = emptyList(),
    val episodes: List<Episode> = emptyList()
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvMediaDetailModal(
    media: MediaDetailUiModel,
    onPlayClick: (episodeId: Int?) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSeason by remember(media.id, media.seasons) {
        mutableIntStateOf(media.seasons.firstOrNull() ?: 1)
    }

    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        playFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LelouchBackground.copy(alpha = 0.95f))
        ) {
            // Fondo cinematográfico con Backdrop
            if (!media.backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = media.backdropUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.35f),
                    contentScale = ContentScale.Crop
                )
            }

            // Vignette Oscura para contraste y legibilidad
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, LelouchBackground.copy(alpha = 0.95f)),
                            radius = 1200f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                // Fila Superior: Botón Cerrar (Compatible con mando Xiaomi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    var isCloseFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .focusable()
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCloseFocused) Color.White else LelouchSurface)
                            .border(if (isCloseFocused) 2.dp else 1.dp, if (isCloseFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = if (isCloseFocused) Color.Black else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contenido Principal: Póster 2:3 + Ficha de Datos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Póster vertical
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(290.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(2.dp, LelouchCyanAccent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    ) {
                        if (!media.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = media.posterUrl,
                                contentDescription = media.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(LelouchSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = media.title.take(3).uppercase(),
                                    color = LelouchCyanAccent,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Metadatos y Sinopsis
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            color = LelouchTextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Etiquetas: Año, Calificación, Género, Duración
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (media.rating > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE5A00D))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "★ ${String.format("%.1f", media.rating)}",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (!media.year.isNullOrBlank()) {
                                Text(
                                    text = media.year,
                                    color = LelouchTextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            if (!media.genre.isNullOrBlank()) {
                                Text(
                                    text = "•  ${media.genre}",
                                    color = LelouchCyanAccent,
                                    fontSize = 13.sp
                                )
                            }

                            if (media.isSeries && media.seasons.isNotEmpty()) {
                                Text(
                                    text = "•  ${media.seasons.size} Temporadas",
                                    color = LelouchTextSecondary,
                                    fontSize = 13.sp
                                )
                            } else if (!media.duration.isNullOrBlank()) {
                                Text(
                                    text = "•  ${media.duration}",
                                    color = LelouchTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sinopsis
                        Text(
                            text = media.synopsis.ifEmpty { "Información de la producción en catálogo streaming." },
                            color = LelouchTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!media.director.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Director: ${media.director}",
                                color = LelouchTextMuted,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Botones de Acción (Ambos con .focusable() para mando Xiaomi)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .focusRequester(playFocusRequester)
                                    .focusable()
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isPlayFocused) Color.White else LelouchCyanAccent)
                                    .border(2.dp, if (isPlayFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { onPlayClick(null) }
                                    .padding(horizontal = 22.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (media.isSeries) "VER SERIE (OK)" else "REPRODUCIR (OK)",
                                        color = Color.Black,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            var isFavFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .focusable()
                                    .onFocusChanged { isFavFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isFavFocused) LelouchCardFocused else LelouchSurface)
                                    .border(
                                        width = if (isFavFocused) 2.dp else 1.dp,
                                        color = if (isFavFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onToggleFavorite() }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (media.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = if (media.isFavorite) Color(0xFFFFD700) else LelouchTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (media.isFavorite) "En Favoritos" else "Añadir a Favoritos",
                                        color = LelouchTextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Sección Especial para Series: Selector de Temporadas y Capítulos 16:9
                if (media.isSeries) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "📺 Temporadas y Episodios",
                        color = LelouchTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val seasonsToShow = if (media.seasons.isNotEmpty()) media.seasons else listOf(1)

                    // Selector de Temporadas con foco para mando TV
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        seasonsToShow.forEach { seasonNum ->
                            var isSeasonFocused by remember { mutableStateOf(false) }
                            val isCurrent = (seasonNum == selectedSeason)
                            Box(
                                modifier = Modifier
                                    .focusable()
                                    .onFocusChanged { isSeasonFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSeasonFocused -> Color.White
                                            isCurrent -> LelouchCyanAccent.copy(alpha = 0.35f)
                                            else -> LelouchSurface
                                        }
                                    )
                                    .border(
                                        width = if (isSeasonFocused) 2.dp else if (isCurrent) 1.5.dp else 1.dp,
                                        color = if (isSeasonFocused) Color.White else if (isCurrent) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedSeason = seasonNum }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Temporada $seasonNum",
                                    color = if (isSeasonFocused) Color.Black else if (isCurrent) LelouchCyanAccent else LelouchTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent || isSeasonFocused) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    val filteredEpisodes = remember(media.episodes, selectedSeason) {
                        val eps = media.episodes.filter { it.seasonNumber == selectedSeason }
                        if (eps.isNotEmpty()) eps
                        else {
                            // Fallback dinámico si aún están cargando o no vinieron indexados
                            media.episodes.ifEmpty {
                                (1..6).map { num ->
                                    Episode(
                                        id = "${media.id}-$selectedSeason-$num",
                                        episodeId = (media.id * 100) + num,
                                        seriesId = media.id,
                                        seasonNumber = selectedSeason,
                                        episodeNumber = num,
                                        title = "Episodio $num",
                                        plot = "Capítulo $num de la Temporada $selectedSeason.",
                                        durationFormatted = "45m"
                                    )
                                }
                            }
                        }
                    }

                    // Riel de Episodios 16:9 con foco navegable D-Pad y restauración
                    TvLazyRow(
                        modifier = Modifier.focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(
                            items = filteredEpisodes,
                            key = { _, ep -> "ep_${ep.id}" }
                        ) { _, ep ->
                            var isEpFocused by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(
                                targetValue = if (isEpFocused) 1.06f else 1.0f,
                                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                                label = "epScale"
                            )

                            Box(
                                modifier = Modifier
                                    .width(235.dp)
                                    .height(125.dp)
                                    .scale(scale)
                                    .focusable()
                                    .onFocusChanged { isEpFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isEpFocused) LelouchCardFocused else LelouchSurface)
                                    .border(
                                        width = if (isEpFocused) 2.5.dp else 1.dp,
                                        color = if (isEpFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onPlayClick(ep.episodeId) }
                                    .padding(10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isEpFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "E${ep.episodeNumber}",
                                                color = if (isEpFocused) Color.Black else LelouchCyanAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (ep.durationFormatted.isNotBlank()) {
                                                Text(
                                                    text = ep.durationFormatted,
                                                    color = LelouchTextSecondary,
                                                    fontSize = 10.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            if (isEpFocused) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = LelouchCyanAccent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = ep.title,
                                            color = if (isEpFocused) LelouchCyanAccent else LelouchTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = ep.plot ?: "Capítulo ${ep.episodeNumber}",
                                            color = LelouchTextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
