package com.lelouch.core.player

/**
 * Configuration parameters for the Lelouch IPTV Media3 Video Player.
 */
data class PlayerConfig(
    val userAgent: String = "IPTVSmartersPlayer",
    val minBufferMs: Int = 2500,
    val maxBufferMs: Int = 15000,
    val bufferForPlaybackMs: Int = 1000,
    val bufferForPlaybackAfterRebufferMs: Int = 2000,
    val connectTimeoutMs: Int = 15000,
    val readTimeoutMs: Int = 15000,
    val enableHardwareAcceleration: Boolean = true,
    val allowChunklessPreparation: Boolean = true
)
