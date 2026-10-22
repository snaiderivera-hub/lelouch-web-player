package com.lelouch.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Indicador de carga circular continuo con aro fino, degradé degradado (Neón Cyan -> Azul -> Violeta / Púrpura)
 * y rotación suave continua (FASE 34), recreando con exactitud la imagen de referencia solicitada por el usuario.
 */
@Composable
fun LelouchCyberpunkLoader(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 4.dp
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
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)

        // 1. Aro base blanco/translúcido fino de fondo
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = radius,
            center = center,
            style = Stroke(width = strokePx * 0.75f)
        )

        // 2. Trazo continuo en degradé rotatorio (Neón Cyan -> Deep Blue -> Violeta / Púrpura)
        val stepCount = 36
        val stepSweep = 360f / stepCount

        for (i in 0 until stepCount) {
            val angle = (i * stepSweep)
            val diff = (angle - rotation + 360f) % 360f
            val progress = 1f - (diff / 360f) // 1.0 en la cabeza, 0.0 en la cola

            // Mapeo suave de colores vivos: Violeta (#A855F7) -> Azul Eléctrico (#3B82F6) -> Neón Cyan (#00E5FF)
            val segmentColor = when {
                progress < 0.35f -> {
                    val t = progress / 0.35f
                    Color(
                        red = 0.50f + (0.15f * t),
                        green = 0.20f + (0.25f * t),
                        blue = 0.85f + (0.10f * t),
                        alpha = 0.15f + (0.45f * t)
                    )
                }
                progress < 0.75f -> {
                    val t = (progress - 0.35f) / 0.40f
                    Color(
                        red = 0.40f - (0.35f * t),
                        green = 0.40f + (0.45f * t),
                        blue = 0.95f + (0.05f * t),
                        alpha = 0.60f + (0.30f * t)
                    )
                }
                else -> {
                    val t = (progress - 0.75f) / 0.25f
                    Color(
                        red = 0.05f * (1f - t),
                        green = 0.85f + (0.15f * t),
                        blue = 1.0f,
                        alpha = 0.90f + (0.10f * t)
                    )
                }
            }

            drawArc(
                color = segmentColor,
                startAngle = angle,
                sweepAngle = stepSweep * 1.15f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round
                )
            )

            // Resplandor exterior suave para los puntos brillantes de la cabeza
            if (progress > 0.65f) {
                drawArc(
                    color = segmentColor.copy(alpha = (progress - 0.65f) * 0.4f),
                    startAngle = angle,
                    sweepAngle = stepSweep * 1.25f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokePx * 2.2f,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

