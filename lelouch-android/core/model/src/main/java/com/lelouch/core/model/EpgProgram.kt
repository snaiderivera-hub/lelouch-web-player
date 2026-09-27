package com.lelouch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EpgProgram(
    val id: String,
    val epgId: String? = null,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTimestamp: Long,
    val stopTimestamp: Long,
    val startTimeFormatted: String = "",
    val stopTimeFormatted: String = "",
    val progressPercent: Float = 0f
)
