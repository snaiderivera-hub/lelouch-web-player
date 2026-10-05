package com.lelouch.core.player.telemetry

/**
 * Terminal result of a playback attempt.
 */
enum class PlaybackAttemptResult {
    SUCCESS_FIRST_FRAME,
    PLAYER_ERROR,
    RESOLVE_ERROR,
    WATCHDOG_SKIP,
    USER_CANCEL,
    CHANNEL_CHANGED_BEFORE_READY,
    TIMEOUT,
    AUDIO_ONLY_SUCCESS
}

/**
 * Underlying stream transport protocol.
 */
enum class StreamProtocol {
    HLS,
    MPEG_TS,
    DASH,
    MP4,
    OTHER
}

/**
 * Content category for playback analytics.
 */
enum class PlaybackMediaType {
    LIVE_CHANNEL,
    MOVIE,
    SERIES_EPISODE
}

/**
 * Cold start vs warm channel zapping.
 */
enum class PlayerStartType {
    COLD_START,
    WARM_SWITCH
}

/**
 * Watchdog intervention events.
 */
enum class WatchdogEventType {
    WATCHDOG_STARTED,
    WATCHDOG_BITRATE_REDUCTION,
    WATCHDOG_RETRY,
    WATCHDOG_SKIP,
    WATCHDOG_CANCELLED
}

data class WatchdogEvent(
    val type: WatchdogEventType,
    val elapsedMs: Long,
    val details: String? = null
)

/**
 * Complete metrics model capturing each stage of playback latency (TTFF).
 */
data class PlaybackAttemptMetrics(
    val attemptId: Long,
    val mediaType: PlaybackMediaType,
    val protocol: StreamProtocol,
    val startType: PlayerStartType,
    val sanitizedUrl: String,
    val t0UserAction: Long,
    var t1UrlResolved: Long? = null,
    var t2MediaItemSet: Long? = null,
    var t3Prepare: Long? = null,
    var t4FirstBuffering: Long? = null,
    var t5FirstReady: Long? = null,
    var t6FirstFrame: Long? = null,
    var result: PlaybackAttemptResult? = null,
    var errorCode: Int? = null,
    var errorMessage: String? = null,
    var videoDecoderInitElapsedMs: Long? = null,
    var audioDecoderInitElapsedMs: Long? = null,
    var manifestLoadDurationMs: Long? = null,
    var firstSegmentLoadDurationMs: Long? = null,
    val watchdogEvents: MutableList<WatchdogEvent> = mutableListOf()
) {
    /**
     * Real Time To First Frame (T6 - T0).
     */
    val ttffMs: Long?
        get() {
            val t6 = t6FirstFrame ?: return null
            return (t6 - t0UserAction).coerceAtLeast(0L)
        }

    val urlResolutionMs: Long?
        get() {
            val t1 = t1UrlResolved ?: return null
            return (t1 - t0UserAction).coerceAtLeast(0L)
        }

    val setupMs: Long?
        get() {
            val t3 = t3Prepare ?: return null
            val t1 = t1UrlResolved ?: t0UserAction
            return (t3 - t1).coerceAtLeast(0L)
        }

    val prepareToBufferingMs: Long?
        get() {
            val t4 = t4FirstBuffering ?: return null
            val t3 = t3Prepare ?: return null
            return (t4 - t3).coerceAtLeast(0L)
        }

    val bufferingToReadyMs: Long?
        get() {
            val t5 = t5FirstReady ?: return null
            val t4 = t4FirstBuffering ?: return null
            return (t5 - t4).coerceAtLeast(0L)
        }

    val readyToFirstFrameMs: Long?
        get() {
            val t6 = t6FirstFrame ?: return null
            val t5 = t5FirstReady ?: return null
            return (t6 - t5).coerceAtLeast(0L)
        }

    val prepareToFirstFrameMs: Long?
        get() {
            val t6 = t6FirstFrame ?: return null
            val t3 = t3Prepare ?: return null
            return (t6 - t3).coerceAtLeast(0L)
        }

    fun isComplete(): Boolean = result != null
}
