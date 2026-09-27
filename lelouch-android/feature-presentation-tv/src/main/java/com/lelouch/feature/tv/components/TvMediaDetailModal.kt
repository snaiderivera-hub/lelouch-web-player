package com.lelouch.feature.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@Composable
fun TvMediaDetailModal(
    media: MediaDetailUiModel,
    onPlayClick: (episodeId: Int?) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSeason by remember { mutableIntStateOf(media.seasons.firstOrNull() ?: 1) }

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
                    .padding(48.dp)
            ) {
                // Fila Superior: Botón Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    var isCloseFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCloseFocused) LelouchCyanAccent else LelouchSurface)
                            .border(1.dp, if (isCloseFocused) Color.White else LelouchBorder, RoundedCornerShape(8.dp))
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

                Spacer(modifier = Modifier.height(16.dp))

                // Contenido Principal: Póster 2:3 + Ficha de Datos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Póster vertical
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(330.dp)
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
                                    text = media.title.take(2).uppercase(),
                                    color = LelouchCyanAccent,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    // Información y Sinopsis
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            color = LelouchTextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Badges: Año, Rating, Duración, Género
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (media.rating > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2A2300))
                                        .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "★ ${String.format("%.1f", media.rating)}",
                                        color = Color(0xFFFFD700),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            val metaTags = listOfNotNull(media.year, media.duration, media.genre)
                            Text(
                                text = metaTags.joinToString("  •  "),
                                color = LelouchCyanAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sinopsis
                        Text(
                            text = media.synopsis.ifBlank { "Sin sinopsis disponible para este título." },
                            color = LelouchTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!media.director.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Director: ${media.director}",
                                color = LelouchTextMuted,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Botones de Acción
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isPlayFocused) LelouchCyanAccent else Color.White)
                                    .border(2.dp, if (isPlayFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { onPlayClick(null) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "REPRODUCIR",
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            var isFavFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isFavFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isFavFocused) LelouchSurfaceVariant else LelouchSurface)
                                    .border(
                                        width = if (isFavFocused) 2.dp else 1.dp,
                                        color = if (isFavFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onToggleFavorite() }
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (media.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = if (media.isFavorite) Color(0xFFFFD700) else LelouchTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
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
                if (media.isSeries && media.seasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Temporadas y Episodios",
                        color = LelouchTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Selector de Temporadas
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        media.seasons.forEach { seasonNum ->
                            var isSeasonFocused by remember { mutableStateOf(false) }
                            val isCurrent = (seasonNum == selectedSeason)
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isSeasonFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSeasonFocused || isCurrent) LelouchCyanAccent.copy(alpha = 0.25f) else LelouchSurface)
                                    .border(
                                        width = if (isSeasonFocused) 2.dp else if (isCurrent) 1.5.dp else 0.dp,
                                        color = if (isSeasonFocused || isCurrent) LelouchCyanAccent else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedSeason = seasonNum }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Temporada $seasonNum",
                                    color = if (isSeasonFocused || isCurrent) LelouchTextPrimary else LelouchTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Riel de Episodios 16:9
                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val filteredEpisodes = media.episodes.filter { it.seasonNumber == selectedSeason }
                        itemsIndexed(filteredEpisodes) { _, ep ->
                            var isEpFocused by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(targetValue = if (isEpFocused) 1.06f else 1.0f, label = "epScale")

                            Box(
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(125.dp)
                                    .scale(scale)
                                    .onFocusChanged { isEpFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isEpFocused) LelouchCardFocused else LelouchSurface)
                                    .border(
                                        width = if (isEpFocused) 2.dp else 1.dp,
                                        color = if (isEpFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onPlayClick(ep.episodeId) }
                                    .padding(10.dp)
                            ) {
                                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                    Text(
                                        text = "E${ep.episodeNumber}: ${ep.title}",
                                        color = if (isEpFocused) LelouchCyanAccent else LelouchTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = ep.plot ?: "Sin descripción",

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
