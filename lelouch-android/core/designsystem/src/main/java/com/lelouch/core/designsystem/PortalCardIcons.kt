package com.lelouch.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * FASE 32: Iconos Vectoriales Oficiales para las 4 Tarjetas Hero del Portal Dashboard
 * Reproduce fielmente las ilustraciones de la interfaz web (Imagen 2):
 * 1. TV EN VIVO: Televisor retro azul brillante con antenas, pantalla cian y bisel blanco.
 * 2. PELÍCULAS: Claqueta de cine inclinada con franjas diagonales y botón de play cian.
 * 3. SERIES: Carrete de película azul/cian con orificios circulares y cinta cinematográfica.
 * 4. DEPORTES: Balón de fútbol 3D con degradado azul marino y paneles geométricos.
 */

@Composable
fun TvCardVectorIcon(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val scaleX = w / 96f
            val scaleY = h / 96f

            // 1. Antenas
            drawLine(
                color = Color.White,
                start = Offset(42f * scaleX, 26f * scaleY),
                end = Offset(26f * scaleX, 9f * scaleY),
                strokeWidth = 3.5f * scaleX,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 3.5f * scaleX,
                center = Offset(26f * scaleX, 9f * scaleY)
            )

            drawLine(
                color = Color.White,
                start = Offset(54f * scaleX, 26f * scaleY),
                end = Offset(70f * scaleX, 9f * scaleY),
                strokeWidth = 3.5f * scaleX,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 3.5f * scaleX,
                center = Offset(70f * scaleX, 9f * scaleY)
            )

            // 2. Patas del televisor
            drawLine(
                color = Color.White,
                start = Offset(30f * scaleX, 74f * scaleY),
                end = Offset(23f * scaleX, 85f * scaleY),
                strokeWidth = 3.5f * scaleX,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(66f * scaleX, 74f * scaleY),
                end = Offset(73f * scaleX, 85f * scaleY),
                strokeWidth = 3.5f * scaleX,
                cap = StrokeCap.Round
            )

            // 3. Cuerpo del televisor (Bisel blanco/celeste)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFDBEAFE)),
                    startY = 24f * scaleY,
                    endY = 76f * scaleY
                ),
                topLeft = Offset(12f * scaleX, 24f * scaleY),
                size = Size(72f * scaleX, 52f * scaleY),
                cornerRadius = CornerRadius(12f * scaleX, 12f * scaleY)
            )
            drawRoundRect(
                color = Color(0xFFBAE6FD),
                topLeft = Offset(12f * scaleX, 24f * scaleY),
                size = Size(72f * scaleX, 52f * scaleY),
                cornerRadius = CornerRadius(12f * scaleX, 12f * scaleY),
                style = Stroke(width = 1.5f * scaleX)
            )

            // 4. Pantalla interior (Azul cian brillante)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0EA5E9), Color(0xFF0284C7)),
                    start = Offset(18f * scaleX, 30f * scaleY),
                    end = Offset(66f * scaleX, 70f * scaleY)
                ),
                topLeft = Offset(18f * scaleX, 30f * scaleY),
                size = Size(48f * scaleX, 40f * scaleY),
                cornerRadius = CornerRadius(8f * scaleX, 8f * scaleY)
            )

            // Brillo diagonal en la pantalla
            val glossPath = Path().apply {
                moveTo(21f * scaleX, 33f * scaleY)
                quadraticBezierTo(42f * scaleX, 30f * scaleY, 63f * scaleX, 36f * scaleY)
                quadraticBezierTo(42f * scaleX, 43f * scaleY, 21f * scaleX, 45f * scaleY)
                close()
            }
            drawPath(glossPath, color = Color.White.copy(alpha = 0.35f))

            // 5. Perillas a la derecha
            drawCircle(
                color = Color(0xFF0369A1),
                radius = 3.5f * scaleX,
                center = Offset(75f * scaleX, 40f * scaleY)
            )
            drawCircle(
                color = Color(0xFF0369A1),
                radius = 3.5f * scaleX,
                center = Offset(75f * scaleX, 52f * scaleY)
            )
            drawRoundRect(
                color = Color(0xFF0369A1),
                topLeft = Offset(71f * scaleX, 62f * scaleY),
                size = Size(8f * scaleX, 2.5f * scaleY),
                cornerRadius = CornerRadius(1.25f * scaleX, 1.25f * scaleY)
            )
            drawRoundRect(
                color = Color(0xFF0369A1),
                topLeft = Offset(71f * scaleX, 66f * scaleY),
                size = Size(8f * scaleX, 2.5f * scaleY),
                cornerRadius = CornerRadius(1.25f * scaleX, 1.25f * scaleY)
            )
        }
    }
}

@Composable
fun MovieCardVectorIcon(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val scaleX = w / 96f
            val scaleY = h / 96f

            // 1. Tapa inclinada superior (-15 grados)
            rotate(degrees = -14f, pivot = Offset(16f * scaleX, 35f * scaleY)) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(14f * scaleX, 19f * scaleY),
                    size = Size(68f * scaleX, 15f * scaleY),
                    cornerRadius = CornerRadius(4f * scaleX, 4f * scaleY)
                )
                drawRoundRect(
                    color = Color(0xFFBAE6FD),
                    topLeft = Offset(14f * scaleX, 19f * scaleY),
                    size = Size(68f * scaleX, 15f * scaleY),
                    cornerRadius = CornerRadius(4f * scaleX, 4f * scaleY),
                    style = Stroke(width = 1.2f * scaleX)
                )

                // Franjas diagonales azul oscuro
                val stripeWidth = 7f * scaleX
                val offsets = listOf(22f, 37f, 52f, 67f)
                offsets.forEach { ox ->
                    val stripePath = Path().apply {
                        moveTo((ox + 3f) * scaleX, 19f * scaleY)
                        lineTo((ox + 10f) * scaleX, 19f * scaleY)
                        lineTo((ox + 4f) * scaleX, 34f * scaleY)
                        lineTo((ox - 3f) * scaleX, 34f * scaleY)
                        close()
                    }
                    drawPath(stripePath, color = Color(0xFF0A2558))
                }
            }

            // 2. Base de la claqueta (Blanca)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFE0F2FE)),
                    startY = 37f * scaleY,
                    endY = 83f * scaleY
                ),
                topLeft = Offset(14f * scaleX, 37f * scaleY),
                size = Size(68f * scaleX, 46f * scaleY),
                cornerRadius = CornerRadius(8f * scaleX, 8f * scaleY)
            )
            drawRoundRect(
                color = Color(0xFFBAE6FD),
                topLeft = Offset(14f * scaleX, 37f * scaleY),
                size = Size(68f * scaleX, 46f * scaleY),
                cornerRadius = CornerRadius(8f * scaleX, 8f * scaleY),
                style = Stroke(width = 1.5f * scaleX)
            )

            // 3. Triángulo Play central azul/cian
            val playPath = Path().apply {
                moveTo(43f * scaleX, 49f * scaleY)
                lineTo(63f * scaleX, 60f * scaleY)
                lineTo(43f * scaleX, 71f * scaleY)
                close()
            }
            drawPath(
                playPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF0284C7)),
                    start = Offset(43f * scaleX, 49f * scaleY),
                    end = Offset(63f * scaleX, 71f * scaleY)
                )
            )
            drawPath(
                playPath,
                color = Color(0xFF38BDF8),
                style = Stroke(width = 1.2f * scaleX, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun SeriesCardVectorIcon(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val scaleX = w / 96f
            val scaleY = h / 96f

            // 1. Tira de película curva detrás
            val ribbonPath = Path().apply {
                moveTo(46f * scaleX, 75f * scaleY)
                cubicTo(64f * scaleX, 75f * scaleY, 80f * scaleX, 71f * scaleY, 82f * scaleX, 55f * scaleY)
                cubicTo(83f * scaleX, 45f * scaleY, 76f * scaleX, 39f * scaleY, 70f * scaleX, 39f * scaleY)
            }
            drawPath(
                ribbonPath,
                color = Color.White,
                style = Stroke(width = 8f * scaleX, cap = StrokeCap.Round)
            )
            drawPath(
                ribbonPath,
                color = Color(0xFF082A5C),
                style = Stroke(width = 3.5f * scaleX, cap = StrokeCap.Round)
            )

            // 2. Carrete circular principal
            val center = Offset(44f * scaleX, 45f * scaleY)
            val radius = 30f * scaleX

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFE0F2FE), Color(0xFFBAE6FD)),
                    start = Offset(14f * scaleX, 15f * scaleY),
                    end = Offset(74f * scaleX, 75f * scaleY)
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0xFF93C5FD),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f * scaleX)
            )
            drawCircle(
                color = Color(0xFF7DD3FC).copy(alpha = 0.6f),
                radius = radius - 4f * scaleX,
                center = center,
                style = Stroke(width = 1.2f * scaleX)
            )

            // 3. Orificio central oscuro con borde cian
            drawCircle(
                color = Color(0xFF061826),
                radius = 6.5f * scaleX,
                center = center
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 6.5f * scaleX,
                center = center,
                style = Stroke(width = 1.5f * scaleX)
            )

            // 4. 5 orificios radiales alrededor
            val holeDist = 18f * scaleX
            for (i in 0 until 5) {
                val angle = Math.toRadians((i * 72.0) - 90.0)
                val hx = center.x + (holeDist * cos(angle)).toFloat()
                val hy = center.y + (holeDist * sin(angle)).toFloat()
                drawCircle(
                    color = Color(0xFF061826),
                    radius = 5.2f * scaleX,
                    center = Offset(hx, hy)
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 5.2f * scaleX,
                    center = Offset(hx, hy),
                    style = Stroke(width = 1.2f * scaleX)
                )
            }
        }
    }
}

@Composable
fun SportsCardVectorIcon(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val scaleX = w / 96f
            val scaleY = h / 96f

            val center = Offset(48f * scaleX, 48f * scaleY)
            val radius = 33f * scaleX

            // 1. Esfera base sombreada 3D
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFDBEAFE), Color(0xFF93C5FD)),
                    center = Offset(36f * scaleX, 34f * scaleY),
                    radius = radius * 1.3f
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0xFFBAE6FD),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f * scaleX)
            )

            // 2. Pentágono central azul marino / cian
            val pPath = Path().apply {
                moveTo(48f * scaleX, 36f * scaleY)
                lineTo(59f * scaleX, 44f * scaleY)
                lineTo(55f * scaleX, 57f * scaleY)
                lineTo(41f * scaleX, 57f * scaleY)
                lineTo(37f * scaleX, 44f * scaleY)
                close()
            }
            drawPath(
                pPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0284C7), Color(0xFF082A5C)),
                    start = Offset(37f * scaleX, 36f * scaleY),
                    end = Offset(59f * scaleX, 57f * scaleY)
                )
            )
            drawPath(
                pPath,
                color = Color(0xFF00E5FF),
                style = Stroke(width = 1.4f * scaleX)
            )

            // 3. Costuras y parches geométricos hacia los bordes
            val seamColor = Color(0xFF082A5C)
            val seamWidth = 1.8f * scaleX

            // Línea hacia arriba
            drawLine(seamColor, Offset(48f * scaleX, 36f * scaleY), Offset(48f * scaleX, 16f * scaleY), seamWidth)
            // Hacia arriba-derecha
            drawLine(seamColor, Offset(59f * scaleX, 44f * scaleY), Offset(78f * scaleX, 36f * scaleY), seamWidth)
            // Hacia abajo-derecha
            drawLine(seamColor, Offset(55f * scaleX, 57f * scaleY), Offset(69f * scaleX, 74f * scaleY), seamWidth)
            // Hacia abajo-izquierda
            drawLine(seamColor, Offset(41f * scaleX, 57f * scaleY), Offset(27f * scaleX, 74f * scaleY), seamWidth)
            // Hacia arriba-izquierda
            drawLine(seamColor, Offset(37f * scaleX, 44f * scaleY), Offset(18f * scaleX, 36f * scaleY), seamWidth)

            // Parches periféricos
            val topPatch = Path().apply {
                moveTo(38f * scaleX, 15f * scaleY)
                lineTo(48f * scaleX, 16f * scaleY)
                lineTo(58f * scaleX, 15f * scaleY)
                lineTo(53f * scaleX, 22f * scaleY)
                lineTo(43f * scaleX, 22f * scaleY)
                close()
            }
            drawPath(topPatch, color = Color(0xFF0A2558))

            val rightPatch = Path().apply {
                moveTo(78f * scaleX, 36f * scaleY)
                lineTo(81f * scaleX, 48f * scaleY)
                lineTo(75f * scaleX, 58f * scaleY)
                lineTo(70f * scaleX, 47f * scaleY)
                close()
            }
            drawPath(rightPatch, color = Color(0xFF0A2558))

            val leftPatch = Path().apply {
                moveTo(18f * scaleX, 36f * scaleY)
                lineTo(15f * scaleX, 48f * scaleY)
                lineTo(21f * scaleX, 58f * scaleY)
                lineTo(26f * scaleX, 47f * scaleY)
                close()
            }
            drawPath(leftPatch, color = Color(0xFF0A2558))
        }
    }
}
