package com.lelouch.core.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * FASE 37 · Capa de gestos del reproductor móvil.
 *
 * - Arrastre vertical en la mitad izquierda  → brillo de la ventana.
 * - Arrastre vertical en la mitad derecha    → volumen multimedia.
 * - Arrastre horizontal                      → búsqueda (se aplica al soltar).
 * - Doble toque izquierda/derecha            → ±10 s.
 * - Doble toque centro                       → play/pausa.
 *
 * Se desactiva con [enabled] = false en directo: ahí el gesto vertical ya está asignado
 * al cambio de canal en las pantallas y no queremos robarle el evento.
 */
@Composable
fun PlayerGestureLayer(
    engine: LelouchPlayerEngine,
    enabled: Boolean = true,
    onSingleTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    var osd by remember { mutableStateOf<GestureOsd?>(null) }
    var brightness by remember { mutableFloatStateOf(activity.initialBrightness()) }
    var pendingSeekMs by remember { mutableStateOf(0L) }

    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
    }
    var volume by remember(audioManager) {
        mutableFloatStateOf((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) / maxVolume.toFloat())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled, activity) {
                var mode: GestureMode? = null
                var startX = 0f
                var totalDx = 0f
                var totalDy = 0f

                detectDragGestures(
                    onDragStart = { offset ->
                        mode = null
                        startX = offset.x
                        totalDx = 0f
                        totalDy = 0f
                        pendingSeekMs = 0L
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y

                        if (mode == null) {
                            val threshold = 12f
                            if (abs(totalDx) > threshold || abs(totalDy) > threshold) {
                                mode = when {
                                    abs(totalDx) > abs(totalDy) -> GestureMode.SEEK
                                    startX < size.width / 2f -> GestureMode.BRIGHTNESS
                                    else -> GestureMode.VOLUME
                                }
                            }
                        }

                        when (mode) {
                            GestureMode.BRIGHTNESS -> {
                                // Arriba = más brillo. 1 px ≈ 1/500 del recorrido.
                                brightness = (brightness - dragAmount.y / 500f).coerceIn(0.02f, 1f)
                                activity?.applyBrightness(brightness)
                                osd = GestureOsd("☀", (brightness * 100).roundToLong().toString(), "Brillo")
                            }
                            GestureMode.VOLUME -> {
                                volume = (volume - dragAmount.y / 400f).coerceIn(0f, 1f)
                                audioManager?.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (volume * maxVolume).toInt().coerceIn(0, maxVolume),
                                    0
                                )
                                osd = GestureOsd("🔊", (volume * 100).roundToLong().toString(), "Volumen")
                            }
                            GestureMode.SEEK -> {
                                val duration = engine.durationMs
                                if (duration > 0L) {
                                    // Un barrido completo de la pantalla equivale a 90 s.
                                    pendingSeekMs = (totalDx / size.width * SEEK_RANGE_MS).roundToLong()
                                    osd = GestureOsd(
                                        if (pendingSeekMs >= 0) "⏩" else "⏪",
                                        formatSignedSeconds(pendingSeekMs),
                                        "Búsqueda"
                                    )
                                }
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = {
                        if (mode == GestureMode.SEEK && pendingSeekMs != 0L) {
                            engine.seekBy(pendingSeekMs)
                        }
                        mode = null
                        pendingSeekMs = 0L
                        osd = null
                    },
                    onDragCancel = {
                        mode = null
                        pendingSeekMs = 0L
                        osd = null
                    }
                )
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> engine.seekBy(-DOUBLE_TAP_SEEK_MS)
                            offset.x > size.width - third -> engine.seekBy(DOUBLE_TAP_SEEK_MS)
                            else -> engine.togglePlayPause()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        osd?.let { current ->
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = current.icon, fontSize = 26.sp)
                Text(
                    text = current.value,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = current.label,
                    color = Color(0xFF9AA4B2),
                    fontSize = 11.sp
                )
            }
        }
    }
}

private enum class GestureMode { BRIGHTNESS, VOLUME, SEEK }

private data class GestureOsd(val icon: String, val value: String, val label: String)

private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val SEEK_RANGE_MS = 90_000f

private fun formatSignedSeconds(ms: Long): String {
    val totalSeconds = abs(ms) / 1000
    val sign = if (ms >= 0) "+" else "-"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "$sign${minutes}m ${seconds}s" else "$sign${seconds}s"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity?.initialBrightness(): Float {
    val stored = this?.window?.attributes?.screenBrightness ?: -1f
    // -1 significa «brillo del sistema»: arrancamos desde la mitad para no dar un salto brusco.
    return if (stored < 0f) 0.5f else stored.coerceIn(0.02f, 1f)
}

private fun Activity?.applyBrightness(value: Float) {
    val window = this?.window ?: return
    val params = window.attributes
    params.screenBrightness = value.coerceIn(0.02f, 1f)
    window.attributes = params
}
