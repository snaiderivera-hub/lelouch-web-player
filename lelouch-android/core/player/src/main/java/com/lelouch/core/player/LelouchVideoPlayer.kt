package com.lelouch.core.player

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Creates and remembers a [LelouchPlayerEngine] instance tied to the composable lifecycle.
 */
@Composable
fun rememberLelouchPlayer(
    config: PlayerConfig = PlayerConfig()
): LelouchPlayerEngine {
    val context = LocalContext.current
    val engine = remember { LelouchPlayerEngine(context.applicationContext, config) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    engine.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    engine.release()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.release()
        }
    }

    return engine
}

/**
 * Native Video Player surface view composable powered by AndroidX Media3 PlayerView.
 * Prevents screen timeout via window FLAG_KEEP_SCREEN_ON and PlayerView.keepScreenOn.
 */
@OptIn(UnstableApi::class)
@Composable
fun LelouchVideoPlayer(
    playerEngine: LelouchPlayerEngine,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    overlayContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    // FASE 33: estado observable del motor. Antes [PlaybackState.Error] se emitía y nadie lo
    // renderizaba: el usuario veía pantalla negra sin mensaje alguno ("no reproduce nada").
    val playbackState by playerEngine.playbackState.collectAsState()

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    isFocusable = true
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode
                    player = playerEngine.exoPlayer
                }
            },
            update = { playerView ->
                playerView.keepScreenOn = true
                playerView.player = playerEngine.exoPlayer
                playerView.resizeMode = resizeMode
            }
        )

        // Custom overlay slot (HUD, gradients, watermark, indicators)
        overlayContent()

        // ── FASE 33: Overlay de estado — error visible + botón reintentar ──
        when (val state = playbackState) {
            is PlaybackState.Error -> ErrorOverlay(
                message = state.errorMessage,
                errorCode = state.errorCode,
                canRetry = state.canRetry,
                onRetry = { playerEngine.retry() },
                modifier = Modifier.fillMaxSize()
            )
            PlaybackState.Buffering -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(46.dp)
                )
            }
            else -> Unit
        }
    }
}

/**
 * Panel de error legible con el mensaje del motor y el código de ExoPlayer (FASE 33).
 * Sin esto, un fallo de reproducción se traducía en pantalla negra silenciosa.
 */
@Composable
private fun ErrorOverlay(
    message: String,
    errorCode: Int,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚠️ NO SE PUDO REPRODUCIR",
                color = Color(0xFFFF5252),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            if (errorCode != -1) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Código ExoPlayer: $errorCode",
                    color = Color(0xFF9AA4B2),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (canRetry) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Text(text = "🔄 REINTENTAR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
