package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["sourceId", "categoryId"]),
        Index(value = ["streamId"]),
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["isFavorite"])
    ]
)
data class ChannelEntity(
    @PrimaryKey
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
