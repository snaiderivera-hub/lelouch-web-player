package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["seriesId"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["isFavorite"])
    ]
)
data class SeriesEntity(
    @PrimaryKey
    val id: String,
    val seriesId: Int,
    val num: Int = 0,
    val name: String,
    val title: String = name,
    val cover: String? = null,
    val backdropPath: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val rating5based: Double? = null,
    val categoryId: String,
    val categoryName: String = "",
    val isFavorite: Boolean = false,
    val sourceId: String = ""
)
