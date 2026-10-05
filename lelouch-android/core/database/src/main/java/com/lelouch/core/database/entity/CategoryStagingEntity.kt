package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "categories_staging",
    primaryKeys = ["syncId", "id"],
    indices = [
        Index(value = ["syncId", "sourceId"]),
        Index(value = ["categoryId", "type", "sourceId"])
    ]
)
data class CategoryStagingEntity(
    val syncId: String,
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val parentId: Int = 0,
    val type: String,
    val itemCount: Int = 0,
    val isAdult: Boolean = false,
    val sourceId: String = ""
)
