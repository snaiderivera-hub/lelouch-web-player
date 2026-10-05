package com.lelouch.feature.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.lelouch.core.designsystem.*
import com.lelouch.core.designsystem.components.releasesFocusVertically
import com.lelouch.core.domain.repository.ChannelRepository
import com.lelouch.core.domain.repository.SeriesRepository
import com.lelouch.core.domain.repository.VodRepository
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.Series
import com.lelouch.core.model.VodMovie
import com.lelouch.feature.tv.ChannelUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Clean, fast, and scalable search screen for Android TV.
 * FASE CONTROLADA — P1 #3:
 * - Uses native IME text input (compatible with remote, Gboard, and voice input).
 * - Debounces user typing (250ms) and cancels outdated queries automatically with collectLatest.
 * - Queries Room directly via repositories with source isolation and hidden category filtering.
 * - NEVER materializes whole catalogs (50k + 30k + 10k) into RAM.
 * - Bounded results per domain (20 items max per section).
 */
@OptIn(ExperimentalTvMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun TvSearchContent(
    activeSourceId: String?,
    channelRepository: ChannelRepository?,
    vodRepository: VodRepository?,
    seriesRepository: SeriesRepository?,
    hiddenLiveCategories: Set<String>,
    hiddenMovieCategories: Set<String>,
    hiddenSeriesCategories: Set<String>,
    popularMovies: List<VodMovie>,
    onSelectChannel: (ChannelUiModel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit,
    searchFocusRequester: FocusRequester,
    sidebarRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }

    var searchChannelsResults by remember { mutableStateOf<List<ChannelUiModel>>(emptyList()) }
    var searchMoviesResults by remember { mutableStateOf<List<VodMovie>>(emptyList()) }
    var searchSeriesResults by remember { mutableStateOf<List<Series>>(emptyList()) }

    val isSearching = searchQuery.trim().length >= 2

    LaunchedEffect(activeSourceId, hiddenLiveCategories, hiddenMovieCategories, hiddenSeriesCategories) {
        snapshotFlow { searchQuery }
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce(250)
            .collectLatest { query ->
                if (query.length < 2 || activeSourceId.isNullOrBlank() || channelRepository == null || vodRepository == null || seriesRepository == null) {
                    searchChannelsResults = emptyList()
                    searchMoviesResults = emptyList()
                    searchSeriesResults = emptyList()
                } else {
                    val channelsDeferred = async(Dispatchers.IO) {
                        channelRepository.searchChannels(
                            sourceId = activeSourceId,
                            query = query,
                            hiddenCategoryIds = hiddenLiveCategories.toList(),
                            limit = 20
                        )
                    }
                    val moviesDeferred = async(Dispatchers.IO) {
                        vodRepository.searchMovies(
                            sourceId = activeSourceId,
                            query = query,
                            hiddenCategoryIds = hiddenMovieCategories.toList(),
                            limit = 20
                        )
                    }
                    val seriesDeferred = async(Dispatchers.IO) {
                        seriesRepository.searchSeries(
                            sourceId = activeSourceId,
                            query = query,
                            hiddenCategoryIds = hiddenSeriesCategories.toList(),
                            limit = 20
                        )
                    }

                    searchChannelsResults = channelsDeferred.await().map { ch ->
                        ChannelUiModel(
                            id = ch.id,
                            streamId = ch.streamId,
                            name = ch.name,
                            num = ch.num,
                            categoryName = ch.categoryName,
                            categoryId = ch.categoryId,
                            streamIcon = ch.streamIcon,
                            streamUrl = ch.streamUrl,
                            isFavorite = ch.isFavorite,
                            sourceId = activeSourceId
                        )
                    }
                    searchMoviesResults = moviesDeferred.await()
                    searchSeriesResults = seriesDeferred.await()
                }
            }
    }

    val totalResults = searchChannelsResults.size + searchMoviesResults.size + searchSeriesResults.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 24.dp)
    ) {
        // Search Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isInputFocused) LelouchCardFocused else LelouchSurface)
                .border(
                    width = if (isInputFocused) 2.dp else 1.dp,
                    color = if (isInputFocused) LelouchCyanAccent else LelouchBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isInputFocused) LelouchCyanAccent else LelouchTextSecondary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                cursorBrush = SolidColor(LelouchCyanAccent),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
                    .focusProperties {
                        if (sidebarRequester != null) {
                            left = sidebarRequester
                        }
                    }
                    .releasesFocusVertically()
                    .onFocusChanged { isInputFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Escribe o usa tu voz para buscar canales, películas o series...",
                            color = LelouchTextMuted,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            )

            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { searchQuery = "" }
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar",
                        tint = LelouchTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Results or Suggestions Header
        if (isSearching) {
            Text(
                text = if (totalResults > 0) "RESULTADOS PARA \"$searchQuery\" ($totalResults):" else "No se encontraron resultados para \"$searchQuery\"",
                color = if (totalResults > 0) LelouchCyanAccent else LelouchTextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        } else {
            Text(
                text = "SUGERENCIAS POPULARES:",
                color = LelouchTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content Area
        if (!isSearching) {
            // Show suggestions: top movies + channels
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🎬 Películas Recomendadas",
                    color = LelouchTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    itemsIndexed(popularMovies.take(10)) { _, movie ->
                        TvPosterCard(
                            title = movie.name,
                            posterUrl = movie.streamIcon,
                            rating = movie.rating ?: 0.0,
                            year = movie.year,
                            onClick = { onSelectMovie(movie) }
                        )
                    }
                }
            }
        } else {
            // Search Results
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 36.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Channels
                items(searchChannelsResults, key = { "search_ch_${it.id.ifEmpty { it.streamId.toString() }}" }) { channel ->
                    var isCardFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { onSelectChannel(channel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .onFocusChanged { isCardFocused = it.isFocused },
                        shape = ClickableSurfaceDefaults.shape(
                            shape = RoundedCornerShape(12.dp),
                            focusedShape = RoundedCornerShape(12.dp)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = LelouchSurface,
                            focusedContainerColor = LelouchCardFocused,
                            pressedContainerColor = LelouchCardFocused
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(androidx.compose.foundation.BorderStroke(1.dp, LelouchBorder)),
                            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, LelouchCyanAccent))
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔴 EN VIVO",
                                color = LelouchLiveRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = channel.name,
                                color = if (isCardFocused) LelouchCyanAccent else LelouchTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                            Text(
                                text = "CH ${channel.num}",
                                color = LelouchTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Movies
                items(searchMoviesResults, key = { "search_mov_${it.id.ifEmpty { it.streamId.toString() }}" }) { movie ->
                    TvPosterCard(
                        title = movie.name,
                        posterUrl = movie.streamIcon,
                        rating = movie.rating ?: 0.0,
                        year = movie.year,
                        onClick = { onSelectMovie(movie) }
                    )
                }

                // Series
                items(searchSeriesResults, key = { "search_ser_${it.id.ifEmpty { it.seriesId.toString() }}" }) { series ->
                    TvPosterCard(
                        title = series.name,
                        posterUrl = series.cover,
                        rating = series.rating ?: 0.0,
                        year = series.releaseDate?.take(4),
                        onClick = { onSelectSeries(series) }
                    )
                }
            }
        }
    }
}
