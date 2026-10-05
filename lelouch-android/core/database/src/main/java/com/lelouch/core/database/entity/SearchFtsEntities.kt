package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Entity(tableName = "movies_fts")
@Fts4(
    contentEntity = MovieEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"]
)
data class MovieFtsEntity(
    val name: String,
    val title: String,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val plot: String?
)

@Entity(tableName = "channels_fts")
@Fts4(
    contentEntity = ChannelEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"]
)
data class ChannelFtsEntity(
    val name: String,
    val categoryName: String
)

@Entity(tableName = "series_fts")
@Fts4(
    contentEntity = SeriesEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"]
)
data class SeriesFtsEntity(
    val name: String,
    val title: String,
    val genre: String?,
    val cast: String?,
    val plot: String?
)
