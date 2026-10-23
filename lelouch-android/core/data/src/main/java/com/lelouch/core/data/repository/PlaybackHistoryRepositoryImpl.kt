package com.lelouch.core.data.repository

import com.lelouch.core.database.dao.WatchHistoryDao
import com.lelouch.core.database.entity.WatchHistoryEntity
import com.lelouch.core.domain.repository.PlaybackHistoryRepository
import com.lelouch.core.model.ContentType
import com.lelouch.core.model.ContinueWatchingItem
import com.lelouch.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * FASE 37 · Historial y «continuar viendo».
 *
 * Dueño único de la tabla `watch_history`: las pantallas no construyen entidades, no
 * aplican reglas de reanudación y no conocen el esquema de Room.
 *
 * [record] recibe como `contentId` la **URL resuelta del stream**. Así una entrada del
 * historial se puede volver a reproducir sin reconstruir la URL: las listas M3U no tienen
 * servidor Xtream desde el que regenerarla.
 */
class PlaybackHistoryRepositoryImpl(
    private val dao: WatchHistoryDao
) : PlaybackHistoryRepository {

    /** Ítems con avance reanudable, del más reciente al más antiguo. */
    override fun continueWatching(sourceId: String, limit: Int): Flow<List<ContinueWatchingItem>> =
        dao.getContinueWatching(sourceId, limit).map { rows -> rows.map { it.toUiModel() } }

    /** Avance guardado de un contenido, o `null` si nunca se reprodujo. */
    override suspend fun progressFor(contentId: String, sourceId: String): ContinueWatchingItem? =
        dao.getProgressForContent(contentId, sourceId)?.toUiModel()

    /**
     * Punto de reanudación para arrancar el reproductor.
     * Devuelve 0 cuando no hay avance, el contenido está en vivo o ya se considera visto.
     */
    override suspend fun resumePositionMs(
        contentId: String,
        sourceId: String,
        isLive: Boolean
    ): Long {
        if (isLive || contentId.isBlank()) return 0L
        val saved = progressFor(contentId, sourceId) ?: return 0L
        val progress = WatchProgress(
            contentId = saved.contentId,
            contentType = saved.contentType,
            positionMs = saved.positionMs,
            durationMs = saved.durationMs
        )
        if (!progress.isResumable) return 0L
        return saved.positionMs.coerceAtMost(
            (saved.durationMs - WatchProgress.END_CREDITS_GUARD_MS).coerceAtLeast(0L)
        )
    }

    /**
     * Registra el avance actual.
     *
     * - En vivo no se guarda (no hay reanudación posible y llenaría la tabla de ruido).
     * - Al superar el umbral de «visto» se borra la entrada: no debe aparecer en «continuar viendo».
     */
    override suspend fun record(
        sourceId: String,
        contentId: String,
        title: String,
        contentType: ContentType,
        positionMs: Long,
        durationMs: Long,
        posterUrl: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ) {
        if (contentType == ContentType.LIVE) return
        if (contentId.isBlank() || sourceId.isBlank()) return
        if (!isMeaningfulUpdate(contentId, sourceId, positionMs)) return

        if (durationMs > 0L) {
            val fraction = positionMs.toFloat() / durationMs.toFloat()
            if (fraction >= WatchProgress.COMPLETION_PERCENT) {
                dao.deleteProgress(contentId, sourceId)
                return
            }
        }

        dao.saveProgress(
            WatchHistoryEntity(
                id = "$sourceId-$contentId",
                contentId = contentId,
                title = title,
                posterUrl = posterUrl,
                contentType = contentType.name,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                lastWatchedTimestamp = System.currentTimeMillis(),
                sourceId = sourceId
            )
        )
    }

    override suspend fun remove(contentId: String, sourceId: String) = dao.deleteProgress(contentId, sourceId)

    override suspend fun clear(sourceId: String) = dao.clearHistory(sourceId)

    /**
     * Evita escribir cada segundo: solo persiste si el avance cambió ≥ 5 s, lo que reduce
     * el desgaste de la base sin perder precisión perceptible al reanudar.
     */
    private suspend fun isMeaningfulUpdate(
        contentId: String,
        sourceId: String,
        positionMs: Long
    ): Boolean {
        val previous = dao.getProgressForContent(contentId, sourceId) ?: return true
        return kotlin.math.abs(previous.positionMs - positionMs) >= MIN_PERSIST_DELTA_MS
    }

    private fun WatchHistoryEntity.toUiModel(): ContinueWatchingItem = ContinueWatchingItem(
        contentId = contentId,
        title = title,
        posterUrl = posterUrl,
        contentType = contentType.toContentType(),
        positionMs = positionMs,
        durationMs = durationMs,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        sourceId = sourceId,
        lastWatchedAt = lastWatchedTimestamp
    )

    private fun String.toContentType(): ContentType =
        ContentType.entries.firstOrNull { it.name.equals(this, ignoreCase = true) } ?: ContentType.VOD

    companion object {
        const val MIN_PERSIST_DELTA_MS = 5_000L
    }
}
