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
)
