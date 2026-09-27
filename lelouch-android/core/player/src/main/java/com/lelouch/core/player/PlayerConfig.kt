package com.lelouch.core.player

/**
 * Configuration parameters for the Lelouch IPTV Media3 Video Player.
 */
data class PlayerConfig(
    val userAgent: String = "IPTVSmartersPlayer",
    val minBufferMs: Int = 1500,
    val maxBufferMs: Int = 15000,
    val bufferForPlaybackMs: Int = 250,
    val bufferForPlaybackAfterRebufferMs: Int = 800,
    val connectTimeoutMs: Int = 8000,
    val readTimeoutMs: Int = 8000,
    val enableHardwareAcceleration: Boolean = true,
    val allowChunklessPreparation: Boolean = true
)
