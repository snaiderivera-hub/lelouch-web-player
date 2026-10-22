package com.lelouch.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lelouch.core.designsystem.LelouchCyanAccent

/**
 * Loader circular segmentado estilo Neón Cyberpunk Multi-Color (FASE 34).
 * Presenta 8 segmentos curvados con extremos redondeados (StrokeCap.Round),
 * resplandor neón exterior y un degradé dinámico de colores vivos (cian neón, azul eléctrico,
 * violeta púrpura y magenta) con estela rotatoria.
 */
@Composable
fun LelouchCyberpunkLoader(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    segmentCount: Int = 8,
    strokeWidth: Dp = 5.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CyberpunkLoaderTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Paleta de colores vivos y saturados por segmento (Neón Cyan -> Azul Eléctrico -> Púrpura -> Turquesa)
    val segmentColors = listOf(
        Color(0xFF00F0FF), // Neón Cyan brillante
        Color(0xFF00D2FF), // Sky Cyan
        Color(0xFF0099FF), // Electric Blue
        Color(0xFF4361EE), // Royal Indigo
        Color(0xFF7209B7), // Electric Violet
        Color(0xFFB5179E), // Vivid Magenta
        Color(0xFF00E5FF), // Pure Cyan
        Color(0xFF00F5D4)  // Aquamarine
    )

    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val radius = (this.size.minDimension / 2f) - (strokePx * 1.3f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val arcSweep = 360f / segmentCount

        for (i in 0 until segmentCount) {
            val segmentBaseAngle = (i * arcSweep)
            val diff = (segmentBaseAngle - rotation + 360f) % 360f
            val normalizedTrail = 1f - (diff / 360f)
            val alpha = (0.15f + 0.85f * normalizedTrail).coerceIn(0.12f, 1f)

            val baseColor = segmentColors[i % segmentColors.size]
            val segmentLength = arcSweep * 0.62f
            val startAngle = segmentBaseAngle + (arcSweep - segmentLength) / 2f

            // Glow exterior difuso vibrante
            if (alpha > 0.30f) {
                drawArc(
                    color = baseColor.copy(alpha = alpha * 0.45f),
                    startAngle = startAngle,
                    sweepAngle = segmentLength,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokePx * 2.4f,
                        cap = StrokeCap.Round
                    )
                )
            }

            // Trazo principal nítido con puntas redondeadas
            drawArc(
                color = baseColor.copy(alpha = alpha),
                startAngle = startAngle,
                sweepAngle = segmentLength,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
