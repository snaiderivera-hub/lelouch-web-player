package com.lelouch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Series(
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
    val seasonsCount: Int = 0,
    val episodeCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastWatchedEpisode: Episode? = null
)

@Serializable
data class Episode(
    val id: String,
    val episodeId: Int,
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String = "mp4",
    val info: String? = null,
    val plot: String? = null,
    val durationSecs: Int = 0,
    val durationFormatted: String = "",
    val cover: String? = null,
    val streamUrl: String = "",
    val watchProgress: WatchProgress? = null
)
