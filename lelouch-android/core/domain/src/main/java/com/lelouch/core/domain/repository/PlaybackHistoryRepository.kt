package com.lelouch.core.domain.repository

import com.lelouch.core.model.ContentType
import com.lelouch.core.model.ContinueWatchingItem
import kotlinx.coroutines.flow.Flow

/**
 * FASE 37 · Contrato de historial de reproducción y «continuar viendo».
 *
 * Las pantallas dependen de este contrato, no de Room: la implementación vive en
 * `core:data` y es la única que conoce la tabla `watch_history`.
 */
interface PlaybackHistoryRepository {

    /** Ítems con avance reanudable, del más reciente al más antiguo. */
    fun continueWatching(sourceId: String, limit: Int = 30): Flow<List<ContinueWatchingItem>>

    /** Avance guardado de un contenido, o `null` si nunca se reprodujo. */
    suspend fun progressFor(contentId: String, sourceId: String): ContinueWatchingItem?

    /**
     * Punto de reanudación en ms para arrancar el reproductor; 0 si no hay avance,
     * es en vivo o ya se considera visto.
     */
    suspend fun resumePositionMs(contentId: String, sourceId: String, isLive: Boolean): Long

    /**
     * Registra el avance. [contentId] debe ser la URL resuelta del stream, para poder
     * reanudar después sin reconstruirla (las listas M3U no tienen servidor Xtream).
     */
    suspend fun record(
        sourceId: String,
        contentId: String,
        title: String,
        contentType: ContentType,
        positionMs: Long,
        durationMs: Long,
        posterUrl: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    )

    suspend fun remove(contentId: String, sourceId: String)

    suspend fun clear(sourceId: String)
}
