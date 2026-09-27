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
 * HUD de depuración de foco en tiempo real para verificar navegación física en Xiaomi TV Box.
 * Muestra el último botón D-Pad presionado, la zona actual, el elemento enfocado y el siguiente destino.
 */
@Composable
fun FocusDebugHud(
    tracker: FocusTracker,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(top = 16.dp, end = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.dp, LelouchCyanAccent.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(0.28f)
            ) {
                Text(
                    text = "XIAOMI D-PAD HUD",
                    color = LelouchCyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = tracker.currentScreen,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "KEY: ${tracker.lastKey}",
                color = Color(0xFFFFD700),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "ZONE: ${tracker.currentZone.label}",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "FOCUS: ${tracker.currentTag}",
                color = LelouchCyanAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "INDEX: [Row ${tracker.lastRowIndex} | Card #${tracker.lastCardIndex}]",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (tracker.targetDescription.isNotBlank()) {
                Text(
                    text = "TARGET: ${tracker.targetDescription}",
                    color = Color(0xFF10B981),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
