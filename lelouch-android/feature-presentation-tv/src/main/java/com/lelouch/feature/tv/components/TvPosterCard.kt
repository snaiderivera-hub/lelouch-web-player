package com.lelouch.feature.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.*

/**
 * Xbox/Leanback style TV card with 2:3 poster ratio, Coil image loading,
 * 1.08x D-Pad focus zoom, and Cyan Lelouch border glow.
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
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        label = "posterScale"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) LelouchCardFocused else LelouchSurface)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) LelouchCyanAccent else LelouchBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        // Poster Image
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
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
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
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
                    text = "★ ${String.format("%.1f", rating)}",
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
