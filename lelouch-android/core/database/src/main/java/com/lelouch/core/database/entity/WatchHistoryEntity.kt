package com.lelouch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["contentId", "sourceId"], unique = true),
        Index(value = ["lastWatchedTimestamp"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey
    val id: String, // "$sourceId-$contentId"
    val contentId: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val contentType: String, // "LIVE", "VOD", "SERIES"
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val sourceId: String = ""
)
