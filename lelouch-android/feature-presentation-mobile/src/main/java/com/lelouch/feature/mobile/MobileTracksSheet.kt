package com.lelouch.feature.mobile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import com.lelouch.core.designsystem.LelouchBorder
import com.lelouch.core.designsystem.LelouchCyanAccent
import com.lelouch.core.designsystem.LelouchSurface
import com.lelouch.core.designsystem.LelouchSurfaceVariant
import com.lelouch.core.designsystem.LelouchTextMuted
import com.lelouch.core.designsystem.LelouchTextPrimary
import com.lelouch.core.designsystem.LelouchTextSecondary
import com.lelouch.core.player.AUTO_VIDEO_TRACK_ID
import com.lelouch.core.player.LelouchPlayerEngine

/**
 * FASE 36 & 37 · Hoja inferior de pistas y formato de pantalla para móvil/tablet.
 *
 * Permite cambiar:
 * 1. Modo de Pantalla (Ajustar / Llenar Zoom / Estirar 16:9)
 * 2. Calidad de video (o Auto)
 * 3. Pista de audio
 * 4. Subtítulos embebidos o externos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTracksSheet(
    engine: LelouchPlayerEngine,
    currentResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    onResizeModeChanged: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val state by engine.trackSelection.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                // Permiso no persistente: el subtítulo sigue disponible en esta sesión.
            }
            engine.addExternalSubtitle(uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LelouchSurface,
        contentColor = LelouchTextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = LelouchBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Configuración de Pantalla y Pistas",
                color = LelouchCyanAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── MODO DE PANTALLA (FORMATO) ──
            MobileSectionTitle("Modo de Pantalla")
            MobileTrackRow(
                title = "Ajustar (Original)",
                subtitle = "Mantiene proporción original con barras",
                isSelected = currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                onClick = { onResizeModeChanged?.invoke(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
            )
            MobileTrackRow(
                title = "Llenar Pantalla (Zoom)",
                subtitle = "Ocupa toda la pantalla recortando bordes suaves",
                isSelected = currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                onClick = { onResizeModeChanged?.invoke(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) }
            )
            MobileTrackRow(
                title = "Estirar a Pantalla Completa (16:9)",
                subtitle = "Estira al 100% sin barras negras ni recortes",
                isSelected = currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL,
                onClick = { onResizeModeChanged?.invoke(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── CALIDAD DE VIDEO ──
            MobileSectionTitle("Calidad de video")
            MobileTrackRow(
                title = "Auto (adaptativo)",
                subtitle = "Ajusta la resolución al ancho de banda",
                isSelected = state.autoVideoSelected,
                onClick = { engine.selectVideoTrack(AUTO_VIDEO_TRACK_ID) }
            )
            state.video.forEach { track ->
                MobileTrackRow(
                    title = track.label,
                    subtitle = track.secondaryLabel,
                    isSelected = !state.autoVideoSelected && track.isSelected,
                    onClick = { engine.selectVideoTrack(track.id) }
                )
            }

            // ── AUDIO ──
            Spacer(modifier = Modifier.height(14.dp))
            MobileSectionTitle("Audio / Idioma")
            if (state.audio.isNotEmpty()) {
                state.audio.forEach { track ->
                    MobileTrackRow(
                        title = track.label,
                        subtitle = track.secondaryLabel,
                        isSelected = track.isSelected,
                        onClick = { engine.selectAudioTrack(track.id) }
                    )
                }
            } else {
                MobileTrackRow(
                    title = "Pista Principal (Original / Estéreo)",
                    subtitle = "Audio nativo predeterminado del stream",
                    isSelected = true,
                    onClick = {}
                )
            }

            // ── SUBTÍTULOS ──
            Spacer(modifier = Modifier.height(14.dp))
            MobileSectionTitle("Subtítulos")
            MobileTrackRow(
                title = "Desactivados",
                subtitle = null,
                isSelected = !state.subtitlesEnabled,
                onClick = { engine.selectSubtitleTrack(null) }
            )
            state.subtitles.forEach { track ->
                MobileTrackRow(
                    title = track.label,
                    subtitle = track.secondaryLabel,
                    isSelected = state.subtitlesEnabled && track.isSelected,
                    onClick = { engine.selectSubtitleTrack(track.id) }
                )
            }
            MobileTrackRow(
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
    }
}

@Composable
private fun MobileSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = LelouchTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun MobileTrackRow(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                color = if (isSelected) LelouchSurfaceVariant else Color.Transparent,
                shape = RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
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
                color = if (isSelected) LelouchTextPrimary else LelouchTextSecondary,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, color = LelouchTextMuted, fontSize = 11.sp)
            }
        }
    }
}
