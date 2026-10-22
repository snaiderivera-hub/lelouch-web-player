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
 * Loader circular segmentado estilo Neón Cyberpunk (FASE 34).
 * Presenta 8 segmentos curvados con extremos redondeados (StrokeCap.Round),
 * resplandor exterior y estela rotatoria que replica fielmente el diseño del usuario.
 */
@Composable
fun LelouchCyberpunkLoader(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    segmentCount: Int = 8,
    color: Color = LelouchCyanAccent,
    strokeWidth: Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CyberpunkLoaderTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val radius = (this.size.minDimension / 2f) - (strokePx * 1.2f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val arcSweep = 360f / segmentCount

        for (i in 0 until segmentCount) {
            val segmentBaseAngle = (i * arcSweep)
            val diff = (segmentBaseAngle - rotation + 360f) % 360f
            val normalizedTrail = 1f - (diff / 360f)
            val alpha = (0.12f + 0.88f * normalizedTrail).coerceIn(0.10f, 1f)

            val segmentLength = arcSweep * 0.60f
            val startAngle = segmentBaseAngle + (arcSweep - segmentLength) / 2f

            // Glow exterior difuso
            if (alpha > 0.35f) {
                drawArc(
                    color = color.copy(alpha = alpha * 0.35f),
                    startAngle = startAngle,
                    sweepAngle = segmentLength,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokePx * 2.2f,
                        cap = StrokeCap.Round
                    )
                )
            }

            // Trazo principal nítido con puntas redondeadas
            drawArc(
                color = color.copy(alpha = alpha),
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
