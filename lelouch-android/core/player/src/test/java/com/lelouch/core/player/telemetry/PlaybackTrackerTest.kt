package com.lelouch.core.player.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackerTest {

    @Test
    fun `testSuccessTimelineCalculation validates mathematical stage accuracy`() {
        var simulatedClock = 1000L
        val tracker = PlaybackTracker(clock = { simulatedClock })

        // T0 = 1000
        val attempt = tracker.startAttempt(
            url = "http://iptv.server.io:8080/live/user/pass/123.m3u8",
            mediaType = PlaybackMediaType.LIVE_CHANNEL,
            startType = PlayerStartType.COLD_START,
            t0Override = 1000L
        )
        val id = attempt.attemptId

        // T1 = 1050 (URL resolution done)
        simulatedClock = 1050L
        attempt.t1UrlResolved = 1050L

        // T2 = 1060 (MediaItem set)
        simulatedClock = 1060L
        tracker.onMediaItemSet(id)

        // T3 = 1070 (prepare() called)
        simulatedClock = 1070L
        tracker.onPrepareCalled(id)

        // T4 = 1100 (first STATE_BUFFERING)
        simulatedClock = 1100L
        tracker.onBuffering(id)

        // T5 = 1800 (first STATE_READY)
        simulatedClock = 1800L
        tracker.onReady(id)

        // T6 = 1860 (onRenderedFirstFrame)
        simulatedClock = 1860L
        tracker.onRenderedFirstFrame(id)

        // Validaciones exactas requeridas por la especificación:
        assertEquals(PlaybackAttemptResult.SUCCESS_FIRST_FRAME, attempt.result)
        assertEquals(50L, attempt.urlResolutionMs)        // T1 - T0 = 1050 - 1000 = 50ms
        assertEquals(20L, attempt.setupMs)                // T3 - T1 = 1070 - 1050 = 20ms
        assertEquals(30L, attempt.prepareToBufferingMs)  // T4 - T3 = 1100 - 1070 = 30ms
        assertEquals(700L, attempt.bufferingToReadyMs)   // T5 - T4 = 1800 - 1100 = 700ms
        assertEquals(60L, attempt.readyToFirstFrameMs)   // T6 - T5 = 1860 - 1800 = 60ms
        assertEquals(790L, attempt.prepareToFirstFrameMs) // T6 - T3 = 1860 - 1070 = 790ms
        assertEquals(860L, attempt.ttffMs)                // T6 - T0 = 1860 - 1000 = 860ms
    }

    @Test
    fun `testErrorBeforePrepare records error and halts attempt`() {
        var clockTime = 5000L
        val tracker = PlaybackTracker(clock = { clockTime })

        val attempt = tracker.startAttempt("", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.COLD_START)
        tracker.onPlayerError(attempt.attemptId, -1004, "Invalid URL")

        assertEquals(PlaybackAttemptResult.PLAYER_ERROR, attempt.result)
        assertEquals(-1004, attempt.errorCode)
        assertEquals("Invalid URL", attempt.errorMessage)
        assertNull(attempt.ttffMs)
    }

    @Test
    fun `testErrorDuringBuffering records stage and code`() {
        var clockTime = 1000L
        val tracker = PlaybackTracker(clock = { clockTime })

        val attempt = tracker.startAttempt("http://server/live/stream.m3u8", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
        clockTime = 1050L
        tracker.onPrepareCalled(attempt.attemptId)
        clockTime = 1100L
        tracker.onBuffering(attempt.attemptId)

        clockTime = 1500L
        tracker.onPlayerError(attempt.attemptId, 403, "HTTP Forbidden")

        assertEquals(PlaybackAttemptResult.PLAYER_ERROR, attempt.result)
        assertEquals(403, attempt.errorCode)
        assertNotNull(attempt.t4FirstBuffering)
        assertNull(attempt.t5FirstReady)
        assertNull(attempt.t6FirstFrame)
    }

    @Test
    fun `testUserCancel marks attempt as user cancel`() {
        var clockTime = 2000L
        val tracker = PlaybackTracker(clock = { clockTime })

        val attempt = tracker.startAttempt("http://server/vod/film.mp4", PlaybackMediaType.MOVIE, PlayerStartType.COLD_START)
        clockTime = 2200L
        tracker.onUserCancel(attempt.attemptId)

        assertEquals(PlaybackAttemptResult.USER_CANCEL, attempt.result)
    }

    @Test
    fun `testRapidChannelSwitchIsolation isolates fast zapping and ignores late callbacks`() {
        var clockTime = 1000L
        val tracker = PlaybackTracker(clock = { clockTime })

        // Channel A starts at T=1000
        val attemptA = tracker.startAttempt("http://server/live/A.ts", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
        clockTime = 1050L
        tracker.onPrepareCalled(attemptA.attemptId)

        // 150ms later, user zaps to Channel B at T=1150
        clockTime = 1150L
        val attemptB = tracker.startAttempt("http://server/live/B.ts", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
        assertEquals(PlaybackAttemptResult.CHANNEL_CHANGED_BEFORE_READY, attemptA.result)

        // 200ms later, user zaps to Channel C at T=1350
        clockTime = 1350L
        val attemptC = tracker.startAttempt("http://server/live/C.ts", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
        assertEquals(PlaybackAttemptResult.CHANNEL_CHANGED_BEFORE_READY, attemptB.result)

        // Late callback arrives from Channel A!
        tracker.onRenderedFirstFrame(attemptA.attemptId)
        // Verify late callback did NOT revive attempt A or affect attempt C
        assertEquals(PlaybackAttemptResult.CHANNEL_CHANGED_BEFORE_READY, attemptA.result)
        assertNull(attemptC.result)

        // Channel C proceeds normally
        clockTime = 1400L
        tracker.onPrepareCalled(attemptC.attemptId)
        clockTime = 1450L
        tracker.onBuffering(attemptC.attemptId)
        clockTime = 1800L
        tracker.onReady(attemptC.attemptId)
        clockTime = 1880L
        tracker.onRenderedFirstFrame(attemptC.attemptId)

        assertEquals(PlaybackAttemptResult.SUCCESS_FIRST_FRAME, attemptC.result)
        assertEquals(530L, attemptC.ttffMs) // 1880 - 1350 = 530ms
    }

    @Test
    fun `testWatchdogEventsAndSkip tracks watchdog phases`() {
        var clockTime = 1000L
        val tracker = PlaybackTracker(clock = { clockTime })

        val attempt = tracker.startAttempt("http://server/live/slow.ts", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
        clockTime = 1010L
        tracker.onPrepareCalled(attempt.attemptId)
        clockTime = 1050L
        tracker.onBuffering(attempt.attemptId)
        tracker.onWatchdogEvent(attempt.attemptId, WatchdogEventType.WATCHDOG_STARTED)

        // 3s later: bitrate reduction
        clockTime = 4050L
        tracker.onWatchdogEvent(attempt.attemptId, WatchdogEventType.WATCHDOG_BITRATE_REDUCTION, "MaxBitrate=1.5M")

        // 7s later: skip
        clockTime = 8050L
        tracker.onWatchdogEvent(attempt.attemptId, WatchdogEventType.WATCHDOG_SKIP, "Stream frozen >7s")

        assertEquals(PlaybackAttemptResult.WATCHDOG_SKIP, attempt.result)
        assertEquals(3, attempt.watchdogEvents.size)
        assertEquals(WatchdogEventType.WATCHDOG_STARTED, attempt.watchdogEvents[0].type)
        assertEquals(WatchdogEventType.WATCHDOG_BITRATE_REDUCTION, attempt.watchdogEvents[1].type)
        assertEquals(WatchdogEventType.WATCHDOG_SKIP, attempt.watchdogEvents[2].type)
    }

    @Test
    fun `testAudioOnlySuccess records audio first frame`() {
        var clockTime = 1000L
        val tracker = PlaybackTracker(clock = { clockTime })

        val attempt = tracker.startAttempt("http://server/radio/stream.aac", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.COLD_START)
        clockTime = 1050L
        tracker.onPrepareCalled(attempt.attemptId)
        clockTime = 1100L
        tracker.onBuffering(attempt.attemptId)
        clockTime = 1450L
        tracker.onReady(attempt.attemptId)
        tracker.onAudioOnlySuccess(attempt.attemptId)

        assertEquals(PlaybackAttemptResult.AUDIO_ONLY_SUCCESS, attempt.result)
        assertEquals(450L, attempt.ttffMs)
    }

    @Test
    fun `testRingBufferHistoryCappedAtMax`() {
        var clockTime = 100L
        val tracker = PlaybackTracker(clock = { clockTime })

        for (i in 1..120) {
            clockTime += 50
            val attempt = tracker.startAttempt("http://server/ch_$i.ts", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
            tracker.onRenderedFirstFrame(attempt.attemptId)
        }

        val history = tracker.getHistory()
        assertEquals(PlaybackTracker.MAX_HISTORY, history.size)
        assertEquals(21L, history.first().attemptId)
        assertEquals(120L, history.last().attemptId)
    }

    @Test
    fun `testUrlSanitizationProtectsCredentials`() {
        var clockTime = 100L
        val tracker = PlaybackTracker(clock = { clockTime })

        val attempt = tracker.startAttempt(
            "http://iptv.provider.com:8080/live/agent007/supersecret/channel99.ts?token=1234567890abcdef",
            PlaybackMediaType.LIVE_CHANNEL,
            PlayerStartType.COLD_START
        )

        assertFalse(attempt.sanitizedUrl.contains("agent007"))
        assertFalse(attempt.sanitizedUrl.contains("supersecret"))
        assertTrue(attempt.sanitizedUrl.contains("/live/***/***/channel99.ts"))
        assertTrue(attempt.sanitizedUrl.contains("token=1234…"))
    }

    @Test
    fun `testSimulatedBenchmarkDevMachineEvaluatesPercentiles`() {
        val ttffList = mutableListOf<Long>()
        val urlList = mutableListOf<Long>()
        val bufReadyList = mutableListOf<Long>()
        val readyFrameList = mutableListOf<Long>()

        var clock = 10_000L
        val tracker = PlaybackTracker(clock = { clock })

        // 10 Cold Starts
        for (i in 1..10) {
            val urlDelay = 5L + (i % 3) * 2L
            val setupDelay = 10L
            val prepBufDelay = 20L
            val bufReadyDelay = 800L + (i * 25L)
            val readyFrameDelay = 60L + (i * 5L)

            val attempt = tracker.startAttempt("http://server/cold_$i.m3u8", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.COLD_START)
            clock += urlDelay
            attempt.t1UrlResolved = clock
            clock += setupDelay
            tracker.onPrepareCalled(attempt.attemptId)
            clock += prepBufDelay
            tracker.onBuffering(attempt.attemptId)
            clock += bufReadyDelay
            tracker.onReady(attempt.attemptId)
            clock += readyFrameDelay
            tracker.onRenderedFirstFrame(attempt.attemptId)

            ttffList.add(attempt.ttffMs!!)
            urlList.add(attempt.urlResolutionMs!!)
            bufReadyList.add(attempt.bufferingToReadyMs!!)
            readyFrameList.add(attempt.readyToFirstFrameMs!!)
        }

        // 30 Warm Channel Switches
        for (i in 1..30) {
            val urlDelay = 2L + (i % 2)
            val setupDelay = 5L
            val prepBufDelay = 15L
            val bufReadyDelay = 450L + (i * 10L)
            val readyFrameDelay = 40L + (i * 2L)

            val attempt = tracker.startAttempt("http://server/warm_$i.ts", PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
            clock += urlDelay
            attempt.t1UrlResolved = clock
            clock += setupDelay
            tracker.onPrepareCalled(attempt.attemptId)
            clock += prepBufDelay
            tracker.onBuffering(attempt.attemptId)
            clock += bufReadyDelay
            tracker.onReady(attempt.attemptId)
            clock += readyFrameDelay
            tracker.onRenderedFirstFrame(attempt.attemptId)

            ttffList.add(attempt.ttffMs!!)
            urlList.add(attempt.urlResolutionMs!!)
            bufReadyList.add(attempt.bufferingToReadyMs!!)
            readyFrameList.add(attempt.readyToFirstFrameMs!!)
        }

        assertEquals(40, ttffList.size)

        // Percentiles calculations
        fun percentile(list: List<Long>, p: Double): Long {
            val sorted = list.sorted()
            val index = ((sorted.size - 1) * p).toInt()
            return sorted[index]
        }

        val medianTtff = percentile(ttffList, 0.50)
        val p95Ttff = percentile(ttffList, 0.95)
        val medianUrl = percentile(urlList, 0.50)
        val medianBufReady = percentile(bufReadyList, 0.50)
        val medianReadyFrame = percentile(readyFrameList, 0.50)

        assertTrue(medianTtff in 500..850)
        assertTrue(p95Ttff in 850..1200)
        assertTrue(medianUrl in 1..10)
        assertTrue(medianBufReady in 450..850)
        assertTrue(medianReadyFrame in 40..85)
    }
}
