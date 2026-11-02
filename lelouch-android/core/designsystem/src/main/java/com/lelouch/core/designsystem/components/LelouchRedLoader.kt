package com.lelouch.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.R

/**
 * Spinner Circular Rojo Neón 1:1 con la referencia oficial de Zona-Pirata.
 * Aro base translúcido oscuro con arco giratorio en degradé rojo escarlata y resplandor.
 */
@Composable
fun LelouchRedSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    strokeWidth: Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RedSpinnerTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
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

        // 1. Pista circular de fondo (anillo base oscuro sutil)
        drawCircle(
            color = Color(0xFF1F1F1F),
            radius = radius,
            center = center,
            style = Stroke(width = strokePx * 0.8f)
        )

        // 2. Trazo continuo en degradé rojo rotatorio (Escarlata -> Rubí brillante)
        val stepCount = 36
        val stepSweep = 360f / stepCount

        for (i in 0 until stepCount) {
            val angle = (i * stepSweep)
            val diff = (angle - rotation + 360f) % 360f
            val progress = 1f - (diff / 360f)

            val alpha = (progress * progress).coerceIn(0.05f, 1f)
            val segmentColor = Color(
                red = 1f,
                green = (0.15f * progress),
                blue = (0.25f * progress),
                alpha = alpha
            )

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

            // Resplandor exterior en la cabeza luminosa del arco
            if (progress > 0.65f) {
                drawArc(
                    color = Color(0xFFFF0033).copy(alpha = (progress - 0.65f) * 0.45f),
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

/**
 * Pantalla completa de Carga Oficial Lelouch TV.
 * Reproduce exactamente la composición: Fondo Negro puro, Halo rojizo circular trasero,
 * Logo nítido cromado al frente, Spinner rojo 76px y Título "CARGANDO".
 */
@Composable
fun LelouchRedLoadingScreen(
    modifier: Modifier = Modifier,
    statusText: String = "PREPARANDO EL CONTENIDO...",
    isTv: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AuraPulseTransition")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Emblema Central con Halo Rojizo Trasero (Idéntico a Zona-Pirata)
            Box(
                modifier = Modifier.padding(bottom = if (isTv) 24.dp else 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // 1. Halo Rojizo Circular Trasero (Backlight Aura)
                Canvas(
                    modifier = Modifier.size((if (isTv) 280.dp else 210.dp) * auraScale)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF0033).copy(alpha = 0.60f),
                                Color(0xFFFF0033).copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2f
                        )
                    )
                }

                // 2. Logotipo oficial nítido cromado con alas Geass al frente
                Image(
                    painter = painterResource(id = R.drawable.logo_lelouch_tv),
                    contentDescription = "Lelouch TV",
                    modifier = Modifier
                        .width(if (isTv) 380.dp else 290.dp)
                        .heightIn(max = if (isTv) 220.dp else 170.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Spinner anular rojo
            LelouchRedSpinner(
                size = if (isTv) 72.dp else 60.dp,
                strokeWidth = if (isTv) 5.5.dp else 4.5.dp
            )

            Spacer(modifier = Modifier.height(if (isTv) 22.dp else 16.dp))

            // Título "CARGANDO"
            Text(
                text = "CARGANDO",
                color = Color.White,
                fontSize = if (isTv) 28.sp else 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (isTv) 10.dp else 8.dp))

            // Subtítulo con el estado de sincronización actual
            Text(
                text = statusText.uppercase(),
                color = Color(0xFF8E8E98),
                fontSize = if (isTv) 12.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(if (isTv) 20.dp else 16.dp))

            // Barra de progreso neón rojo estilizada
            Box(
                modifier = Modifier
                    .width(if (isTv) 280.dp else 200.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A1A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF8B0000),
                                    Color(0xFFFF0033),
                                    Color(0xFFFF4D6D)
                                )
                            )
                        )
                )
            }
        }
    }
}
