package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "movies_fts")
@Fts4(contentEntity = MovieEntity::class)
data class MovieFtsEntity(
    val name: String,
    val title: String,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val plot: String?
)

@Entity(tableName = "channels_fts")
@Fts4(contentEntity = ChannelEntity::class)
data class ChannelFtsEntity(
    val name: String,
    val categoryName: String
)

@Entity(tableName = "series_fts")
@Fts4(contentEntity = SeriesEntity::class)
data class SeriesFtsEntity(
    val name: String,
    val title: String,
    val genre: String?,
    val cast: String?,
    val plot: String?
)
