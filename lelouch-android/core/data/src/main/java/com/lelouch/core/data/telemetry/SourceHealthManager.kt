package com.lelouch.core.data.telemetry

import com.lelouch.core.database.dao.SourceHealthDao
import com.lelouch.core.database.entity.SourceHealthEntity
import com.lelouch.core.model.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Persiste métricas históricas de éxito, fallos y latencia por fuente/servidor en Room
 * para calcular un score de confiabilidad (0..100) y priorizar las listas más estables.
 */
class SourceHealthManager(
    private val sourceHealthDao: SourceHealthDao
) {
    fun observeScores(): Flow<Map<String, Double>> {
        return sourceHealthDao.observeAll().map { entries ->
            entries.associate { it.sourceId to computeScore(it) }
        }
    }

    suspend fun recordOutcome(
        sourceId: String,
        serverUrl: String,
        success: Boolean,
        latencyMs: Long = 0L
    ) = withContext(Dispatchers.IO) {
        if (sourceId.isBlank()) return@withContext
        val now = System.currentTimeMillis()
        val host = runCatching { URI(serverUrl.trim()).host?.lowercase() }.getOrNull().orEmpty()
        val prev = sourceHealthDao.getBySourceId(sourceId)
        val newSuccess = (prev?.successCount ?: 0) + if (success) 1 else 0
        val newFail = (prev?.failCount ?: 0) + if (success) 0 else 1
        val prevLatency = prev?.avgLatencyMs ?: 0L
        val newLatency = when {
            !success || latencyMs <= 0L -> prevLatency
            prevLatency <= 0L -> latencyMs
            else -> ((prevLatency * 7L) + (latencyMs * 3L)) / 10L
        }
        sourceHealthDao.upsert(
            SourceHealthEntity(
                sourceId = sourceId,
                host = host.ifBlank { prev?.host.orEmpty() },
                successCount = newSuccess,
                failCount = newFail,
                avgLatencyMs = newLatency,
                lastSuccessAt = if (success) now else (prev?.lastSuccessAt ?: 0L),
                lastFailAt = if (!success) now else (prev?.lastFailAt ?: 0L),
                updatedAt = now
            )
        )
    }

    fun sortSourcesByHealth(
        sources: List<SourceConfig>,
        scores: Map<String, Double>,
        activeSourceId: String? = null
    ): List<SourceConfig> {
        return sources.sortedWith(
            compareByDescending<SourceConfig> { it.id == activeSourceId }
                .thenByDescending { scores[it.id] ?: 50.0 }
                .thenBy { it.name.lowercase() }
        )
    }

    companion object {
        fun computeScore(entity: SourceHealthEntity): Double {
            val total = entity.successCount + entity.failCount
            if (total == 0) return 50.0
            val successRatio = entity.successCount.toDouble() / total.toDouble()
            val latencyPenalty = (entity.avgLatencyMs.coerceIn(0L, 6000L) / 6000.0) * 18.0
            val recencyBonus = if (entity.lastSuccessAt > entity.lastFailAt) 5.0 else -5.0
            return (successRatio * 95.0 - latencyPenalty + recencyBonus).coerceIn(0.0, 100.0)
        }
    }
}
