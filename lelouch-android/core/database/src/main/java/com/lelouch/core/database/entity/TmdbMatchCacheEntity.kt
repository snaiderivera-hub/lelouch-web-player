package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tmdb_match_cache")
data class TmdbMatchCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val releaseYear: String?,
    val updatedAt: Long
)
