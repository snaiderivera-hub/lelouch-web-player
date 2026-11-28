package com.lelouch.core.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
    isLive: Boolean = true,
    useOrangeSpinner: Boolean = false,
    extraZoomScale: Float = 1f,
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

    var isBufferTimeout by remember { mutableStateOf(false) }

    // FASE 40: el aviso de "canal fuera de línea" pertenece al stream que lo provocó. Al cambiar
    // de canal (o al reintentar) hay que retirarlo y volver a contar el margen del nuevo enlace;
    // si no, seguiría tapando la pantalla aunque el canal nuevo ya estuviera cargando.
    val currentUrl by playerEngine.currentUrl.collectAsState()
    val playerInstanceVersion by playerEngine.playerInstanceVersion.collectAsState()
    val useLibVlcFallback by playerEngine.useLibVlcFallback.collectAsState()

    LaunchedEffect(currentUrl, playbackState, isLive) {
        if (playbackState is PlaybackState.Buffering) {
            isBufferTimeout = false
            // En vivo: 22s (margen para canales pesados o congestión temporal del proveedor sin cortar a los 10s).
            // VOD: 40s (los MKV/MP4 progresivos tardan más en negociar cabeceras remotas).
            kotlinx.coroutines.delay(if (isLive) 22000L else 40000L)
            isBufferTimeout = true
        } else {
            isBufferTimeout = false
        }
    }

    val safeScale = if (!isLive) extraZoomScale.coerceIn(1f, 2f) else 1f
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val videoModifier = if (safeScale > 1.01f) {
            Modifier.requiredSize(maxWidth * safeScale, maxHeight * safeScale)
        } else {
            Modifier.fillMaxSize()
        }
        if (useLibVlcFallback) {
            AndroidView(
                modifier = videoModifier,
                factory = { ctx ->
                    org.videolan.libvlc.util.VLCVideoLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        keepScreenOn = true
                        playerEngine.attachLibVlcView(this)
                    }
                },
                update = {
                    playerEngine.updateLibVlcScale(resizeMode)
                },
                onRelease = {
                    playerEngine.detachLibVlcView()
                }
            )
        } else {
            AndroidView(
                modifier = videoModifier,
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
                    val targetVersion = playerInstanceVersion
                    if (playerView.player !== playerEngine.exoPlayer || playerView.tag != targetVersion) {
                        playerView.tag = targetVersion
                        playerView.player = playerEngine.exoPlayer
                    }
                    if (playerView.resizeMode != resizeMode) {
                        playerView.resizeMode = resizeMode
                        playerView.requestLayout()
                    }
                }
            )
        }

        // Custom overlay slot (HUD, gradients, watermark, indicators)
        overlayContent()

        // ── FASE 33 & 34: Overlay de estado — error visible / fuera de línea + loader discreto ──
        val externalLauncher = currentUrl?.takeIf { it.isNotBlank() }?.let { url ->
            {
                playerEngine.pause()
                openStreamInExternalPlayer(context, url)
            }
        }
        when (val state = playbackState) {
            is PlaybackState.Error -> ErrorOverlay(
                message = if (!isLive && state.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    when (state.httpStatusCode) {
                        401, 403 -> "🎬 El servidor de películas y series de tu proveedor rechazó esta cuenta (HTTP ${state.httpStatusCode}).\nEl en vivo sí funciona. Contacta a tu proveedor."
                        404 -> "🎬 Contenido no encontrado en el servidor del proveedor (Error 404)."
                        429 -> "🎬 Límite de conexiones alcanzado (Error 429).\nCierra otros dispositivos con la misma cuenta, espera unos segundos y pulsa Reintentar."
                        else -> "🎬 El servidor del proveedor rechazó la reproducción${state.httpStatusCode?.let { " (HTTP $it)" } ?: ""}.\nPulsa Reintentar o prueba con otro título."
                    }
                } else state.errorMessage,
                errorCode = state.errorCode,
                canRetry = state.canRetry,
                onRetry = { playerEngine.retry() },
                onOpenExternal = externalLauncher,
                modifier = Modifier.fillMaxSize()
            )
            PlaybackState.Buffering -> {
                if (isBufferTimeout) {
                    // Si el stream no responde tras 10s continuos de buffering, notificar fuera de línea
                    ErrorOverlay(
                        message = if (isLive)
                            "📡 Canal fuera de línea o sin señal del proveedor.\nEl servidor no está emitiendo este stream."
                        else
                            "🎬 La película está tardando demasiado en cargar desde el servidor del proveedor.\nPulsa Reintentar.",
                        errorCode = -1,
                        canRetry = true,
                        onRetry = {
                            isBufferTimeout = false
                            playerEngine.retry()
                        },
                        onOpenExternal = externalLauncher,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (useOrangeSpinner) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF6B00),
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(46.dp)
                            )
                        } else {
                            com.lelouch.core.designsystem.components.LelouchCyberpunkLoader(
                                size = 68.dp,
                                strokeWidth = 4.5.dp
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

private fun openStreamInExternalPlayer(context: Context, streamUrl: String) {
    val baseIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(streamUrl), "video/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        val vlcIntent = Intent(baseIntent).setPackage("org.videolan.vlc")
        context.startActivity(vlcIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent.createChooser(baseIntent, "Abrir señal con..."))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Instala VLC para abrir esta señal externa.", Toast.LENGTH_SHORT).show()
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
    onOpenExternal: (() -> Unit)? = null,
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
            if (canRetry || onOpenExternal != null) {
                val retryFocusRequester = remember { FocusRequester() }
                val externalFocusRequester = remember { FocusRequester() }
                var retryFocused by remember { mutableStateOf(false) }
                var externalFocused by remember { mutableStateOf(false) }

                LaunchedEffect(canRetry, onOpenExternal) {
                    kotlinx.coroutines.delay(150L)
                    try {
                        if (canRetry) retryFocusRequester.requestFocus()
                        else externalFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (onOpenExternal != null) {
                                    try { externalFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (canRetry) {
                                    try { retryFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                                true
                            }
                            else -> false
                        }
                    }
                ) {
                    if (canRetry) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp),
                            border = if (retryFocused) BorderStroke(3.dp, Color.White) else null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .focusRequester(retryFocusRequester)
                                .onFocusChanged { retryFocused = it.isFocused }
                        ) {
                            Text(text = "🔄 REINTENTAR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    if (onOpenExternal != null) {
                        Button(
                            onClick = onOpenExternal,
                            shape = RoundedCornerShape(10.dp),
                            border = if (externalFocused) BorderStroke(3.dp, Color.White) else null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B00),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .focusRequester(externalFocusRequester)
                                .onFocusChanged { externalFocused = it.isFocused }
                        ) {
                            Text(text = "🎬 ABRIR EN VLC", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
