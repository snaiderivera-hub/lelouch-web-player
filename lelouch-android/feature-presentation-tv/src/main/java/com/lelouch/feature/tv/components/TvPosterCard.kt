package com.lelouch.feature.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lelouch.core.designsystem.*

private val PosterBottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))

/**
 * Tarjeta de Póster nativa optimizada para Android TV (D-Pad).
 * Elimina animaciones de software redundantes y delega 100% el escalado y bordes
 * a Surface (hardware Skia/OpenGL) con downsampling en Coil para 60 FPS estables.
 */
@Composable
fun TvPosterCard(
    title: String,
    posterUrl: String?,
    rating: Double = 0.0,
    year: String? = null,
    width: Dp = 150.dp,
    height: Dp = 225.dp,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val sizeModifier = if (width != androidx.compose.ui.unit.Dp.Unspecified && height != androidx.compose.ui.unit.Dp.Unspecified) {
        Modifier.width(width).height(height)
    } else if (width != androidx.compose.ui.unit.Dp.Unspecified) {
        Modifier.width(width)
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .then(sizeModifier)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            },
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp),
            focusedShape = RoundedCornerShape(12.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LelouchSurface,
            focusedContainerColor = LelouchCardFocused,
            pressedContainerColor = LelouchCardFocused
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, LelouchBorder)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, LelouchCyanAccent))
        )
    ) {
        // Poster Image con downsampling para no saturar memoria GPU de la TV Box
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = remember(posterUrl) {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(false)
                        .size(300, 450)
                        .build()
                },
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LelouchSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.take(2).uppercase(),
                    color = LelouchCyanAccent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Degradado inferior para legibilidad del título
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(PosterBottomScrim)
        )

        // Badge de Rating en la esquina superior derecha
        if (rating > 0.0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.5.dp, LelouchCyanAccent.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "★ %.1f".format(rating),
                    color = Color(0xFFFFD700),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Título y Año en la parte inferior
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = title,
                color = if (isFocused) LelouchCyanAccent else LelouchTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!year.isNullOrBlank()) {
                Text(
                    text = year,
                    color = LelouchTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
