package com.lelouch.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
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
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
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

@OptIn(UnstableApi::class)
private class IptvLoadErrorHandlingPolicy(private val maxRetries: Int = 5) : DefaultLoadErrorHandlingPolicy(maxRetries) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val ex = loadErrorInfo.exception
        if (ex is HttpDataSource.InvalidResponseCodeException) {
            // Error 429 (Too Many Requests), 503 (Service Unavailable) o 500: reintentar con backoff suave
            if (ex.responseCode == 429 || ex.responseCode == 503 || ex.responseCode == 500) {
                if (loadErrorInfo.errorCount <= maxRetries) {
                    return (1_500L * loadErrorInfo.errorCount).coerceAtMost(5_000L)
                }
            }
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        return maxRetries
    }
}

/**
 * Native IPTV video player engine powered by AndroidX Media3 ExoPlayer.
 * Tailored for low-latency live IPTV streams, hardware accelerated decoding, and automatic HLS/MPEG-TS negotiation.
 */
@OptIn(UnstableApi::class)
data class PlayerDiagnosticInfo(
    val isLive: Boolean = false,
    val stateName: String = "IDLE",
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val currentPositionMs: Long = 0L,
    val renderedFrames: Int = 0,
    val droppedFrames: Int = 0,
    val resolution: String = "Detectando...",
    val frameRate: Float = 0f,
    val bitrate: Int = 0,
    val mimeType: String = "",
    val stallCount: Int = 0,
    val lastRecoveryReason: String = "Ninguna",
    val lastLogTimeMs: Long = System.currentTimeMillis()
)

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

    private val _diagnosticInfo = MutableStateFlow(PlayerDiagnosticInfo())
    val diagnosticInfo: StateFlow<PlayerDiagnosticInfo> = _diagnosticInfo.asStateFlow()
    private var lastRecoveryReasonString: String = "Ninguna"

    // FASE 36: selección de pistas (calidad / audio / subtítulos)
    private val _trackSelection = MutableStateFlow(TrackSelectionState())
    val trackSelection: StateFlow<TrackSelectionState> = _trackSelection.asStateFlow()

    private data class TrackBinding(val group: TrackGroup, val index: Int)
    private val trackBindings = mutableMapOf<String, TrackBinding>()
    private val externalSubtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
    private var manualVideoOverrideId: String? = null
    private var lastDiagnosticLogTimestampMs: Long = 0L

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // FASE 37: avance observable para «continuar viendo» (historial) y gestos de búsqueda.
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    /** Posición actual en ms (0 si el reproductor aún no está listo). */
    val currentPositionMs: Long
        get() = runCatching { exoPlayer.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)

    /** Duración conocida en ms; 0 en vivo o mientras el contenedor no la declare. */
    val durationMs: Long
        get() = runCatching { exoPlayer.duration.coerceAtLeast(0L) }.getOrDefault(0L)

    private val streamingOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(10, TimeUnit.SECONDS)
            .connectTimeout(config.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val httpDataSourceFactory: androidx.media3.datasource.DataSource.Factory by lazy {
        OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent(config.userAgent)
    }

    private val loadControl by lazy {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                config.minBufferMs,
                config.maxBufferMs,
                config.bufferForPlaybackMs,
                config.bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(0, false)
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
        // ABR adaptativo optimizado para IPTV y redes domésticas:
        // - minDurationForQualityIncreaseMs = 15s para subir de resolución con estabilidad
        // - maxDurationForQualityDecreaseMs = 2s (si el buffer decae a menos de 2s, baja de calidad inmediatamente para evitar cortes)
        // - minDurationToRetainAfterDiscardMs = 2s
        // - bandwidthFraction = 0.85f (utiliza hasta el 85% del ancho de banda disponible)
        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            15_000,
            2_000,
            2_000,
            0.85f
        )
        DefaultTrackSelector(context, adaptiveTrackSelectionFactory).also { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
            )
        }
    }

    private fun extractAttemptId(eventTime: AnalyticsListener.EventTime): Long? {
        val mediaId = if (!eventTime.timeline.isEmpty && eventTime.windowIndex in 0 until eventTime.timeline.windowCount) {
            val window = androidx.media3.common.Timeline.Window()
            eventTime.timeline.getWindow(eventTime.windowIndex, window).mediaItem.mediaId
        } else if (!eventTime.currentTimeline.isEmpty && eventTime.currentWindowIndex in 0 until eventTime.currentTimeline.windowCount) {
            val window = androidx.media3.common.Timeline.Window()
            eventTime.currentTimeline.getWindow(eventTime.currentWindowIndex, window).mediaItem.mediaId
        } else null

        return if (mediaId != null && mediaId.startsWith("attempt_")) {
            mediaId.removePrefix("attempt_").toLongOrNull()
        } else null
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long
        ) {
            val eventAttemptId = extractAttemptId(eventTime)
            if (eventAttemptId != null && eventAttemptId != currentAttemptId) {
                android.util.Log.d(
                    "LelouchTTFF",
                    "[STALE_CALLBACK_DROPPED] onRenderedFirstFrame dropped for stale attempt=$eventAttemptId (active=$currentAttemptId)"
                )
                return
            }
            playbackTracker.onRenderedFirstFrame(eventAttemptId ?: currentAttemptId)
        }

        override fun onPlaybackStateChanged(
            eventTime: AnalyticsListener.EventTime,
            state: Int
        ) {
            val eventAttemptId = extractAttemptId(eventTime)
            if (eventAttemptId != null && eventAttemptId != currentAttemptId) {
                android.util.Log.d(
                    "LelouchTTFF",
                    "[STALE_CALLBACK_DROPPED] onPlaybackStateChanged($state) dropped for stale attempt=$eventAttemptId (active=$currentAttemptId)"
                )
                return
            }
            val targetId = eventAttemptId ?: currentAttemptId
            when (state) {
                Player.STATE_BUFFERING -> playbackTracker.onBuffering(targetId)
                Player.STATE_READY -> {
                    playbackTracker.onReady(targetId)
                    if (exoPlayer.videoFormat == null && exoPlayer.currentTracks.isTypeSupported(C.TRACK_TYPE_AUDIO)) {
                        playbackTracker.onAudioOnlySuccess(targetId)
                    }
                }
            }
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: PlaybackException
        ) {
            val eventAttemptId = extractAttemptId(eventTime)
            if (eventAttemptId != null && eventAttemptId != currentAttemptId) {
                android.util.Log.d(
                    "LelouchTTFF",
                    "[STALE_CALLBACK_DROPPED] onPlayerError dropped for stale attempt=$eventAttemptId (active=$currentAttemptId)"
                )
                return
            }
            playbackTracker.onPlayerError(eventAttemptId ?: currentAttemptId, error.errorCode, error.message)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val eventAttemptId = extractAttemptId(eventTime)
            if (eventAttemptId != null && eventAttemptId != currentAttemptId) {
                android.util.Log.d(
                    "LelouchTTFF",
                    "[STALE_CALLBACK_DROPPED] onVideoDecoderInitialized dropped for stale attempt=$eventAttemptId (active=$currentAttemptId)"
                )
                return
            }
            playbackTracker.onVideoDecoderInitialized(eventAttemptId ?: currentAttemptId, initializationDurationMs)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val eventAttemptId = extractAttemptId(eventTime)
            if (eventAttemptId != null && eventAttemptId != currentAttemptId) {
                android.util.Log.d(
                    "LelouchTTFF",
                    "[STALE_CALLBACK_DROPPED] onAudioDecoderInitialized dropped for stale attempt=$eventAttemptId (active=$currentAttemptId)"
                )
                return
            }
            playbackTracker.onAudioDecoderInitialized(eventAttemptId ?: currentAttemptId, initializationDurationMs)
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            val eventAttemptId = extractAttemptId(eventTime)
            if (eventAttemptId != null && eventAttemptId != currentAttemptId) {
                android.util.Log.d(
                    "LelouchTTFF",
                    "[STALE_CALLBACK_DROPPED] onLoadCompleted dropped for stale attempt=$eventAttemptId (active=$currentAttemptId)"
                )
                return
            }
            val targetId = eventAttemptId ?: currentAttemptId
            if (mediaLoadData.dataType == C.DATA_TYPE_MANIFEST) {
                playbackTracker.onManifestLoaded(targetId, loadEventInfo.loadDurationMs)
            } else if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA) {
                playbackTracker.onFirstSegmentLoaded(targetId, loadEventInfo.loadDurationMs)
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

    @Volatile
    private var isRecoveringLive: Boolean = false
    private var lastFreezeRecoveryTimestampMs: Long = 0L
    private var lastTrackedPositionMs: Long = -1L
    private var lastRenderedFrames: Int = -1
    private var silentStallCounter: Int = 0

    /**
     * Auto-recuperación transparente para transmisiones en vivo congeladas o atascadas.
     * Simula internamente un reinicio limpio de hardware (MediaCodec flush) y socket HTTP
     * (exactamente lo mismo que ocurre al cambiar de canal y retornar),
     * sin alterar la interfaz ni deseleccionar el canal actual.
     */
    fun recoverLiveStream(reason: String) {
        if (!isCurrentStreamLive) return
        val url = _currentUrl.value ?: return
        val now = System.currentTimeMillis()
        if (isRecoveringLive || (now - lastFreezeRecoveryTimestampMs < 3_500L)) {
            return
        }
        isRecoveringLive = true
        lastFreezeRecoveryTimestampMs = now
        lastRecoveryReasonString = reason

        android.util.Log.e("LelouchDiag", "[LIVE_HARD_RECOVER] Causa: $reason. Ejecutando reinicio completo de hardware y socket...")
        _playbackState.value = PlaybackState.Buffering

        scope.launch {
            try {
                // HARD FLUSH: Detener y limpiar ítems para forzar a Android y al chip Amlogic
                // a liberar los buffers saturados en MediaCodec (BufferPoolAccessor2.0)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()

                val attempt = playbackTracker.startAttempt(url, PlaybackMediaType.LIVE_CHANNEL, PlayerStartType.WARM_SWITCH)
                currentAttemptId = attempt.attemptId

                val newMediaSource = buildMediaSource(url, currentAttemptId)
                exoPlayer.setMediaSource(newMediaSource)
                playbackTracker.onMediaItemSet(currentAttemptId)
                exoPlayer.prepare()
                playbackTracker.onPrepareCalled(currentAttemptId)
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                android.util.Log.e("LelouchDiag", "[LIVE_HARD_RECOVER] Error al reconectar: ${e.message}")
            } finally {
                delay(2_000L)
                isRecoveringLive = false
            }
        }
    }

    private fun startBufferingWatchdog() {
        stopBufferingWatchdog()
        if (!isCurrentStreamLive) return
        val watchdogAttemptId = currentAttemptId
        playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_STARTED)
        bufferingWatchdogJob = scope.launch {
            val isMultiBitrateHls = _currentUrl.value?.contains(".m3u8", ignoreCase = true) == true ||
                                    _currentUrl.value?.contains("output=m3u8", ignoreCase = true) == true

            // FASE 1 (2.5 segundos de buffering):
            // Reducir la calidad de video al bitrate intermedio (720p/480p) para salvar la transmisión sin cortar.
            // NOTA: Solo aplicar reducción de bitrate (ABR) en streams HLS multi-bitrate (.m3u8).
            delay(2_500L)
            if (!isActive || !isCurrentStreamLive) return@launch

            if (isMultiBitrateHls) {
                playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_BITRATE_REDUCTION, "Bitrate -> 1.5Mbps")
                android.util.Log.w("LelouchPlayer", "Buffer en riesgo (>2.5s). Reduciendo calidad de video (ABR) para evitar corte...")
                try {
                    trackSelector.setParameters(
                        trackSelector.parameters.buildUpon()
                            .setMaxVideoBitrate(1_500_000)
                            .setExceedVideoConstraintsIfNecessary(true)
                    )
                } catch (_: Exception) {}
            }

            // FASE 2 (4.5 segundos continuos en buffering en vivo):
            // Si el buffer no se recuperó en 4.5s (en vivo), no dejar al usuario esperando 12 segundos;
            // auto-reconectar de inmediato el socket hacia el servidor IPTV.
            delay(2_000L)
            if (!isActive || !isCurrentStreamLive) return@launch

            if (autoRetryAttemptsLeft > 0) {
                autoRetryAttemptsLeft--
                playbackTracker.onWatchdogEvent(watchdogAttemptId, WatchdogEventType.WATCHDOG_RETRY, "Buffering timeout auto-recover")
                recoverLiveStream("Buffer agotado durante >4.5s")
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
        scope.launch {
            delay(1_000L)
            onChannelUnavailable?.invoke()
        }
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
                    _playbackState.value = PlaybackState.Buffering
                    startBufferingWatchdog()
                    if (exoPlayer.playWhenReady) startProgressTracking()
                }
                Player.STATE_READY -> {
                    stopBufferingWatchdog()
                    autoRetryAttemptsLeft = config.autoRetryCount
                    updateState(exoPlayer.isPlaying)
                    if (exoPlayer.playWhenReady) {
                        startProgressTracking()
                    } else {
                        stopProgressTracking()
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
            // Handled exclusively by analyticsListener with verified attemptId
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState(isPlaying)
            // BLINDAJE CRÍTICO: Si el usuario desea reproducir (playWhenReady == true),
            // NUNCA detener el tracking cuando isPlaying pasa a false por un congelamiento o corte.
            if (exoPlayer.playWhenReady) {
                startProgressTracking()
            } else {
                stopProgressTracking()
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val format = exoPlayer.videoFormat
            if (format != null) {
                _videoTrackInfo.value = VideoTrackInfo(
                    width = format.width,
                    height = format.height,
                    frameRate = if (format.frameRate > 0) format.frameRate else 0f,
                    bitrate = if (format.bitrate > 0) format.bitrate else 0,
                    mimeType = format.sampleMimeType
                )
            }
            rebuildTrackSelection(tracks)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val format = exoPlayer.videoFormat
            _videoTrackInfo.value = _videoTrackInfo.value.copy(
                width = videoSize.width,
                height = videoSize.height,
                frameRate = if (format != null && format.frameRate > 0) format.frameRate else _videoTrackInfo.value.frameRate,
                bitrate = if (format != null && format.bitrate > 0) format.bitrate else _videoTrackInfo.value.bitrate,
                mimeType = format?.sampleMimeType ?: _videoTrackInfo.value.mimeType
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            // Error telemetry is handled exclusively by analyticsListener with verified attemptId
            stopBufferingWatchdog()
            stopProgressTracking()
            // Auto-recuperación silenciosa para errores de red y timeout:
            // Si hay una URL activa y es un stream en vivo, reintentamos automáticamente
            // después de 2 segundos sin molestar al usuario, igual al modelo Killua.
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && autoRetryAttemptsLeft > 0) {
                // FASE 33: El stream en vivo se desfasó (error común en IPTV al cambiar canales rápido). 
                // La recomendación oficial de ExoPlayer es reiniciar a la posición por defecto y preparar de nuevo.
                autoRetryAttemptsLeft--
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                return
            }

            val isNetworkError = error.errorCode in setOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                -3 // Handling undocumented legacy error -3
            )
            val currentStreamUrl = _currentUrl.value
            if (isNetworkError && currentStreamUrl != null && autoRetryAttemptsLeft > 0) {
                autoRetryAttemptsLeft--
                _playbackState.value = PlaybackState.Buffering
                android.util.Log.w(
                    "LelouchPlayer",
                    "Error de red o HTTP (${error.errorCode}). Reintentando conexión silenciosa (isLive=$isCurrentStreamLive, intentosRestantes=$autoRetryAttemptsLeft)..."
                )
                if (isCurrentStreamLive) {
                    recoverLiveStream("Error de red/timeout (${error.errorCode})")
                } else {
                    val resumePosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                    scope.launch {
                        delay(1_500L)
                        if (_currentUrl.value == currentStreamUrl) {
                            if (resumePosition > 0L) {
                                exoPlayer.seekTo(resumePosition)
                            }
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        }
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
                    "Canal no disponible (Error 403/404/429). Saltando al siguiente..."
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
            // FASE 33: Intentar salto automático al siguiente canal (limitado en UI)
            if (isCurrentStreamLive) {
                scope.launch {
                    delay(1_000L)
                    if (currentStreamUrl == _currentUrl.value) {
                        onChannelUnavailable?.invoke()
                    }
                }
            }
        }
    }

    /**
     * Sintoniza y reproduce una URL de transmisión IPTV en vivo o VOD bajo demanda.
     *
     * @param startPositionMs posición inicial para reanudar (solo VOD). Se ignora en vivo.
     */
    fun playStream(
        url: String,
        isLive: Boolean = false,
        userT0: Long? = null,
        startPositionMs: Long = 0L
    ) {
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
            if (startPositionMs > 0L) exoPlayer.seekTo(startPositionMs)
            return
        }

        // Cancelar buffer previo, liberar conexión HTTP previa para evitar 429/503 y reiniciar contador de reintentos
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _currentUrl.value = url
        isCurrentStreamLive = isLive
        autoRetryAttemptsLeft = config.autoRetryCount
        _playbackState.value = PlaybackState.Buffering
        _progress.value = PlaybackProgress(isLive = isLive)

        // FASE 36: un contenido nuevo no hereda subtítulos externos ni el override manual de calidad.
        externalSubtitleConfigs.clear()
        manualVideoOverrideId = null

        // Reiniciar límites de bitrate para permitir la máxima calidad al iniciar cada nuevo canal
        try {
            trackSelector.setParameters(
                trackSelector.parameters.buildUpon()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            )
        } catch (_: Exception) {}

        val mediaSource = buildMediaSource(url, currentAttemptId)
        exoPlayer.setMediaSource(mediaSource)
        playbackTracker.onMediaItemSet(currentAttemptId)
        exoPlayer.prepare()
        playbackTracker.onPrepareCalled(currentAttemptId)
        // FASE 37: reanudar en la posición guardada. ExoPlayer acepta seek antes de STATE_READY
        // y lo aplica en cuanto el contenedor conoce el índice.
        if (!isLive && startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    private val extractorsFactory by lazy {
        DefaultExtractorsFactory().apply {
            setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )
        }
    }

    private val defaultMediaSourceFactory by lazy {
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory)
    }

    private fun buildMediaSource(url: String, attemptId: Long): MediaSource {
        val uri = Uri.parse(url)
        val retryPolicy = IptvLoadErrorHandlingPolicy(config.autoRetryCount)

        val isHls = url.contains(".m3u8", ignoreCase = true) || url.contains("output=m3u8", ignoreCase = true)
        val isMp4 = url.contains(".mp4", ignoreCase = true)

        val liveConfig = if (isCurrentStreamLive) {
            MediaItem.LiveConfiguration.Builder()
                .setMaxPlaybackSpeed(1.02f)
                .setMinPlaybackSpeed(0.98f)
                .build()
        } else null

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId("attempt_$attemptId")
            .setSubtitleConfigurations(externalSubtitleConfigs)
            .apply {
                if (liveConfig != null) {
                    setLiveConfiguration(liveConfig)
                }
                when {
                    isHls -> setMimeType(MimeTypes.APPLICATION_M3U8)
                    isMp4 -> setMimeType(MimeTypes.APPLICATION_MP4)
                }
            }
            .build()

        return if (isHls) {
            HlsMediaSource.Factory(httpDataSourceFactory)
                .setAllowChunklessPreparation(config.allowChunklessPreparation)
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)
        } else {
            defaultMediaSourceFactory
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)
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
        if (progressTrackingJob?.isActive == true) return
        lastTrackedPositionMs = -1L
        lastRenderedFrames = -1
        silentStallCounter = 0
        progressTrackingJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    updateState(true)
                }

                val currentPos = exoPlayer.currentPosition
                // FASE 37: publicar avance para el historial/«continuar viendo».
                if (currentPos >= 0L) {
                    _progress.value = PlaybackProgress(
                        positionMs = currentPos,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        isLive = isCurrentStreamLive,
                        isPlaying = exoPlayer.isPlaying
                    )
                }
                val renderedFrames = exoPlayer.videoDecoderCounters?.renderedOutputBufferCount ?: -1
                val droppedFrames = exoPlayer.videoDecoderCounters?.droppedBufferCount ?: 0

                // DETECCIÓN DE CONGELAMIENTO EN VIVO (STALL WATCHDOG):
                // En canales en vivo, mientras el usuario tenga playWhenReady = true:
                // 1. Si los fotogramas de video no avanzan (decodificador de chip trabado en TV Box)
                // 2. Si la posición de tiempo/audio no avanza
                // 3. Si el reproductor está en STATE_READY pero isPlaying es false por más de 3.5s
                if (isCurrentStreamLive && 
                    exoPlayer.playWhenReady &&
                    !isRecoveringLive
                ) {
                    val isFrameStalled = if (renderedFrames >= 0 && lastRenderedFrames >= 0) {
                        (renderedFrames - lastRenderedFrames) == 0
                    } else false

                    val isPositionStalled = lastTrackedPositionMs >= 0L && kotlin.math.abs(currentPos - lastTrackedPositionMs) < 150L
                    val isNotPlayingStalled = !exoPlayer.isPlaying && exoPlayer.playbackState == Player.STATE_READY

                    if (isFrameStalled || isPositionStalled || isNotPlayingStalled) {
                        silentStallCounter++
                        if (silentStallCounter >= 4) { // 4 segundos consecutivos sin avance
                            silentStallCounter = 0
                            lastTrackedPositionMs = -1L
                            lastRenderedFrames = -1
                            recoverLiveStream(
                                when {
                                    isFrameStalled -> "Video congelado (0 frames renderizados en 4s, decodificador bloqueado)"
                                    isNotPlayingStalled -> "ExoPlayer detenido en STATE_READY (isPlaying=false por 4s)"
                                    else -> "Transmisión congelada (posición estancada por 4s)"
                                }
                            )
                        }
                    } else {
                        silentStallCounter = 0
                        lastTrackedPositionMs = currentPos
                        if (renderedFrames >= 0) {
                            lastRenderedFrames = renderedFrames
                        }
                    }
                } else {
                    silentStallCounter = 0
                    lastTrackedPositionMs = -1L
                    lastRenderedFrames = -1
                }

                // Emisión de Telemetría cada 2 segundos a Log.e("LelouchDiag")
                val now = System.currentTimeMillis()
                if (now - lastDiagnosticLogTimestampMs >= 2_000L) {
                    lastDiagnosticLogTimestampMs = now
                    val stateStr = when (exoPlayer.playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    val diagMsg = "[LelouchDiag] isLive=$isCurrentStreamLive | State=$stateStr | isPlaying=${exoPlayer.isPlaying} | playWhenReady=${exoPlayer.playWhenReady} | pos=${currentPos}ms | frames=$renderedFrames | dropped=$droppedFrames | stallCounter=$silentStallCounter | res=${_videoTrackInfo.value.resolutionLabel} (${_videoTrackInfo.value.width}x${_videoTrackInfo.value.height} @ ${_videoTrackInfo.value.frameRate}fps) | codec=${_videoTrackInfo.value.mimeType}"
                    android.util.Log.e("LelouchDiag", diagMsg)
                    _diagnosticInfo.value = PlayerDiagnosticInfo(
                        isLive = isCurrentStreamLive,
                        stateName = stateStr,
                        isPlaying = exoPlayer.isPlaying,
                        playWhenReady = exoPlayer.playWhenReady,
                        currentPositionMs = currentPos,
                        renderedFrames = renderedFrames.coerceAtLeast(0),
                        droppedFrames = droppedFrames,
                        resolution = "${_videoTrackInfo.value.resolutionLabel} (${_videoTrackInfo.value.width}x${_videoTrackInfo.value.height})",
                        frameRate = _videoTrackInfo.value.frameRate,
                        bitrate = _videoTrackInfo.value.bitrate,
                        mimeType = _videoTrackInfo.value.mimeType ?: "Desconocido",
                        stallCount = silentStallCounter,
                        lastRecoveryReason = lastRecoveryReasonString
                    )
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
        // FASE 37: conservar la posición al reintentar (antes volvía al inicio de la película).
        val resumePositionMs = if (isLive) 0L else exoPlayer.currentPosition.coerceAtLeast(0L)
        autoRetryAttemptsLeft = config.autoRetryCount
        _playbackState.value = PlaybackState.Buffering
        val mediaType = if (isLive) PlaybackMediaType.LIVE_CHANNEL else PlaybackMediaType.MOVIE
        val attempt = playbackTracker.startAttempt(url, mediaType, PlayerStartType.WARM_SWITCH)
        currentAttemptId = attempt.attemptId
        val mediaSource = buildMediaSource(url, currentAttemptId)
        exoPlayer.setMediaSource(mediaSource)
        playbackTracker.onMediaItemSet(currentAttemptId)
        exoPlayer.prepare()
        if (resumePositionMs > 0L) exoPlayer.seekTo(resumePositionMs)
        playbackTracker.onPrepareCalled(currentAttemptId)
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

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 36 · Selección de pistas de video, audio y subtítulos
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reconstruye las listas de pistas a partir del snapshot de [Tracks] de Media3.
     * Las claves `g{grupo}t{pista}` se reutilizan para aplicar el override correcto.
     */
    private fun rebuildTrackSelection(tracks: Tracks) {
        val video = mutableListOf<PlayerTrack>()
        val audio = mutableListOf<PlayerTrack>()
        val subtitles = mutableListOf<PlayerTrack>()
        trackBindings.clear()

        tracks.groups.forEachIndexed { groupIndex, group ->
            val kind = when (group.type) {
                C.TRACK_TYPE_VIDEO -> PlayerTrackKind.VIDEO
                C.TRACK_TYPE_AUDIO -> PlayerTrackKind.AUDIO
                C.TRACK_TYPE_TEXT -> PlayerTrackKind.SUBTITLE
                else -> return@forEachIndexed
            }
            if (!group.isSupported) return@forEachIndexed

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val id = "g${groupIndex}t${trackIndex}"
                trackBindings[id] = TrackBinding(group.mediaTrackGroup, trackIndex)

                val track = PlayerTrack(
                    id = id,
                    kind = kind,
                    label = when (kind) {
                        PlayerTrackKind.VIDEO -> videoTrackLabel(format)
                        PlayerTrackKind.AUDIO -> audioTrackLabel(format)
                        PlayerTrackKind.SUBTITLE -> subtitleTrackLabel(format)
                    },
                    secondaryLabel = when (kind) {
                        PlayerTrackKind.VIDEO -> videoTrackSecondaryLabel(format)
                        PlayerTrackKind.AUDIO -> audioTrackSecondaryLabel(format)
                        PlayerTrackKind.SUBTITLE -> subtitleTrackSecondaryLabel(format)
                    },
                    languageCode = format.language,
                    isSelected = group.isTrackSelected(trackIndex)
                )

                when (kind) {
                    PlayerTrackKind.VIDEO -> video += track
                    PlayerTrackKind.AUDIO -> audio += track
                    PlayerTrackKind.SUBTITLE -> subtitles += track
                }
            }
        }

        _trackSelection.value = TrackSelectionState(
            video = video,
            audio = audio,
            subtitles = subtitles,
            // "Auto" está activo mientras el usuario no fije una resolución manual.
            autoVideoSelected = manualVideoOverrideId == null,
            subtitlesEnabled = subtitles.any { it.isSelected }
        )
    }

    private inline fun applyTrackParameters(block: TrackSelectionParameters.Builder.() -> Unit) {
        try {
            val builder = trackSelector.parameters.buildUpon()
            builder.block()
            trackSelector.setParameters(builder)
        } catch (t: Throwable) {
            android.util.Log.w("LelouchPlayer", "No se pudo aplicar la selección de pista: ${t.message}")
        }
    }

    /** Selecciona una calidad de video fija o [AUTO_VIDEO_TRACK_ID] para ABR adaptativo. */
    fun selectVideoTrack(id: String) {
        if (id == AUTO_VIDEO_TRACK_ID) {
            manualVideoOverrideId = null
            applyTrackParameters {
                clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            }
            _trackSelection.value = _trackSelection.value.copy(
                autoVideoSelected = true,
                video = _trackSelection.value.video.map { it.copy(isSelected = false) }
            )
            return
        }

        val binding = trackBindings[id] ?: return
        manualVideoOverrideId = id
        applyTrackParameters {
            clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            setOverrideForType(TrackSelectionOverride(binding.group, binding.index))
            setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
        }
        _trackSelection.value = _trackSelection.value.copy(
            autoVideoSelected = false,
            video = _trackSelection.value.video.map { it.copy(isSelected = it.id == id) }
        )
    }

    /** Selecciona una pista de audio concreta (idioma/doblaje). */
    fun selectAudioTrack(id: String) {
        val binding = trackBindings[id] ?: return
        applyTrackParameters {
            setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            setOverrideForType(TrackSelectionOverride(binding.group, binding.index))
        }
        _trackSelection.value = _trackSelection.value.copy(
            audio = _trackSelection.value.audio.map { it.copy(isSelected = it.id == id) }
        )
    }

    /** Activa subtítulos concretos, o los desactiva con `null`. */
    fun selectSubtitleTrack(id: String?) {
        if (id == null) {
            applyTrackParameters {
                clearOverridesOfType(C.TRACK_TYPE_TEXT)
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            _trackSelection.value = _trackSelection.value.copy(
                subtitlesEnabled = false,
                subtitles = _trackSelection.value.subtitles.map { it.copy(isSelected = false) }
            )
            return
        }

        val binding = trackBindings[id] ?: return
        applyTrackParameters {
            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            setOverrideForType(TrackSelectionOverride(binding.group, binding.index))
        }
        _trackSelection.value = _trackSelection.value.copy(
            subtitlesEnabled = true,
            subtitles = _trackSelection.value.subtitles.map { it.copy(isSelected = it.id == id) }
        )
    }

    /**
     * Añade un subtítulo externo (`.srt`, `.vtt`, `.ass`, `.ttml`) mediante una URI `content://` o `file://`.
     * Media3 lo mezcla con el medio actual; se conserva la posición de reproducción.
     */
    fun addExternalSubtitle(
        uri: String,
        label: String? = null,
        language: String? = null,
        mimeType: String? = null
    ): Boolean {
        if (uri.isBlank()) return false
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mimeType ?: guessSubtitleMimeType(uri))
            .setLanguage(language)
            .setLabel(label ?: uri.substringAfterLast('/').substringBeforeLast('.'))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        externalSubtitleConfigs += config
        rebuildCurrentMediaSource()
        return true
    }

    /** Elimina los subtítulos externos añadidos en la sesión actual. */
    fun clearExternalSubtitles() {
        if (externalSubtitleConfigs.isEmpty()) return
        externalSubtitleConfigs.clear()
        rebuildCurrentMediaSource()
    }

    /**
     * Recrea el MediaSource actual para incorporar/retirar subtítulos side-loaded.
     * Mantiene posición y estado de reproducción; en vivo vuelve al borde del directo.
     */
    private fun rebuildCurrentMediaSource() {
        val url = _currentUrl.value ?: return
        val position = exoPlayer.currentPosition
        val playWhenReady = exoPlayer.playWhenReady
        val mediaSource = buildMediaSource(url, currentAttemptId)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        if (isCurrentStreamLive) {
            exoPlayer.seekToDefaultPosition()
        } else if (position > 0L) {
            exoPlayer.seekTo(position)
        }
        exoPlayer.playWhenReady = playWhenReady
    }


    fun stop() {
        // FASE 37: conservar la última posición en el flujo antes de detener; las pantallas
        // la leen al cerrar el reproductor para persistir el avance.
        _progress.value = _progress.value.copy(
            positionMs = runCatching { exoPlayer.currentPosition.coerceAtLeast(0L) }.getOrDefault(_progress.value.positionMs),
            isPlaying = false
        )
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
