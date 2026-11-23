package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_health")
data class SourceHealthEntity(
    @PrimaryKey
    val sourceId: String,
    val host: String,
    val successCount: Int,
    val failCount: Int,
    val avgLatencyMs: Long,
    val lastSuccessAt: Long,
    val lastFailAt: Long,
    val updatedAt: Long
)
