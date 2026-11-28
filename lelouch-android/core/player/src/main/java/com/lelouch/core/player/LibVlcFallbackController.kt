package com.lelouch.core.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Isolated LibVLC fallback player used exclusively when ExoPlayer/Media3 fails to render
 * hardware frames on live streams (e.g. 1080i PAFF streams affected by androidx/media#3515).
 */
@OptIn(UnstableApi::class)
internal class LibVlcFallbackController(
    private val context: Context,
    private val onStateChanged: (PlaybackState) -> Unit,
    private val onVideoInfoChanged: (VideoTrackInfo) -> Unit,
    private val onProgressChanged: (positionMs: Long, durationMs: Long, isPlaying: Boolean) -> Unit,
    private val onFatalError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var attachedLayout: VLCVideoLayout? = null
    private var currentScaleType: MediaPlayer.ScaleType = MediaPlayer.ScaleType.SURFACE_FIT_SCREEN

    private data class PendingStart(val url: String, val userAgent: String, val muted: Boolean, val isLive: Boolean)
    private var pendingStart: PendingStart? = null
    private var isActiveSession: Boolean = false
    private var hasReportedPlaying: Boolean = false
    private var currentIsLive: Boolean = true

    private val eventListener = MediaPlayer.EventListener { event ->
        if (!isActiveSession) return@EventListener
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                mainHandler.post {
                    if (isActiveSession) onStateChanged(PlaybackState.Buffering)
                }
            }
            MediaPlayer.Event.Buffering -> {
                if (!hasReportedPlaying && event.buffering < 100f) {
                    mainHandler.post {
                        if (isActiveSession && !hasReportedPlaying) {
                            onStateChanged(PlaybackState.Buffering)
                        }
                    }
                } else if (event.buffering >= 100f) {
                    mainHandler.post {
                        if (isActiveSession && mediaPlayer?.isPlaying == true) {
                            hasReportedPlaying = true
                            onStateChanged(PlaybackState.Playing(currentPositionMs, durationMs, currentIsLive))
                        }
                    }
                }
            }
            MediaPlayer.Event.Playing -> {
                hasReportedPlaying = true
                mainHandler.post {
                    if (isActiveSession) {
                        onStateChanged(PlaybackState.Playing(currentPositionMs, durationMs, currentIsLive))
                    }
                }
            }
            MediaPlayer.Event.Paused -> {
                mainHandler.post {
                    if (isActiveSession) {
                        onStateChanged(PlaybackState.Paused(currentPositionMs, durationMs, currentIsLive))
                    }
                }
            }
            MediaPlayer.Event.Vout -> {
                if (event.voutCount > 0) {
                    hasReportedPlaying = true
                    mainHandler.post {
                        if (!isActiveSession) return@post
                        onStateChanged(PlaybackState.Playing(currentPositionMs, durationMs, currentIsLive))
                        updateVideoTrackMetadata()
                    }
                }
            }
            MediaPlayer.Event.TimeChanged -> {
                val mp = mediaPlayer ?: return@EventListener
                val pos = event.timeChanged.coerceAtLeast(0L)
                val len = runCatching { mp.length.coerceAtLeast(0L) }.getOrDefault(0L)
                val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                mainHandler.post {
                    if (isActiveSession) onProgressChanged(pos, len, playing)
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                mainHandler.post {
                    if (isActiveSession) {
                        isActiveSession = false
                        onFatalError("No se pudo reproducir la señal con LibVLC.")
                    }
                }
            }
        }
    }

    private fun ensurePlayer(): MediaPlayer {
        mediaPlayer?.let { return it }
        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--network-caching=1500",
            "--http-reconnect"
        )
        val vlc = LibVLC(context.applicationContext, options)
        val mp = MediaPlayer(vlc).apply {
            setEventListener(eventListener)
        }
        libVlc = vlc
        mediaPlayer = mp
        return mp
    }

    private fun updateVideoTrackMetadata() {
        val track = runCatching { mediaPlayer?.currentVideoTrack }.getOrNull() ?: return
        if (track is IMedia.VideoTrack && track.width > 0 && track.height > 0) {
            val fps = if (track.frameRateDen > 0) {
                track.frameRateNum.toFloat() / track.frameRateDen.toFloat()
            } else 0f
            onVideoInfoChanged(
                VideoTrackInfo(
                    width = track.width,
                    height = track.height,
                    frameRate = fps,
                    bitrate = track.bitrate,
                    mimeType = track.codec ?: "video/avc"
                )
            )
        }
    }

    fun attachView(layout: VLCVideoLayout) {
        val mp = ensurePlayer()
        if (attachedLayout !== layout) {
            if (attachedLayout != null) {
                runCatching { mp.detachViews() }
            }
            attachedLayout = layout
            runCatching {
                mp.attachViews(layout, null, false, false)
                mp.videoScale = currentScaleType
            }
        }
        val queued = pendingStart
        if (queued != null && isActiveSession) {
            pendingStart = null
            startMediaInternal(queued.url, queued.userAgent, queued.muted)
        }
    }

    fun detachView() {
        attachedLayout = null
        mediaPlayer?.let { mp ->
            runCatching { mp.detachViews() }
        }
    }

    fun setResizeMode(resizeMode: Int) {
        currentScaleType = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            else -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
        }
        mediaPlayer?.let { mp ->
            if (attachedLayout != null) {
                runCatching { mp.videoScale = currentScaleType }
            }
        }
    }

    fun play(url: String, userAgent: String, muted: Boolean, isLive: Boolean = true) {
        isActiveSession = true
        hasReportedPlaying = false
        currentIsLive = isLive
        ensurePlayer()
        if (attachedLayout == null) {
            // Wait for Compose AndroidView to mount VLCVideoLayout before starting media
            // so LibVLC binds MediaCodec directly to the active Surface.
            pendingStart = PendingStart(url, userAgent, muted, isLive)
            return
        }
        pendingStart = null
        startMediaInternal(url, userAgent, muted)
    }

    private fun startMediaInternal(url: String, userAgent: String, muted: Boolean) {
        val vlc = libVlc ?: return
        val mp = mediaPlayer ?: return
        runCatching {
            mp.stop()
            val media = Media(vlc, Uri.parse(url)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=1500")
                addOption(":http-user-agent=$userAgent")
                addOption(":http-reconnect")
            }
            mp.media = media
            media.release()
            mp.volume = if (muted) 0 else 100
            mp.videoScale = currentScaleType
            mp.play()
        }.onFailure { err ->
            isActiveSession = false
            onFatalError("Error inicializando LibVLC: ${err.message ?: "desconocido"}")
        }
    }

    fun pause() {
        runCatching { mediaPlayer?.pause() }
    }

    fun resume() {
        runCatching { mediaPlayer?.play() }
    }

    fun setMuted(muted: Boolean) {
        runCatching { mediaPlayer?.volume = if (muted) 0 else 100 }
    }

    val isPlaying: Boolean
        get() = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    val currentPositionMs: Long
        get() = runCatching { mediaPlayer?.time?.coerceAtLeast(0L) ?: 0L }.getOrDefault(0L)

    val durationMs: Long
        get() = runCatching { mediaPlayer?.length?.coerceAtLeast(0L) ?: 0L }.getOrDefault(0L)

    fun stop() {
        isActiveSession = false
        hasReportedPlaying = false
        pendingStart = null
        runCatching { mediaPlayer?.stop() }
    }

    fun release() {
        stop()
        detachView()
        mediaPlayer?.let { mp ->
            runCatching {
                mp.setEventListener(null)
                mp.release()
            }
        }
        mediaPlayer = null
        libVlc?.let { runCatching { it.release() } }
        libVlc = null
    }
}
