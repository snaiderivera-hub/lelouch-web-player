package com.lelouch.core.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 */
@OptIn(UnstableApi::class)
@Composable
fun LelouchVideoPlayer(
    playerEngine: LelouchPlayerEngine,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    overlayContent: @Composable () -> Unit = {}
) {
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
                    isFocusable = false
                    isFocusableInTouchMode = false
                    this.resizeMode = resizeMode
                    player = playerEngine.exoPlayer
                }
            },
            update = { playerView ->
                playerView.player = playerEngine.exoPlayer
                playerView.resizeMode = resizeMode
            }
        )

        // Custom overlay slot (HUD, gradients, watermark, indicators)
        overlayContent()
    }
}
