package com.lelouch.feature.tv.components

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import com.lelouch.core.designsystem.LelouchBorder
import com.lelouch.core.designsystem.LelouchCardFocused
import com.lelouch.core.designsystem.LelouchCyanAccent
import com.lelouch.core.designsystem.LelouchSurface
import com.lelouch.core.designsystem.LelouchSurfaceVariant
import com.lelouch.core.designsystem.LelouchTextMuted
import com.lelouch.core.designsystem.LelouchTextPrimary
import com.lelouch.core.designsystem.LelouchTextSecondary
import com.lelouch.core.player.AUTO_VIDEO_TRACK_ID
import com.lelouch.core.player.LelouchPlayerEngine

/**
 * FASE 36 & 37 · Panel de pistas y formato de pantalla para Android TV (D-pad).
 *
 * Utiliza [TvLazyColumn] nativo de Android TV para garantizar un desplazamiento fluido
 * y retención inquebrantable de foco con el control remoto (no pierde el cursor al bajar).
 */
@Composable
fun TvTracksPanel(
    engine: LelouchPlayerEngine,
    currentResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onResizeModeChanged: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by engine.trackSelection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val firstRowFocus = remember { FocusRequester() }

    // Selector de archivo para subtítulos externos.
    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Permiso no persistente: el subtítulo sigue válido en esta sesión.
            }
            engine.addExternalSubtitle(uri.toString())
        }
    }

    BackHandler { onDismiss() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100L)
        try {
            firstRowFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_PROG_BLUE,
                    KeyEvent.KEYCODE_BUTTON_X,
                    KeyEvent.KEYCODE_BACK -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(490.dp)
                .fillMaxHeight()
                .background(LelouchSurface.copy(alpha = 0.98f))
                .border(width = 1.dp, color = LelouchCyanAccent.copy(alpha = 0.35f))
        ) {
            TvLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 22.dp, bottom = 28.dp)
            ) {
                // ── CABECERA ──
                item {
                    Text(
                        text = "CONFIGURACIÓN DE REPRODUCCIÓN",
                        color = LelouchCyanAccent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Formato de pantalla · Calidad · Audio · Subtítulos",
                        color = LelouchTextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // ── SECCIÓN 1: MODO DE PANTALLA (FORMATO) ──
                item {
                    SectionTitle("Modo de Pantalla")
                }
                item {
                    TrackRow(
                        title = "Ajustar (Original)",
                        subtitle = "Proporción original con barras si corresponde",
                        isSelected = currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        focusRequester = firstRowFocus,
                        isFirstItem = true,
                        onClick = { onResizeModeChanged?.invoke(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
                    )
                }
                item {
                    TrackRow(
                        title = "Llenar Pantalla (Zoom)",
                        subtitle = "Ocupa toda la pantalla sin distorsión (recorte suave)",
                        isSelected = currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        onClick = { onResizeModeChanged?.invoke(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) }
                    )
                }
                item {
                    TrackRow(
                        title = "Estirar a Pantalla Completa (16:9)",
                        subtitle = "Estira al 100% de la pantalla eliminando barras negras",
                        isSelected = currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL,
                        onClick = { onResizeModeChanged?.invoke(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
                    )
                }

                // ── SECCIÓN 2: CALIDAD DE VIDEO ──
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle("Calidad de video")
                }
                item {
                    TrackRow(
                        title = "Auto (adaptativo)",
                        subtitle = "Ajusta la resolución al ancho de banda disponible",
                        isSelected = state.autoVideoSelected,
                        onClick = { engine.selectVideoTrack(AUTO_VIDEO_TRACK_ID) }
                    )
                }
                items(state.video) { track ->
                    TrackRow(
                        title = track.label,
                        subtitle = track.secondaryLabel,
                        isSelected = !state.autoVideoSelected && track.isSelected,
                        onClick = { engine.selectVideoTrack(track.id) }
                    )
                }
                if (state.video.isEmpty()) {
                    item {
                        EmptyHint("El stream no declara variantes de video seleccionables.")
                    }
                }

                // ── SECCIÓN 3: PISTAS DE AUDIO ──
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle("Audio / Idioma")
                }
                if (state.audio.isNotEmpty()) {
                    items(state.audio) { track ->
                        TrackRow(
                            title = track.label,
                            subtitle = track.secondaryLabel,
                            isSelected = track.isSelected,
                            onClick = { engine.selectAudioTrack(track.id) }
                        )
                    }
                } else {
                    item {
                        TrackRow(
                            title = "Pista Principal (Original / Estéreo)",
                            subtitle = "Pista de audio nativa predeterminada del stream",
                            isSelected = true,
                            onClick = {}
                        )
                    }
                }

                // ── SECCIÓN 4: SUBTÍTULOS ──
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle("Subtítulos")
                }
                item {
                    TrackRow(
                        title = "Desactivados",
                        subtitle = null,
                        isSelected = !state.subtitlesEnabled,
                        onClick = { engine.selectSubtitleTrack(null) }
                    )
                }
                items(state.subtitles) { track ->
                    TrackRow(
                        title = track.label,
                        subtitle = track.secondaryLabel,
                        isSelected = state.subtitlesEnabled && track.isSelected,
                        onClick = { engine.selectSubtitleTrack(track.id) }
                    )
                }
                item {
                    TrackRow(
                        title = "Cargar archivo externo…",
                        subtitle = ".srt · .vtt · .ass · .ttml",
                        isSelected = false,
                        onClick = {
                            subtitlePicker.launch(
                                arrayOf(
                                    "application/x-subrip",
                                    "text/vtt",
                                    "text/plain",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        }
                    )
                }
                if (state.subtitles.isEmpty()) {
                    item {
                        EmptyHint("Este contenido no incluye subtítulos embebidos; puedes cargar uno externo.")
                    }
                }

                // ── SECCIÓN 5: BOTÓN DE CIERRE (BLINDAJE DE FOCO) ──
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    TrackRow(
                        title = "✕ CERRAR PANEL",
                        subtitle = "Volver a la reproducción",
                        isSelected = false,
                        isLastItem = true,
                        onClick = { onDismiss() }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "BACK o AZUL: cerrar panel",
                        color = LelouchTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = LelouchTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = LelouchTextMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/**
 * Fila enfocable optimizada para D-pad con retención estricta de límites.
 * El foco se dibuja con borde blanco brillante y fondo cian elevado.
 */
@Composable
private fun TrackRow(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = keyEvent.nativeKeyEvent.keyCode
                when {
                    isFirstItem && code == KeyEvent.KEYCODE_DPAD_UP -> true
                    isLastItem && code == KeyEvent.KEYCODE_DPAD_DOWN -> true
                    code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            }
            .clickable { onClick() }
            .background(
                color = when {
                    isFocused -> LelouchCardFocused
                    isSelected -> LelouchSurfaceVariant
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(9.dp)
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> LelouchCyanAccent.copy(alpha = 0.5f)
                    else -> LelouchBorder
                },
                shape = RoundedCornerShape(9.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = when {
                        isSelected -> LelouchCyanAccent
                        isFocused -> Color.White
                        else -> LelouchBorder
                    },
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = when {
                    isFocused -> Color.White
                    isSelected -> LelouchTextPrimary
                    else -> LelouchTextSecondary
                },
                fontSize = 15.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = if (isFocused) Color.White.copy(alpha = 0.85f) else LelouchTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
