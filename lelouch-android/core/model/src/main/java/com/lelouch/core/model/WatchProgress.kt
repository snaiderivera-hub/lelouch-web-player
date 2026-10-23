package com.lelouch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchProgress(
    val contentId: String,
    val contentType: ContentType,
    val positionMs: Long,
    val durationMs: Long,
    val percent: Float = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val updatedAtTimestamp: Long = System.currentTimeMillis()
) {
    /** ¿Merece la pena ofrecer «continuar viendo» en esta posición? */
    val isResumable: Boolean
        get() = durationMs > 0L && positionMs >= MIN_RESUME_MS && percent < COMPLETION_PERCENT

    companion object {
        /** Por debajo de 15 s se considera que el usuario no llegó a empezar. */
        const val MIN_RESUME_MS = 15_000L

        /** Al 95 % se considera terminado y se ofrece empezar de cero. */
        const val COMPLETION_PERCENT = 0.95f

        /** Margen final para que reanudar no caiga dentro de los créditos. */
        const val END_CREDITS_GUARD_MS = 5_000L
    }
}
