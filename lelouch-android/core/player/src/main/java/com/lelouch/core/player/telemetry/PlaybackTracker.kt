package com.lelouch.core.player.telemetry

import android.os.SystemClock
import android.util.Log
import com.lelouch.core.network.UrlSanitizer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe observability engine for playback attempts, stage timings, and TTFF metrics.
 */
class PlaybackTracker(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    companion object {
        const val MAX_HISTORY = 100
        private const val TAG = "LelouchTTFF"
    }

    private val attemptCounter = AtomicLong(0)
    private val activeAttempt = AtomicReference<PlaybackAttemptMetrics?>(null)
    private val historyLock = Any()
    private val history = ArrayDeque<PlaybackAttemptMetrics>(MAX_HISTORY)

    fun startAttempt(
        url: String,
        mediaType: PlaybackMediaType,
        startType: PlayerStartType,
        t0Override: Long? = null
    ): PlaybackAttemptMetrics {
        val now = clock()
        val t0 = t0Override ?: now
        val id = attemptCounter.incrementAndGet()

        // Close previous attempt if it was still pending
        activeAttempt.get()?.let { prev ->
            if (!prev.isComplete()) {
                prev.result = PlaybackAttemptResult.CHANNEL_CHANGED_BEFORE_READY
                logAttemptSummary(prev)
                archiveAttempt(prev)
            }
        }

        val protocol = detectProtocol(url)
        val sanitized = UrlSanitizer.sanitizeUrl(url)

        val newAttempt = PlaybackAttemptMetrics(
            attemptId = id,
            mediaType = mediaType,
            protocol = protocol,
            startType = startType,
            sanitizedUrl = sanitized,
            t0UserAction = t0,
            t1UrlResolved = now
        )

        activeAttempt.set(newAttempt)
        return newAttempt
    }

    fun onMediaItemSet(attemptId: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.t2MediaItemSet == null) {
            attempt.t2MediaItemSet = clock()
        }
    }

    fun onPrepareCalled(attemptId: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.t3Prepare == null) {
            attempt.t3Prepare = clock()
        }
    }

    fun onBuffering(attemptId: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.t4FirstBuffering == null) {
            attempt.t4FirstBuffering = clock()
        }
    }

    fun onReady(attemptId: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.t5FirstReady == null) {
            attempt.t5FirstReady = clock()
        }
    }

    fun onRenderedFirstFrame(attemptId: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.t6FirstFrame == null) {
            attempt.t6FirstFrame = clock()
            attempt.result = PlaybackAttemptResult.SUCCESS_FIRST_FRAME
            logAttemptSummary(attempt)
            archiveAttempt(attempt)
        }
    }

    fun onAudioOnlySuccess(attemptId: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.result == null) {
            attempt.t6FirstFrame = clock()
            attempt.result = PlaybackAttemptResult.AUDIO_ONLY_SUCCESS
            logAttemptSummary(attempt)
            archiveAttempt(attempt)
        }
    }

    fun onPlayerError(attemptId: Long, errorCode: Int, message: String?) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.result == null) {
            attempt.errorCode = errorCode
            attempt.errorMessage = message
            attempt.result = PlaybackAttemptResult.PLAYER_ERROR
            logAttemptSummary(attempt)
            archiveAttempt(attempt)
        }
    }

    fun onWatchdogEvent(attemptId: Long, type: WatchdogEventType, details: String? = null) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        val elapsed = (clock() - attempt.t0UserAction).coerceAtLeast(0L)
        val event = WatchdogEvent(type, elapsed, details)
        attempt.watchdogEvents.add(event)

        if (type == WatchdogEventType.WATCHDOG_SKIP && attempt.result == null) {
            attempt.result = PlaybackAttemptResult.WATCHDOG_SKIP
            logAttemptSummary(attempt)
            archiveAttempt(attempt)
        }
    }

    fun onUserCancel(attemptId: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.result == null) {
            attempt.result = PlaybackAttemptResult.USER_CANCEL
            logAttemptSummary(attempt)
            archiveAttempt(attempt)
        }
    }

    fun onVideoDecoderInitialized(attemptId: Long, elapsedMs: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        attempt.videoDecoderInitElapsedMs = elapsedMs
    }

    fun onAudioDecoderInitialized(attemptId: Long, elapsedMs: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        attempt.audioDecoderInitElapsedMs = elapsedMs
    }

    fun onManifestLoaded(attemptId: Long, durationMs: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.manifestLoadDurationMs == null) {
            attempt.manifestLoadDurationMs = durationMs
        }
    }

    fun onFirstSegmentLoaded(attemptId: Long, durationMs: Long) {
        val attempt = getActiveIfMatches(attemptId) ?: return
        if (attempt.firstSegmentLoadDurationMs == null) {
            attempt.firstSegmentLoadDurationMs = durationMs
        }
    }

    fun getActiveAttempt(): PlaybackAttemptMetrics? = activeAttempt.get()

    fun getHistory(): List<PlaybackAttemptMetrics> {
        synchronized(historyLock) {
            return history.toList()
        }
    }

    private fun getActiveIfMatches(attemptId: Long): PlaybackAttemptMetrics? {
        val current = activeAttempt.get()
        return if (current != null && current.attemptId == attemptId) current else null
    }

    private fun archiveAttempt(attempt: PlaybackAttemptMetrics) {
        synchronized(historyLock) {
            if (history.size >= MAX_HISTORY) {
                history.removeFirst()
            }
            history.addLast(attempt)
        }
    }

    private fun logAttemptSummary(attempt: PlaybackAttemptMetrics) {
        val ttff = attempt.ttffMs?.let { "${it}ms" } ?: "N/A"
        val url = attempt.urlResolutionMs?.let { "${it}ms" } ?: "N/A"
        val setup = attempt.setupMs?.let { "${it}ms" } ?: "N/A"
        val bufReady = attempt.bufferingToReadyMs?.let { "${it}ms" } ?: "N/A"
        val readyFrame = attempt.readyToFirstFrameMs?.let { "${it}ms" } ?: "N/A"

        Log.i(
            TAG,
            "[ATTEMPT #${attempt.attemptId}] ${attempt.result} | " +
                "type=${attempt.mediaType} | proto=${attempt.protocol} | start=${attempt.startType} | " +
                "TTFF=$ttff (url=$url, setup=$setup, buf->ready=$bufReady, ready->frame=$readyFrame) | " +
                "watchdogs=${attempt.watchdogEvents.size} | url=${attempt.sanitizedUrl}"
        )
    }

    private fun detectProtocol(url: String): StreamProtocol {
        val clean = url.lowercase()
        return when {
            clean.contains(".m3u8") || clean.contains("/hls/") || clean.contains("m3u8") -> StreamProtocol.HLS
            clean.contains(".ts") || clean.contains("/live/") -> StreamProtocol.MPEG_TS
            clean.contains(".mpd") -> StreamProtocol.DASH
            clean.contains(".mp4") || clean.contains(".mkv") -> StreamProtocol.MP4
            else -> StreamProtocol.OTHER
        }
    }
}
