package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["sourceId", "categoryId"]),
        Index(value = ["streamId"]),
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["rating"]),
        Index(value = ["added"]),
        Index(value = ["isFavorite"])
    ]
)
data class MovieEntity(
    @PrimaryKey
    val id: String,
    val streamId: Int,
    val num: Int = 0,
    val name: String,
    val title: String = name,
    val year: String? = null,
    val streamIcon: String? = null,
    val backdropPath: String? = null,
    val rating: Double? = null,
    val rating5based: Double? = null,
    val added: String? = null,
    val categoryId: String,
    val categoryName: String = "",
    val containerExtension: String = "mp4",
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val durationSecs: Int = 0,
    val streamUrl: String = "",
    val isFavorite: Boolean = false,
    val sourceId: String = ""
)
