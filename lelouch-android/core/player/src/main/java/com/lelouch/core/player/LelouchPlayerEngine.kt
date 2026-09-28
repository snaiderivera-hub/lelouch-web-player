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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
        DefaultTrackSelector(context)
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
            val errorDescription = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Fallo de conexión de red al stream remoto."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "Error de servidor HTTP (código inválido o 403 Forbidden)."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                    "Formato de transmisión no compatible o manifiesto corrupto."
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

        // Cancelar buffer previo de inmediato para cambio instantáneo de canal
        exoPlayer.stop()
        _currentUrl.value = url
        isCurrentStreamLive = isLive
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

        return if (isHls) {
            HlsMediaSource.Factory(httpDataSourceFactory)
                .setAllowChunklessPreparation(config.allowChunklessPreparation)
                .createMediaSource(MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        } else {
            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
                setConstantBitrateSeekingEnabled(true)
            }
            ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory)
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
