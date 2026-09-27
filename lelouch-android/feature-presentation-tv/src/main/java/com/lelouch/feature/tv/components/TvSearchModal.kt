package com.lelouch.feature.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.Series
import com.lelouch.core.model.VodMovie

/**
 * On-screen D-Pad keyboard and fast local FTS5 search dialog for Android TV.
 */
@Composable
fun TvSearchModal(
    channels: List<LiveStream>,
    movies: List<VodMovie>,
    seriesList: List<Series>,
    onSelectChannel: (LiveStream) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredChannels = remember(searchQuery, channels) {
        if (searchQuery.length < 2) emptyList()
        else channels.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(15)
    }

    val filteredMovies = remember(searchQuery, movies) {
        if (searchQuery.length < 2) emptyList()
        else movies.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(15)
    }

    val filteredSeries = remember(searchQuery, seriesList) {
        if (searchQuery.length < 2) emptyList()
        else seriesList.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(15)
    }

    val keyboardRows = listOf(
        listOf("A", "B", "C", "D", "E", "F", "1", "2", "3"),
        listOf("G", "H", "I", "J", "K", "L", "4", "5", "6"),
        listOf("M", "N", "O", "P", "Q", "R", "7", "8", "9"),
        listOf("S", "T", "U", "V", "W", "X", "Y", "Z", "0")
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LelouchBackground.copy(alpha = 0.96f))
                .padding(36.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra Superior: Búsqueda y Botón Salir
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = LelouchCyanAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Escribe para buscar..." else searchQuery,
                            color = if (searchQuery.isEmpty()) LelouchTextMuted else LelouchTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

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

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxSize()) {
                    // Columna Izquierda: Teclado Virtual D-Pad
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .padding(end = 24.dp)
                    ) {
                        keyboardRows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { char ->
                                    var isKeyFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .onFocusChanged { isKeyFocused = it.isFocused }
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isKeyFocused) LelouchCyanAccent else LelouchSurface)
                                            .border(1.dp, if (isKeyFocused) Color.White else LelouchBorder, RoundedCornerShape(6.dp))
                                            .clickable { searchQuery += char },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            color = if (isKeyFocused) Color.Black else LelouchTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fila de Controles Especiales (Espacio, Borrar, Limpiar)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var isSpaceFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(36.dp)
                                    .onFocusChanged { isSpaceFocused = it.isFocused }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSpaceFocused) LelouchCyanAccent else LelouchSurface)
                                    .border(1.dp, if (isSpaceFocused) Color.White else LelouchBorder, RoundedCornerShape(6.dp))
                                    .clickable { searchQuery += " " },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ESPACIO", color = if (isSpaceFocused) Color.Black else LelouchTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            var isDeleteFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .onFocusChanged { isDeleteFocused = it.isFocused }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDeleteFocused) LelouchLiveRed else LelouchSurface)
                                    .border(1.dp, if (isDeleteFocused) Color.White else LelouchBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (searchQuery.isNotEmpty()) {
                                            searchQuery = searchQuery.dropLast(1)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Backspace, contentDescription = "Borrar", tint = Color.White, modifier = Modifier.size(16.dp))
                            }

                            var isClearFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .onFocusChanged { isClearFocused = it.isFocused }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isClearFocused) LelouchSurfaceVariant else LelouchSurface)
                                    .border(1.dp, if (isClearFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(6.dp))
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LIMPIAR", color = LelouchTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    // Columna Derecha: Rieles de Resultados Instantáneos
                    TvLazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (searchQuery.length < 2) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Introduce al menos 2 letras para buscar en todo el catálogo",
                                        color = LelouchTextMuted,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            val totalResults = filteredChannels.size + filteredMovies.size + filteredSeries.size
                            item {
                                Text(
                                    text = "$totalResults resultados encontrados para \"$searchQuery\"",
                                    color = LelouchCyanAccent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            // Riel: Canales Encontrados
                            if (filteredChannels.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "🔴 Canales en Vivo (${filteredChannels.size})",
                                        color = LelouchTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    TvLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        itemsIndexed(filteredChannels) { _, channel ->
                                            var isFocused by remember { mutableStateOf(false) }
                                            Box(
                                                modifier = Modifier
                                                    .width(160.dp)
                                                    .height(70.dp)
                                                    .onFocusChanged { isFocused = it.isFocused }
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isFocused) LelouchCardFocused else LelouchSurface)
                                                    .border(1.dp, if (isFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(8.dp))
                                                    .clickable { onSelectChannel(channel) }
                                                    .padding(10.dp)
                                            ) {
                                                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                                    Text(channel.name, color = LelouchTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    Text(channel.categoryName ?: "En Vivo", color = LelouchCyanAccent, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // Riel: Películas Encontradas
                            if (filteredMovies.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "🎬 Películas (${filteredMovies.size})",
                                        color = LelouchTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    TvLazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        itemsIndexed(filteredMovies) { _, movie ->
                                            TvPosterCard(
                                                title = movie.name,
                                                posterUrl = movie.streamIcon,
                                                rating = movie.rating ?: 0.0,
                                                year = movie.year,
                                                width = 130.dp,
                                                height = 195.dp,
                                                onClick = { onSelectMovie(movie) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // Riel: Series Encontradas
                            if (filteredSeries.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "📺 Series (${filteredSeries.size})",
                                        color = LelouchTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    TvLazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        itemsIndexed(filteredSeries) { _, series ->
                                            TvPosterCard(
                                                title = series.name,
                                                posterUrl = series.cover,
                                                rating = series.rating ?: 0.0,
                                                year = series.releaseDate?.take(4),
                                                width = 130.dp,
                                                height = 195.dp,
                                                onClick = { onSelectSeries(series) }
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
}
