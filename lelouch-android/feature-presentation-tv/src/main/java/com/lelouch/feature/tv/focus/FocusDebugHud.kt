package com.lelouch.feature.tv.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.LelouchCyanAccent

/**
 * HUD de depuración de foco en tiempo real para Xiaomi TV Box.
 * Muestra el ciclo completo del evento:
 * SCREEN, KEY, CONSUMED, BEFORE, TARGET, AFTER y RESULT (SUCCESS / UNCHANGED / FOCUS_LOST / TARGET_NOT_COMPOSED / REQUEST_FAILED).
 * CONDICIONADO A DEBUG: Solo visible en compilaciones de depuración.
 */
@Composable
fun FocusDebugHud(
    tracker: FocusTracker,
    modifier: Modifier = Modifier
) {
    val resultColor = when (tracker.actualResult) {
        FocusResult.SUCCESS -> Color(0xFF10B981) // Verde esmeralda
        FocusResult.UNCHANGED -> Color(0xFFFFD700) // Amarillo dorado
        FocusResult.TARGET_NOT_COMPOSED -> Color(0xFFFF8C00) // Naranja intenso
        FocusResult.FOCUS_LOST -> Color(0xFFEF4444) // Rojo crítico
        FocusResult.REQUEST_FAILED -> Color(0xFFEC4899) // Magenta advertencia
    }

    Box(
        modifier = modifier
            .padding(top = 16.dp, end = 24.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .border(1.5.dp, resultColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(min = 320.dp, max = 400.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Header: Dispositivo, Pantalla y Tecla
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "XIAOMI D-PAD TRACE",
                    color = LelouchCyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = tracker.currentScreen,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Tecla y Consumido
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "KEY: ${tracker.lastKey}",
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "CONSUMED: ${tracker.eventConsumed}",
                    color = if (tracker.eventConsumed) Color(0xFF10B981) else Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // RESULTADO ESTRICTO DEL CICLO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(resultColor.copy(alpha = 0.25f))
                    .border(1.dp, resultColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESULT:",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = tracker.actualResult.label,
                        color = resultColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 1. BEFORE
            Text(
                text = "BEFORE:  ${tracker.beforeTag}",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "         [${tracker.beforeZone.label} | #${tracker.beforeIndex}]",
                color = Color.Gray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )

            // 2. REQUESTED TARGET
            Text(
                text = "TARGET:  ${tracker.requestedTargetTag}",
                color = Color(0xFF67E8F9),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "         [${tracker.requestedTargetZone.label} | #${tracker.requestedTargetIndex}]",
                color = Color(0xFF0284C7),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )

            // 3. AFTER (Confirmado únicamente por onFocusChanged)
            Text(
                text = "AFTER:   ${tracker.currentTag}",
                color = if (tracker.actualResult == FocusResult.SUCCESS) Color(0xFF10B981) else resultColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "         [${tracker.currentZone.label} | #${tracker.lastCardIndex}]",
                color = if (tracker.actualResult == FocusResult.SUCCESS) Color(0xFF059669) else resultColor.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Memoria de Rieles
            Text(
                text = "MEMORY:  RECENT=#${tracker.recentMovieLastIndex} | TOP_RATED=#${tracker.topRatedMovieLastIndex}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
