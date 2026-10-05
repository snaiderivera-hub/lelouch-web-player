package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "channels_staging",
    primaryKeys = ["syncId", "id"],
    indices = [
        Index(value = ["syncId", "sourceId"]),
        Index(value = ["streamId"])
    ]
)
data class ChannelStagingEntity(
    val syncId: String,
    val id: String,
    val streamId: Int,
    val num: Int = 0,
    val name: String,
    val streamType: String = "live",
    val streamIcon: String? = null,
    val categoryId: String,
    val categoryName: String = "",
    val epgChannelId: String? = null,
    val isAdult: Boolean = false,
    val isFavorite: Boolean = false,
    val streamUrl: String = "",
    val containerExtension: String = "ts",
    val sourceId: String = ""
)
