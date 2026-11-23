package com.lelouch.feature.tv.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import kotlinx.coroutines.delay
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
    val cast: String? = null,
    val containerExtension: String = "mp4",
    val streamUrl: String = "",
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

    LaunchedEffect(media.seasons, media.episodes) {
        val availableSeasons = if (media.episodes.isNotEmpty()) {
            media.episodes.map { it.seasonNumber }.distinct().sorted()
        } else {
            media.seasons
        }
        if (availableSeasons.isNotEmpty() && selectedSeason !in availableSeasons) {
            selectedSeason = availableSeasons.first()
        }
    }

    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            playFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090A0E))
        ) {
            // Fondo cinematográfico full-bleed con Backdrop HD
            val heroBgUrl = media.backdropUrl?.takeIf { it.isNotBlank() } ?: media.posterUrl
            if (!heroBgUrl.isNullOrBlank()) {
                AsyncImage(
                    model = heroBgUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.42f),
                    contentScale = ContentScale.Crop
                )
            }

            // Viñeta oscura cinematográfica estilo móvil unificado
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x99090A0E),
                                Color(0xCC090A0E),
                                Color(0xFF090A0E)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                // Botones flotantes D-Pad sobre el Hero (Atrás izquierda · Compartir & Favoritos derecha)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp),
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape, focusedShape = CircleShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xCC14161F),
                            focusedContainerColor = Color.White
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(1.dp, Color(0xFF252938))),
                            focusedBorder = Border(BorderStroke(2.dp, BixOrangeAccent))
                        )
                    ) {
                        var isBackFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { isBackFocused = it.isFocused },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Atrás",
                                tint = if (isBackFocused) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            onClick = {
                                val sharePayload = buildString {
                                    append(media.title)
                                    if (!media.year.isNullOrBlank()) append(" (${media.year})")
                                    if (!media.streamUrl.isNullOrBlank()) append("\n${media.streamUrl}")
                                }
                                clipboardManager.setText(AnnotatedString(sharePayload))
                                android.widget.Toast.makeText(context, "Enlace copiado", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(44.dp),
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape, focusedShape = CircleShape),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xCC14161F),
                                focusedContainerColor = BixOrangeAccent
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, Color(0xFF252938))),
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            )
                        ) {
                            var isShareFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onFocusChanged { isShareFocused = it.isFocused },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Compartir",
                                    tint = if (isShareFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(44.dp),
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape, focusedShape = CircleShape),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (media.isFavorite) Color(0x40FF4081) else Color(0xCC14161F),
                                focusedContainerColor = if (media.isFavorite) Color(0xFFFF4081) else BixOrangeAccent
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, if (media.isFavorite) Color(0xFFFF4081) else Color(0xFF252938))),
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            )
                        ) {
                            var isFavFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onFocusChanged { isFavFocused = it.isFocused },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (media.isFavorite) "En Favoritos" else "Favorito",
                                    tint = if (isFavFocused) Color.Black else if (media.isFavorite) Color(0xFFFF4081) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Contenido Principal Unificado: Póster 2:3 + Metadatos & Acción
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Póster vertical
                    Box(
                        modifier = Modifier
                            .width(195.dp)
                            .height(285.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(2.dp, BixOrangeAccent.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
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
                                    .background(Color(0xFF14161F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = media.title.take(3).uppercase(),
                                    color = BixOrangeAccent,
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
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Etiquetas: Calificación, Año, Género, Duración/Temporadas
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (media.rating > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BixOrangeAccent)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "★ ${String.format("%.1f", media.rating)}",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            if (!media.year.isNullOrBlank()) {
                                Text(
                                    text = media.year,
                                    color = Color(0xFFB3B8C8),
                                    fontSize = 13.sp
                                )
                            }

                            if (!media.genre.isNullOrBlank()) {
                                Text(
                                    text = "•  ${media.genre}",
                                    color = BixOrangeAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (media.isSeries && media.seasons.isNotEmpty()) {
                                Text(
                                    text = "•  ${media.seasons.size} Temporadas",
                                    color = Color(0xFFB3B8C8),
                                    fontSize = 13.sp
                                )
                            } else if (!media.duration.isNullOrBlank()) {
                                Text(
                                    text = "•  ${media.duration}",
                                    color = Color(0xFFB3B8C8),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = media.synopsis.ifEmpty { "Información de la producción en catálogo streaming." },
                            color = Color(0xFFD1D5DB),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!media.director.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Director: ${media.director}",
                                color = BixOrangeAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!media.cast.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reparto: ${media.cast}",
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // CTA Principal Naranja (#FF6B00)
                        Surface(
                            onClick = { onPlayClick(null) },
                            modifier = Modifier.focusRequester(playFocusRequester),
                            shape = ClickableSurfaceDefaults.shape(
                                shape = RoundedCornerShape(10.dp),
                                focusedShape = RoundedCornerShape(10.dp)
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = BixOrangeAccent,
                                focusedContainerColor = Color.White
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, Color.Transparent)),
                                focusedBorder = Border(BorderStroke(2.dp, BixOrangeAccent))
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val playLabel = if (media.isSeries) {
                                    val firstEp = media.episodes.firstOrNull { it.seasonNumber == selectedSeason } ?: media.episodes.firstOrNull()
                                    if (firstEp != null) "REPRODUCIR T${firstEp.seasonNumber} E${firstEp.episodeNumber}" else "VER SERIE"
                                } else "REPRODUCIR"
                                Text(
                                    text = playLabel,
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Temporadas y Episodios con acentos naranja
                if (media.isSeries) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Temporadas y Episodios",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val seasonsToShow = if (media.episodes.isNotEmpty()) {
                        media.episodes.map { it.seasonNumber }.distinct().sorted()
                    } else if (media.seasons.isNotEmpty()) media.seasons else listOf(1)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        seasonsToShow.forEach { seasonNum ->
                            val isCurrent = (seasonNum == selectedSeason)
                            Surface(
                                onClick = { selectedSeason = seasonNum },
                                shape = ClickableSurfaceDefaults.shape(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedShape = RoundedCornerShape(8.dp)
                                ),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isCurrent) BixOrangeAccent else Color(0xFF14161F),
                                    focusedContainerColor = Color.White
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(BorderStroke(
                                        width = 1.dp,
                                        color = if (isCurrent) BixOrangeAccent else Color(0xFF252938)
                                    )),
                                    focusedBorder = Border(BorderStroke(2.dp, BixOrangeAccent))
                                )
                            ) {
                                var isSeasonFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .onFocusChanged { isSeasonFocused = it.isFocused }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "Temporada $seasonNum",
                                        color = if (isSeasonFocused || isCurrent) Color.Black else Color(0xFFD1D5DB),
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent || isSeasonFocused) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    val filteredEpisodes = remember(media.episodes, selectedSeason) {
                        val eps = media.episodes.filter { it.seasonNumber == selectedSeason }
                        if (eps.isNotEmpty()) eps
                        else {
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
                            Surface(
                                onClick = { onPlayClick(ep.episodeId) },
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(126.dp)
                                    .onFocusChanged { isEpFocused = it.isFocused },
                                shape = ClickableSurfaceDefaults.shape(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedShape = RoundedCornerShape(10.dp)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF14161F),
                                    focusedContainerColor = Color(0xFF1F2330)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(BorderStroke(1.dp, Color(0xFF252938))),
                                    focusedBorder = Border(BorderStroke(2.5.dp, BixOrangeAccent))
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isEpFocused) BixOrangeAccent else BixOrangeAccent.copy(alpha = 0.2f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "EP ${ep.episodeNumber.toString().padStart(2, '0')}",
                                                color = if (isEpFocused) Color.Black else BixOrangeAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (ep.durationFormatted.isNotBlank()) {
                                                Text(
                                                    text = ep.durationFormatted,
                                                    color = Color(0xFF9CA3AF),
                                                    fontSize = 10.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            if (isEpFocused) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = BixOrangeAccent,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = ep.title,
                                            color = if (isEpFocused) BixOrangeAccent else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = ep.plot ?: "Capítulo ${ep.episodeNumber}",
                                            color = Color(0xFF9CA3AF),
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
