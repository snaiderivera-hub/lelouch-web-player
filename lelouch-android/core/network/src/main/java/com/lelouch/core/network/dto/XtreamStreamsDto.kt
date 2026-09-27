package com.lelouch.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamCategoryDto(
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
    @SerialName("parent_id")
    val parentId: Int = 0
)

@Serializable
data class XtreamLiveStreamDto(
    val num: Int = 0,
    val name: String = "",
    @SerialName("stream_type")
    val streamType: String = "live",
    @SerialName("stream_id")
    val streamId: Int,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    @SerialName("epg_channel_id")
    val epgChannelId: String? = null,
    @SerialName("added")
    val added: String? = null,
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("custom_sid")
    val customSid: String? = null,
    @SerialName("tv_archive")
    val tvArchive: Int = 0,
    @SerialName("direct_source")
    val directSource: String? = null
)

@Serializable
data class XtreamVodStreamDto(
    val num: Int = 0,
    val name: String = "",
    @SerialName("title")
    val title: String? = null,
    @SerialName("year")
    val year: String? = null,
    @SerialName("stream_type")
    val streamType: String = "movie",
    @SerialName("stream_id")
    val streamId: Int,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    @SerialName("rating")
    val rating: String? = null,
    @SerialName("rating_5based")
    val rating5based: Double? = null,
    @SerialName("added")
    val added: String? = null,
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("container_extension")
    val containerExtension: String = "mp4",
    @SerialName("custom_sid")
    val customSid: String? = null,
    @SerialName("direct_source")
    val directSource: String? = null
)

@Serializable
data class XtreamSeriesDto(
    val num: Int = 0,
    val name: String = "",
    @SerialName("title")
    val title: String? = null,
    @SerialName("series_id")
    val seriesId: Int,
    @SerialName("cover")
    val cover: String? = null,
    @SerialName("plot")
    val plot: String? = null,
    @SerialName("cast")
    val cast: String? = null,
    @SerialName("director")
    val director: String? = null,
    @SerialName("genre")
    val genre: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("rating")
    val rating: String? = null,
    @SerialName("rating_5based")
    val rating5based: Double? = null,
    @SerialName("category_id")
    val categoryId: String = ""
)
