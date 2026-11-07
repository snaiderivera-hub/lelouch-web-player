package com.lelouch.feature.tv.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import com.lelouch.core.designsystem.*
import com.lelouch.core.domain.repository.ChannelRepository
import com.lelouch.core.domain.repository.SeriesRepository
import com.lelouch.core.domain.repository.VodRepository
import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.Series
import com.lelouch.core.model.VodMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * On-screen D-Pad keyboard and fast local FTS5 search dialog for Android TV with direct Voice Search.
 */
@OptIn(ExperimentalComposeUiApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun TvSearchModal(
    channels: List<LiveStream> = emptyList(),
    movies: List<VodMovie> = emptyList(),
    seriesList: List<Series> = emptyList(),
    channelRepository: ChannelRepository? = null,
    vodRepository: VodRepository? = null,
    seriesRepository: SeriesRepository? = null,
    activeSourceId: String? = null,
    initialVoiceSearch: Boolean = false,
    onSelectChannel: (LiveStream) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val firstKeyFocusRequester = remember { FocusRequester() }

    var isListeningVoice by remember { mutableStateOf(false) }
    var voiceHint by remember { mutableStateOf<String?>(null) }
    val speechRecognizerState = remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizerState.value?.destroy()
            } catch (_: Exception) {}
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken
            }
        }
    }

    fun startInternalVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Di la película, serie o canal...")
                }
                voiceLauncher.launch(intent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "Búsqueda por voz no disponible", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            speechRecognizerState.value?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizerState.value = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningVoice = true
                    voiceHint = "🎙️ Escuchando... Di el título a buscar"
                }

                override fun onBeginningOfSpeech() {
                    voiceHint = "🎙️ Grabando voz..."
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    voiceHint = "Procesando..."
                }

                override fun onError(error: Int) {
                    isListeningVoice = false
                    voiceHint = null
                    try { recognizer.destroy() } catch (_: Exception) {}
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        voiceHint = "No se entendió. Intente de nuevo"
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        searchQuery = spoken
                    }
                    isListeningVoice = false
                    voiceHint = null
                    try { recognizer.destroy() } catch (_: Exception) {}
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        searchQuery = spoken
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
            isListeningVoice = true
            voiceHint = "🎙️ Hable ahora..."
        } catch (e: Exception) {
            isListeningVoice = false
            voiceHint = null
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Di la película, serie o canal...")
                }
                voiceLauncher.launch(intent)
            } catch (_: Exception) {}
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startInternalVoiceRecognition()
        } else {
            android.widget.Toast.makeText(context, "Permiso de micrófono no otorgado", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun launchVoice() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startInternalVoiceRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (initialVoiceSearch) {
            launchVoice()
        } else {
            firstKeyFocusRequester.requestFocus()
        }
    }

    var dbChannels by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
    var dbMovies by remember { mutableStateOf<List<VodMovie>>(emptyList()) }
    var dbSeries by remember { mutableStateOf<List<Series>>(emptyList()) }

    LaunchedEffect(activeSourceId, vodRepository, seriesRepository, channelRepository) {
        snapshotFlow { searchQuery }
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce(250)
            .collectLatest { q ->
                if (q.length < 2) {
                    dbChannels = emptyList()
                    dbMovies = emptyList()
                    dbSeries = emptyList()
                } else if (vodRepository != null && !activeSourceId.isNullOrBlank()) {
                    val mDef = async(Dispatchers.IO) { vodRepository.searchMovies(activeSourceId, q, emptyList(), 25) }
                    val sDef = async(Dispatchers.IO) { seriesRepository?.searchSeries(activeSourceId, q, emptyList(), 25) ?: emptyList() }
                    val cDef = async(Dispatchers.IO) { channelRepository?.searchChannels(activeSourceId, q, emptyList(), false, 25) ?: emptyList() }
                    dbMovies = mDef.await()
                    dbSeries = sDef.await()
                    dbChannels = cDef.await()
                }
            }
    }

    val filteredChannels = if (vodRepository != null && !activeSourceId.isNullOrBlank()) dbChannels else remember(searchQuery, channels) {
        if (searchQuery.length < 2) emptyList()
        else channels.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(15)
    }

    val filteredMovies = if (vodRepository != null && !activeSourceId.isNullOrBlank()) dbMovies else remember(searchQuery, movies) {
        if (searchQuery.length < 2) emptyList()
        else movies.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(15)
    }

    val filteredSeries = if (vodRepository != null && !activeSourceId.isNullOrBlank()) dbSeries else remember(searchQuery, seriesList) {
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
                // Barra Superior: Búsqueda, Botón de Voz y Botón Salir
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = LelouchCyanAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isListeningVoice) (voiceHint ?: "🎙️ Di el título a buscar...") else if (searchQuery.isEmpty()) "Escribe o usa tu voz para buscar..." else searchQuery,
                            color = if (isListeningVoice) LelouchCyanAccent else if (searchQuery.isEmpty()) LelouchTextMuted else LelouchTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Botón de Voz directa con el micrófono del control
                        var isMicFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isMicFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isListeningVoice) Color(0xFFEF4444).copy(alpha = 0.85f)
                                    else if (isMicFocused) Color(0xFF00E5FF)
                                    else Color(0xFF00E5C9).copy(alpha = 0.22f)
                                )
                                .border(
                                    if (isMicFocused) 2.5.dp else 1.dp,
                                    if (isListeningVoice || isMicFocused) Color.White
                                    else LelouchCyanAccent.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (isListeningVoice) {
                                        try { speechRecognizerState.value?.stopListening() } catch (_: Exception) {}
                                        isListeningVoice = false
                                        voiceHint = null
                                    } else {
                                        launchVoice()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Hablar",
                                    tint = if (isListeningVoice) Color.White else if (isMicFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isListeningVoice) "🔴 ESCUCHANDO..." else "🎤 VOZ",
                                    color = if (isListeningVoice) Color.White else if (isMicFocused) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        var isCloseFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCloseFocused) Color(0xFFEF4444) else LelouchSurface)
                                .border(if (isCloseFocused) 2.5.dp else 1.dp, if (isCloseFocused) Color.White else LelouchBorder, RoundedCornerShape(8.dp))
                                .clickable { onDismiss() }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = if (isCloseFocused) Color.White else Color.White
                            )
                        }
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
                                            .then(if (char == "A") Modifier.focusRequester(firstKeyFocusRequester) else Modifier)
                                            .onFocusChanged { isKeyFocused = it.isFocused }
                                            .focusable()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isKeyFocused) Color(0xFF00E5FF) else Color(0xFF1E293B))
                                            .border(if (isKeyFocused) 2.5.dp else 1.dp, if (isKeyFocused) Color.White else Color(0xFF334155), RoundedCornerShape(6.dp))
                                            .clickable { searchQuery += char },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            color = if (isKeyFocused) Color.Black else Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = if (isKeyFocused) FontWeight.Black else FontWeight.Bold
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
                                    .focusable()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSpaceFocused) Color(0xFF00E5FF) else Color(0xFF1E293B))
                                    .border(if (isSpaceFocused) 2.5.dp else 1.dp, if (isSpaceFocused) Color.White else Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable { searchQuery += " " },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ESPACIO", color = if (isSpaceFocused) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            var isDeleteFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .onFocusChanged { isDeleteFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDeleteFocused) Color(0xFFEF4444) else Color(0xFF1E293B))
                                    .border(if (isDeleteFocused) 2.5.dp else 1.dp, if (isDeleteFocused) Color.White else Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (searchQuery.isNotEmpty()) {
                                            searchQuery = searchQuery.dropLast(1)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Backspace, contentDescription = "Borrar", tint = if (isDeleteFocused) Color.White else Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }

                            var isClearFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .onFocusChanged { isClearFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isClearFocused) Color(0xFFFF9800) else Color(0xFF1E293B))
                                    .border(if (isClearFocused) 2.5.dp else 1.dp, if (isClearFocused) Color.White else Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LIMPIAR", color = if (isClearFocused) Color.Black else Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                    TvLazyRow(
                                        modifier = Modifier.focusRestorer(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        itemsIndexed(
                                            items = filteredChannels,
                                            key = { _, channel -> "search_ch_${channel.streamId}" }
                                        ) { _, channel ->
                                            var isFocused by remember { mutableStateOf(false) }
                                            Box(
                                                modifier = Modifier
                                                    .width(160.dp)
                                                    .height(70.dp)
                                                    .onFocusChanged { isFocused = it.isFocused }
                                                    .focusable()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isFocused) Color(0xFF00E5FF) else LelouchSurface)
                                                    .border(if (isFocused) 2.5.dp else 1.dp, if (isFocused) Color.White else LelouchBorder, RoundedCornerShape(8.dp))
                                                    .clickable { onSelectChannel(channel) }
                                                    .padding(10.dp)
                                            ) {
                                                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                                    Text(channel.name, color = if (isFocused) Color.Black else LelouchTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    Text(channel.categoryName ?: "En Vivo", color = if (isFocused) Color.Black.copy(alpha = 0.8f) else LelouchCyanAccent, fontSize = 10.sp)
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
                                    TvLazyRow(
                                        modifier = Modifier.focusRestorer(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(
                                            items = filteredMovies,
                                            key = { _, movie -> "search_movie_${movie.streamId}" }
                                        ) { _, movie ->
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
                                    TvLazyRow(
                                        modifier = Modifier.focusRestorer(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(
                                            items = filteredSeries,
                                            key = { _, series -> "search_series_${series.seriesId}" }
                                        ) { _, series ->
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
