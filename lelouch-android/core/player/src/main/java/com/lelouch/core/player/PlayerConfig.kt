package com.lelouch.core.player

/**
 * Configuration parameters for the Lelouch IPTV Media3 Video Player.
 *
 * Los valores de buffer han sido ajustados para **Fast Zapping** (inicio de reproducción rápido):
 * - minBufferMs: colchón mínimo de 2s para absorber latencia sin retrasar el inicio
 * - maxBufferMs: acumulación máxima de 35s
 * - bufferForPlaybackMs: 0.5s antes de iniciar reproducción (para zapping casi instantáneo)
 * - bufferForPlaybackAfterRebufferMs: 1.0s antes de reanudar tras corte
 * - autoRetryCount: reintentos automáticos
 */
data class PlayerConfig(
    val userAgent: String = "IPTVSmartersPlayer",
    val minBufferMs: Int = 2_000,
    val maxBufferMs: Int = 35_000,
    val bufferForPlaybackMs: Int = 500,
    val bufferForPlaybackAfterRebufferMs: Int = 1_000,
    val connectTimeoutMs: Int = 10_000,
    val readTimeoutMs: Int = 10_000,
    val enableHardwareAcceleration: Boolean = true,
    val allowChunklessPreparation: Boolean = false,
    val autoRetryCount: Int = 3
)
