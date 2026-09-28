package com.lelouch.feature.tv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lelouch.core.player.PlayerConnection

@Composable
fun TvPlayerScreen(
    videoUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerConnection = remember { PlayerConnection(context) }
    
    // Observamos el estado, aunque Media3 ya actualiza el PlayerView automáticamente
    val isPlaying by playerConnection.isPlaying.collectAsState()

    LaunchedEffect(playerConnection) {
        // En cuanto se conecte al servicio, iniciamos la reproducción
        playerConnection.play(videoUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            playerConnection.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Configuración para Android TV
                    useController = true 
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    
                    // Solo si el controller asíncrono ya está listo:
                    // (En un entorno de producción, esperaríamos a que PlayerConnection 
                    // emita que el player no es null)
                    player = playerConnection.getPlayer()
                }
            },
            update = { playerView ->
                // Actualiza el player por si no estaba listo en el factory initial
                if (playerView.player == null) {
                    playerView.player = playerConnection.getPlayer()
                }
            }
        )
    }
}
