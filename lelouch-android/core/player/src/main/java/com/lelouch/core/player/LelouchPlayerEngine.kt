package com.lelouch.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.lelouch.core.player.telemetry.PlaybackAttemptMetrics
import com.lelouch.core.player.telemetry.PlaybackAttemptResult
import com.lelouch.core.player.telemetry.PlaybackMediaType
import com.lelouch.core.player.telemetry.PlaybackTracker
import com.lelouch.core.player.telemetry.PlayerStartType
import com.lelouch.core.player.telemetry.WatchdogEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Native IPTV video player engine powered by AndroidX Media3 ExoPlayer.
 * Tailored for low-latency live IPTV streams, hardware accelerated decoding, and automatic HLS/MPEG-TS negotiation.
 */
@OptIn(UnstableApi::class)
class LelouchPlayerEngine(
    private val context: Context,
    private val config: PlayerConfig = PlayerConfig(),
    val playbackTracker: PlaybackTracker = PlaybackTracker()
) {
    private var currentAttemptId: Long = 0L
    private var isFirstPlayback: Boolean = true
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackingJob: Job? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _videoTrackInfo = MutableStateFlow(VideoTrackInfo())
    val videoTrackInfo: StateFlow<VideoTrackInfo> = _videoTrackInfo.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val httpDataSourceFactory: DefaultHttpDataSource.Factory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent(config.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(config.connectTimeoutMs)
            .setReadTimeoutMs(config.readTimeoutMs)
            .setKeepPostFor302Redirects(true)
    }

    private val loadControl by lazy {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                config.minBufferMs,
                config.maxBufferMs,
                config.bufferForPlaybackMs,
                config.bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    private val renderersFactory by lazy {
        DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            if (config.enableHardwareAcceleration) {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }
        }
    }

    private val trackSelector by lazy {
        // ABR adaptativo agresivo para IPTV:
        // - minDurationForQualityIncreaseMs = 12s para subir de resolución (evita fluctuaciones)
        // - maxDurationForQualityDecreaseMs = 5s (si el buffer decae a menos de 5s, baja calidad de inmediato)
        // - minDurationToRetainAfterDiscardMs = 3s (descarta paquetes pesados para no congelar la imagen)
        // - bandwidthFraction = 0.75f (utiliza hasta el 75% del ancho de banda disponible)
        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            12_000,
            5_000,
            3_000,
            0.75f
        )
        DefaultTrackSelector(context, adaptiveTrackSelectionFactory).also { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            playbackTracker.onVideoDecoderInitialized(currentAttemptId, initializationDurationMs)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            playbackTracker.onAudioDecoderInitialized(currentAttemptId, initializationDurationMs)
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            if (mediaLoadData.dataType == C.DATA_TYPE_MANIFEST) {
                playbackTracker.onManifestLoaded(currentAttemptId, loadEventInfo.loadDurationMs)
            } else if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA) {
                playbackTracker.onFirstSegmentLoaded(currentAttemptId, loadEventInfo.loadDurationMs)
            }
        }
    }

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
            }
    }

    private var isCurrentStreamLive: Boolean = false
    // Contador de reintentos automáticos ante fallos de red (se reinicia al cambiar de canal)
    private var autoRetryAttemptsLeft: Int = config.autoRetryCount
    private var bufferingWatchdogJob: Job? = null
    var onChannelUnavailable: (() -> Unit)? = null

    private fun startBufferingWatchdog() {
        stopBufferingWatchdog()
        if (!isCurrentStreamLive) return
        val watchdogAttemptId = currentAttemptId
        playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_STARTED)
        bufferingWatchdogJob = scope.launch {
            // FASE 1 (3 segundos de buffering):
            // Reducir temporalmente la calidad de video al bitrate más bajo (720p/480p) para salvar la transmisión sin cortar.
            delay(3_000L)
            if (!isActive || !isCurrentStreamLive) return@launch

            playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_BITRATE_REDUCTION, "Bitrate -> 1.5Mbps")
            android.util.Log.w("LelouchPlayer", "Buffer en riesgo (>3s). Reduciendo calidad de video (ABR) para evitar corte...")
            try {
                trackSelector.setParameters(
                    trackSelector.parameters.buildUpon().setMaxVideoBitrate(1_500_000)
                )
            } catch (_: Exception) {}

            // FASE 2 (7 segundos continuos en buffering):
            // Si el buffer no se recuperó a menor bitrate, intentar reconectar stream.
            delay(4_000L)
            if (!isActive || !isCurrentStreamLive) return@launch

            if (autoRetryAttemptsLeft > 0) {
                autoRetryAttemptsLeft--
                playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_RETRY, "Reconnect attempt")
                android.util.Log.w("LelouchPlayer", "Stream congelado >7s. Intentando reconectar señal...")
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                delay(5_000L)
                if (exoPlayer.playbackState == Player.STATE_BUFFERING && isActive && isCurrentStreamLive) {
                    playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_SKIP, "Canal sin señal tras reconexión")
                    android.util.Log.e("LelouchPlayer", "Canal sin señal tras reconexión. Saltando al siguiente canal...")
                    triggerChannelUnavailable("Señal caída (stream congelado). Saltando al siguiente canal...")
                }
            } else {
                playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_SKIP, "Canal congelado >7s")
                android.util.Log.e("LelouchPlayer", "Señal IPTV caída (>7s congelado). Saltando al siguiente canal...")
                triggerChannelUnavailable("Señal caída (stream congelado). Saltando al siguiente canal...")
            }
        }
    }

    private fun stopBufferingWatchdog() {
        if (bufferingWatchdogJob != null) {
            playbackTracker.onWatchdogEvent(currentAttemptId, WatchdogEventType.WATCHDOG_CANCELLED)
        }
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
    }

    private fun triggerChannelUnavailable(reason: String) {
        stopBufferingWatchdog()
        stopProgressTracking()
        _playbackState.value = PlaybackState.Error(
            errorMessage = reason,
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            canRetry = true
        )
        onChannelUnavailable?.invoke()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> {
                    stopBufferingWatchdog()
                    _playbackState.value = PlaybackState.Idle
                    stopProgressTracking()
                }
                Player.STATE_BUFFERING -> {
                    playbackTracker.onBuffering(currentAttemptId)
                    _playbackState.value = PlaybackState.Buffering
                    startBufferingWatchdog()
                }
                Player.STATE_READY -> {
                    playbackTracker.onReady(currentAttemptId)
                    stopBufferingWatchdog()
                    autoRetryAttemptsLeft = config.autoRetryCount
                    val isPlaying = exoPlayer.playWhenReady
                    updateState(isPlaying)
                    if (isPlaying) {
                        startProgressTracking()
                    } else {
                        stopProgressTracking()
                    }
                    if (exoPlayer.videoFormat == null && exoPlayer.currentTracks.isTypeSupported(C.TRACK_TYPE_AUDIO)) {
                        playbackTracker.onAudioOnlySuccess(currentAttemptId)
                    }
                }
                Player.STATE_ENDED -> {
                    stopBufferingWatchdog()
                    _playbackState.value = PlaybackState.Ended
                    stopProgressTracking()
                }
            }
        }

        override fun onRenderedFirstFrame() {
            playbackTracker.onRenderedFirstFrame(currentAttemptId)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                updateState(isPlaying)
                if (isPlaying) startProgressTracking() else stopProgressTracking()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _videoTrackInfo.value = _videoTrackInfo.value.copy(
                width = videoSize.width,
                height = videoSize.height
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackTracker.onPlayerError(currentAttemptId, error.errorCode, error.message)
            stopBufferingWatchdog()
            stopProgressTracking()
            // Auto-recuperación silenciosa para errores de red y timeout:
            // Si hay una URL activa y es un stream en vivo, reintentamos automáticamente
            // después de 2 segundos sin molestar al usuario, igual al modelo Killua.
            val isNetworkError = error.errorCode in setOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            )
            val currentStreamUrl = _currentUrl.value
            if (isNetworkError && isCurrentStreamLive && currentStreamUrl != null && autoRetryAttemptsLeft > 0) {
                autoRetryAttemptsLeft--
                _playbackState.value = PlaybackState.Buffering
                scope.launch {
                    delay(2_000L)
                    if (_currentUrl.value == currentStreamUrl) {
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                }
                return
            }
            // Reset retry counter si llegamos a un error fatal
            autoRetryAttemptsLeft = config.autoRetryCount
            val errorDescription = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Sin conexión al servidor IPTV. Saltando canal..."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "Canal no disponible (Error 403/404). Saltando al siguiente..."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                    "Formato de stream incompatible. Saltando al siguiente..."
                else -> error.localizedMessage ?: "Error durante la reproducción."
            }
            _playbackState.value = PlaybackState.Error(
                errorMessage = errorDescription,
                errorCode = error.errorCode,
                canRetry = true
            )
            // Auto-salto si el canal está caído/muerto
            if (isCurrentStreamLive) {
                scope.launch {
                    delay(1_200L)
                    if (currentStreamUrl == _currentUrl.value) {
                        onChannelUnavailable?.invoke()
                    }
                }
            }
        }
    }

    /**
     * Sintoniza y reproduce una URL de transmisión IPTV en vivo o VOD bajo demanda.
     */
    fun playStream(url: String, isLive: Boolean = false, userT0: Long? = null) {
        val mediaType = if (isLive) PlaybackMediaType.LIVE_CHANNEL else PlaybackMediaType.MOVIE
        val startType = if (isFirstPlayback) PlayerStartType.COLD_START else PlayerStartType.WARM_SWITCH
        isFirstPlayback = false

        val attempt = playbackTracker.startAttempt(url, mediaType, startType, userT0)
        currentAttemptId = attempt.attemptId

        // GUARDIA (FASE 33): nunca preparar ExoPlayer con una URL vacía o malformada.
        if (url.isBlank()) {
            _currentUrl.value = null
            autoRetryAttemptsLeft = config.autoRetryCount
            playbackTracker.onPlayerError(currentAttemptId, PlaybackException.ERROR_CODE_BAD_VALUE, "URL vacía")
            _playbackState.value = PlaybackState.Error(
                errorMessage = "Este elemento no tiene enlace de reproducción.",
                errorCode = PlaybackException.ERROR_CODE_BAD_VALUE,
                canRetry = false
            )
            if (isLive) {
                scope.launch {
                    delay(500L)
                    onChannelUnavailable?.invoke()
                }
            }
            return
        }

        if (url == _currentUrl.value && exoPlayer.playbackState == Player.STATE_READY) {
            if (!exoPlayer.playWhenReady) {
                exoPlayer.playWhenReady = true
            }
            return
        }

        // Cancelar buffer previo y reiniciar el contador de reintentos para el nuevo canal
        exoPlayer.stop()
        _currentUrl.value = url
        isCurrentStreamLive = isLive
        autoRetryAttemptsLeft = config.autoRetryCount
        _playbackState.value = PlaybackState.Buffering

        // Reiniciar límites de bitrate para permitir la máxima calidad al iniciar cada nuevo canal
        try {
            trackSelector.setParameters(
                trackSelector.parameters.buildUpon()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            )
        } catch (_: Exception) {}

        val mediaSource = buildMediaSource(url)
        exoPlayer.setMediaSource(mediaSource)
        playbackTracker.onMediaItemSet(currentAttemptId)
        exoPlayer.prepare()
        playbackTracker.onPrepareCalled(currentAttemptId)
        exoPlayer.playWhenReady = true
    }

    private fun buildMediaSource(url: String): MediaSource {
        val uri = Uri.parse(url)
        val cleanUrl = url.lowercase()
        val isHls = cleanUrl.contains(".m3u8") || cleanUrl.contains("/hls/") || cleanUrl.contains("m3u8")
        // Política de reintentos: hasta autoRetryCount intentos automáticos ante fragmentos perdidos
        // o caídas momentáneas de red (inspirado en PlaybackService.kt del repositorio Killua)
        val retryPolicy = DefaultLoadErrorHandlingPolicy(config.autoRetryCount)

        return if (isHls) {
            HlsMediaSource.Factory(httpDataSourceFactory)
                .setAllowChunklessPreparation(config.allowChunklessPreparation)
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        } else {
            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
                setConstantBitrateSeekingEnabled(true)
            }
            ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(MediaItem.fromUri(uri))
        }
    }

    private fun updateState(isPlaying: Boolean) {
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.coerceAtLeast(0L)
        _playbackState.value = if (isPlaying) {
            PlaybackState.Playing(positionMs = pos, durationMs = dur, isLive = isCurrentStreamLive)
        } else {
            PlaybackState.Paused(positionMs = pos, durationMs = dur, isLive = isCurrentStreamLive)
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressTrackingJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    updateState(true)
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    /**
     * FASE 33 — Reintenta la última URL tras un error, sin cambiar de canal.
     * Usado por el botón «Reintentar» del overlay de error de [LelouchVideoPlayer].
     */
    fun retry() {
        val url = _currentUrl.value
        if (url.isNullOrBlank()) {
            _playbackState.value = PlaybackState.Error(
                errorMessage = "No hay ninguna reproducción anterior que reintentar.",
                errorCode = PlaybackException.ERROR_CODE_BAD_VALUE,
                canRetry = false
            )
            return
        }
        val isLive = isCurrentStreamLive
        autoRetryAttemptsLeft = config.autoRetryCount
        _playbackState.value = PlaybackState.Buffering
        val mediaSource = buildMediaSource(url)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        isCurrentStreamLive = isLive
    }

    fun pause() {
        exoPlayer.playWhenReady = false
        updateState(false)
        stopProgressTracking()
    }

    fun resume() {
        if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playerError != null) {
            _currentUrl.value?.let { playStream(it, isCurrentStreamLive) }
        } else {
            exoPlayer.playWhenReady = true
            updateState(true)
            startProgressTracking()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun seekBy(offsetMs: Long) {
        val current = exoPlayer.currentPosition
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        val target = (current + offsetMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        seekTo(target)
    }

    fun togglePlayPause() {
        if (exoPlayer.playWhenReady) {
            pause()
        } else {
            resume()
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }


    fun stop() {
        playbackTracker.onUserCancel(currentAttemptId)
        stopBufferingWatchdog()
        exoPlayer.stop()
        stopProgressTracking()
        _playbackState.value = PlaybackState.Idle
    }

    fun release() {
        playbackTracker.onUserCancel(currentAttemptId)
        stopBufferingWatchdog()
        stopProgressTracking()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        _playbackState.value = PlaybackState.Idle
    }
}
