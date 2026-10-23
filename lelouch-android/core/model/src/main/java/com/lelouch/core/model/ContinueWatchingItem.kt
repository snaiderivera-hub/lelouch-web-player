package com.lelouch.core.model

/**
 * FASE 37 · Elemento de «continuar viendo» listo para pintar.
 *
 * Es un modelo de presentación: no expone entidades de Room, de modo que las pantallas no
 * dependen del esquema de base de datos (que es propiedad de `core:database`).
 */
data class ContinueWatchingItem(
    /** Clave de reproducción estable; hoy es la URL resuelta del stream. */
    val contentId: String,
    val title: String,
    val posterUrl: String? = null,
    val contentType: ContentType = ContentType.VOD,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val sourceId: String = "",
    val lastWatchedAt: Long = 0L
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)

    /** Texto corto tipo «Faltan 42 min». */
    val remainingLabel: String
        get() {
            if (durationMs <= 0L) return ""
            val totalMinutes = (remainingMs / 60_000L).toInt()
            return when {
                totalMinutes >= 60 -> "Faltan ${totalMinutes / 60} h ${totalMinutes % 60} min"
                totalMinutes >= 1 -> "Faltan $totalMinutes min"
                else -> "Falta menos de 1 min"
            }
        }

    /** «T2 E5» cuando aplica a un episodio. */
    val episodeLabel: String?
        get() = if (seasonNumber != null && episodeNumber != null) {
            "T$seasonNumber E$episodeNumber"
        } else null
}
