package com.lelouch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveStream(
    val id: String,
    val streamId: Int,
    val num: Int = 0,
    val name: String,
    val streamType: String = "live",
    val streamIcon: String? = null,
    val categoryId: String,
    val categoryName: String = "",
    val epgChannelId: String? = null,
    val isAdult: Boolean = false,
    val isFavorite: Boolean = false,
    val streamUrl: String = "",
    val containerExtension: String = "ts",
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null
)
