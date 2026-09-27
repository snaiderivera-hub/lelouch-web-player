package com.lelouch.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType {
    LIVE,
    VOD,
    SERIES
}

@Serializable
data class Category(
    val categoryId: String,
    val categoryName: String,
    val parentId: Int = 0,
    val type: ContentType,
    val itemCount: Int = 0,
    val isAdult: Boolean = false
)
