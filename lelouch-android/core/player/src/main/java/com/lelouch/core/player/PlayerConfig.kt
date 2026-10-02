package com.lelouch.core.player

/**
 * Configuration parameters for the Lelouch IPTV Media3 Video Player.
 *
 * Los valores de buffer están inspirados en el repositorio Killua (IPTV-by-Killua),
 * probados contra servidores IPTV reales para evitar congelamiento por "hambre de paquetes":
 * - minBufferMs: colchón mínimo de 8s para absorber picos de latencia sin congelar
 * - maxBufferMs: acumulación máxima de 35s para tener reserva ante caídas prolongadas
 * - bufferForPlaybackMs: 1.5s antes de iniciar reproducción (evita inicio prematuro)
 * - bufferForPlaybackAfterRebufferMs: 2.5s antes de reanudar tras corte (estabiliza el stream)
 * - autoRetryCount: reintentos automáticos transparentes ante fallos de red o fragmentos perdidos
 */
data class PlayerConfig(
    val userAgent: String = "IPTVSmartersPlayer",
    val minBufferMs: Int = 8_000,
    val maxBufferMs: Int = 35_000,
    val bufferForPlaybackMs: Int = 1_500,
    val bufferForPlaybackAfterRebufferMs: Int = 2_500,
    val connectTimeoutMs: Int = 10_000,
    val readTimeoutMs: Int = 10_000,
    val enableHardwareAcceleration: Boolean = true,
    val allowChunklessPreparation: Boolean = false,
    val autoRetryCount: Int = 3
)
