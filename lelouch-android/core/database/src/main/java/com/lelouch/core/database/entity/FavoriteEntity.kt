package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["contentId", "contentType", "sourceId"], unique = true),
        Index(value = ["contentType"]),
        Index(value = ["addedAt"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey
    val id: String, // "$sourceId-$contentType-$contentId"
    val contentId: String,
    val title: String,
    val posterUrl: String? = null,
    val contentType: String, // "LIVE", "VOD", "SERIES"
    val categoryId: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val sourceId: String = ""
)
