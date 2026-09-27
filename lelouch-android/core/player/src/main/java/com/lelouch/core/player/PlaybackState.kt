package com.lelouch.core.player

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Buffering : PlaybackState
    data class Playing(
        val positionMs: Long,
        val durationMs: Long,
        val isLive: Boolean
    ) : PlaybackState
    data class Paused(
        val positionMs: Long,
        val durationMs: Long,
        val isLive: Boolean
    ) : PlaybackState
    data object Ended : PlaybackState
    data class Error(
        val errorMessage: String,
        val errorCode: Int = -1,
        val canRetry: Boolean = true
    ) : PlaybackState
}

data class VideoTrackInfo(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val bitrate: Int = 0,
    val mimeType: String? = null
) {
    val resolutionLabel: String
        get() = when {
            height >= 2160 -> "4K UHD"
            height >= 1080 -> "1080p FHD"
            height >= 720 -> "720p HD"
            height > 0 -> "${height}p"
            else -> "Auto"
        }
}

data class AudioTrackInfo(
    val channelCount: Int = 2,
    val sampleRate: Int = 48000,
    val language: String? = null,
    val mimeType: String? = null
)
