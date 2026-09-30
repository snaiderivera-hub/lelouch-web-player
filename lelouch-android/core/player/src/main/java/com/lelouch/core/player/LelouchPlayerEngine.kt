package com.lelouch.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
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
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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
    private val config: PlayerConfig = PlayerConfig()
) {
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
        // ABR adaptativo: ExoPlayer bajará automáticamente la calidad (ej. 1080p -> 720p -> 480p)
        // cuando el ancho de banda decaiga, en lugar de congelarse esperando el siguiente fragmento.
        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory()
        DefaultTrackSelector(context, adaptiveTrackSelectionFactory).also { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
            )
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
            }
    }

    private var isCurrentStreamLive: Boolean = false
    // Contador de reintentos automáticos ante fallos de red (se reinicia al cambiar de canal)
    private var autoRetryAttemptsLeft: Int = config.autoRetryCount

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> {
                    _playbackState.value = PlaybackState.Idle
                    stopProgressTracking()
                }
                Player.STATE_BUFFERING -> {
                    _playbackState.value = PlaybackState.Buffering
                }
                Player.STATE_READY -> {
                    val isPlaying = exoPlayer.playWhenReady
                    updateState(isPlaying)
                    if (isPlaying) {
                        startProgressTracking()
                    } else {
                        stopProgressTracking()
                    }
                }
                Player.STATE_ENDED -> {
                    _playbackState.value = PlaybackState.Ended
                    stopProgressTracking()
                }
            }
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
                    "Sin conexión al servidor IPTV. Verifica tu internet."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "Error del servidor (403 / URL expirada). Intenta de nuevo."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                    "Formato de stream no compatible. Cambia de canal."
                else -> error.localizedMessage ?: "Error desconocido durante la reproducción."
            }
            _playbackState.value = PlaybackState.Error(
                errorMessage = errorDescription,
                errorCode = error.errorCode,
                canRetry = true
            )
        }
    }

    /**
     * Sintoniza y reproduce una URL de transmisión IPTV en vivo o VOD bajo demanda.
     */
    fun playStream(url: String, isLive: Boolean = false) {
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

        val mediaSource = buildMediaSource(url)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
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

    fun stop() {
        exoPlayer.stop()
        stopProgressTracking()
        _playbackState.value = PlaybackState.Idle
    }

    fun release() {
        stopProgressTracking()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        _playbackState.value = PlaybackState.Idle
    }
}
