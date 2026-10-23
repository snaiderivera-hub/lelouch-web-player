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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * FASE 36 · Panel de pistas para Android TV (D-pad).
 *
 * Permite cambiar calidad de video (o volver a Auto/ABR), pista de audio y subtítulos
 * embebidos, además de cargar un subtítulo externo (`.srt`, `.vtt`, `.ass`, `.ttml`).
 *
 * Se abre con el botón AZUL del control remoto y se cierra con BACK o el mismo botón.
 */
@Composable
fun TvTracksPanel(
    engine: LelouchPlayerEngine,
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
                // Algunos proveedores no conceden permiso persistente; el subtítulo sigue válido en esta sesión.
            }
            engine.addExternalSubtitle(uri.toString())
        }
    }

    BackHandler { onDismiss() }

    LaunchedEffect(Unit) {
        try {
            firstRowFocus.requestFocus()
        } catch (_: Exception) {
            // El árbol de foco puede no estar listo en el primer frame; el usuario puede navegar con D-pad.
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
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
        Column(
            modifier = Modifier
                .width(470.dp)
                .fillMaxHeight()
                .background(LelouchSurface.copy(alpha = 0.98f))
                .padding(horizontal = 24.dp, vertical = 22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "PISTAS Y SUBTÍTULOS",
                color = LelouchCyanAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Calidad de video · Audio · Subtítulos",
                color = LelouchTextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(18.dp))

            SectionTitle("Calidad de video")
            TrackRow(
                title = "Auto (adaptativo)",
                subtitle = "Ajusta la resolución al ancho de banda disponible",
                isSelected = state.autoVideoSelected,
                focusRequester = firstRowFocus,
                onClick = { engine.selectVideoTrack(AUTO_VIDEO_TRACK_ID) }
            )
            state.video.forEach { track ->
                TrackRow(
                    title = track.label,
                    subtitle = track.secondaryLabel,
                    isSelected = !state.autoVideoSelected && track.isSelected,
                    onClick = { engine.selectVideoTrack(track.id) }
                )
            }
            if (state.video.isEmpty()) {
                EmptyHint("El stream no declara variantes de video seleccionables.")
            }

            if (state.audio.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                SectionTitle("Audio")
                state.audio.forEach { track ->
                    TrackRow(
                        title = track.label,
                        subtitle = track.secondaryLabel,
                        isSelected = track.isSelected,
                        onClick = { engine.selectAudioTrack(track.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            SectionTitle("Subtítulos")
            TrackRow(
                title = "Desactivados",
                subtitle = null,
                isSelected = !state.subtitlesEnabled,
                onClick = { engine.selectSubtitleTrack(null) }
            )
            state.subtitles.forEach { track ->
                TrackRow(
                    title = track.label,
                    subtitle = track.secondaryLabel,
                    isSelected = state.subtitlesEnabled && track.isSelected,
                    onClick = { engine.selectSubtitleTrack(track.id) }
                )
            }
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
            if (state.subtitles.isEmpty()) {
                EmptyHint("Este contenido no incluye subtítulos embebidos; puedes cargar uno externo.")
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "BACK o AZUL: cerrar panel",
                color = LelouchTextMuted,
                fontSize = 11.sp
            )
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
 * Fila enfocable optimizada para D-pad. El foco se dibuja con borde cian y fondo elevado,
 * siguiendo el lenguaje visual del resto de la app.
 */
@Composable
private fun TrackRow(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
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
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> LelouchCyanAccent
                    isSelected -> LelouchCyanAccent.copy(alpha = 0.45f)
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
                    color = if (isSelected) LelouchCyanAccent else LelouchBorder,
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
                color = if (isFocused || isSelected) LelouchTextPrimary else LelouchTextSecondary,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = LelouchTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
