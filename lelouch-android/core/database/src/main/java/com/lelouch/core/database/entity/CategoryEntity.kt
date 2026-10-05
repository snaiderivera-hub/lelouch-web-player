package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["categoryId", "type", "sourceId"], unique = true),
        Index(value = ["sourceId", "type"]),
        Index(value = ["type"])
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val id: String, // composite: "$sourceId-$type-$categoryId"
    val categoryId: String,
    val categoryName: String,
    val parentId: Int = 0,
    val type: String, // "LIVE", "VOD", "SERIES"
    val itemCount: Int = 0,
    val isAdult: Boolean = false,
    val sourceId: String = ""
)
