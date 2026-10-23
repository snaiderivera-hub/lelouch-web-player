package com.lelouch.core.player

import com.lelouch.core.model.WatchProgress
import kotlin.math.max
import kotlin.math.min

/**
 * FASE 37 · Muestra periódica del avance de reproducción.
 *
 * El motor la emite cada segundo mientras haya reproducción activa. Las pantallas la usan
 * para guardar «continuar viendo» sin tener que consultar ExoPlayer directamente (lo que
 * obligaría a replicar la lógica de negocio del reproductor fuera de su dueño).
 */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLive: Boolean = false,
    val isPlaying: Boolean = false
) {
    /** Fracción consumida en el rango [0, 1]. 0 cuando la duración aún es desconocida. */
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** Tiempo restante en ms, o 0 si no aplica (en vivo / duración desconocida). */
    val remainingMs: Long
        get() = if (isLive || durationMs <= 0L) 0L else max(0L, durationMs - positionMs)

    /**
     * ¿Merece la pena reanudar aquí más tarde?
     *
     * Evita dos molestias típicas: reanudar en los primeros segundos (el usuario apenas
     * empezó) y reanudar cuando ya faltan créditos finales (quiere volver a ver el final).
     */
    val isResumable: Boolean
        get() = !isLive && durationMs > 0L &&
            positionMs >= WatchProgress.MIN_RESUME_MS &&
            fraction < WatchProgress.COMPLETION_PERCENT

    /** Posición a la que reanudar, recortada para no quedarse fuera del contenido. */
    val resumePositionMs: Long
        get() = min(positionMs, max(0L, durationMs - WatchProgress.END_CREDITS_GUARD_MS))
}
