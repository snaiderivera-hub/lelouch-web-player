package com.lelouch.core.data.preferences

import kotlinx.serialization.Serializable

/**
 * Metadatos de sincronización por fuente/proveedor para control de frescura (FASE P0 #5).
 * Permite evitar descargas completas en cada arranque si el catálogo no ha cambiado.
 */
@Serializable
data class SourceSyncMetadata(
    val sourceId: String,
    val lastSuccessfulSyncAt: Long = 0L,
    val lastSuccessfulVersion: String = "",
    val lastEtag: String = "",
    val lastSyncResult: String = "SUCCESS",
    val channelCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0
)
