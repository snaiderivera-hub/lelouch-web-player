package com.lelouch.feature.tv.focus

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.LelouchBackground
import com.lelouch.core.designsystem.LelouchCardFocused
import com.lelouch.core.designsystem.LelouchCyanAccent
import com.lelouch.core.designsystem.LelouchSurface
import com.lelouch.core.designsystem.LelouchSurfaceVariant

/**
 * Pantalla mínima aislada (Paso 11) para validar el comportamiento del D-Pad físico
 * de Xiaomi TV Box (XMRM-M3) sin interferencias de Xtream API, imágenes, Room ni MediaPlayer.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DpadFocusLabScreen(
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }

    var lastKey by remember { mutableStateOf("NINGUNA") }
    var focusedTag by remember { mutableStateOf("NAV_LAB") }
    var previousTag by remember { mutableStateOf("INICIO") }
    val logs = remember { mutableStateListOf<String>() }

    // Memoria por riel
    var lastRowAIndex by remember { mutableIntStateOf(0) }
    var lastRowBIndex by remember { mutableIntStateOf(0) }
    var lastRowCIndex by remember { mutableIntStateOf(0) }

    // FocusRequesters deterministas
    val navLabRequester = remember { FocusRequester() }
    val rowARequesters = remember { List(5) { FocusRequester() } }
    val rowBRequesters = remember { List(5) { FocusRequester() } }
    val rowCRequesters = remember { List(5) { FocusRequester() } }

    LaunchedEffect(Unit) {
        navLabRequester.requestFocus()
    }

    fun recordFocus(newTag: String) {
        if (focusedTag != newTag) {
            val logEntry = "$lastKey: $focusedTag -> $newTag"
            previousTag = focusedTag
            focusedTag = newTag
            if (logs.size > 8) logs.removeAt(0)
            logs.add(logEntry)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastKey = when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
                        KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
                        KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
                        KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "DPAD_CENTER"
                        KeyEvent.KEYCODE_BACK -> "BACK"
                        else -> "KEY_${event.nativeKeyEvent.keyCode}"
                    }
                }
                false
            }
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header & HUD de Diagnóstico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🧪 XIAOMI D-PAD FOCUS LAB (PASO 11)",
                        color = LelouchCyanAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Prueba pura de hardware y grafo Compose. Sin Room, Sin Red, Sin Player.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                // HUD en Tiempo Real
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, LelouchCyanAccent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "LAST KEY: $lastKey",
                            color = Color(0xFFFFD700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "CURRENT FOCUS: $focusedTag",
                            color = LelouchCyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "RAIL MEMORY: [A:$lastRowAIndex | B:$lastRowBIndex | C:$lastRowCIndex]",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // TOP NAV
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FocusLabButton(
                    label = "TOP NAV (A1 Anchor)",
                    tag = "NAV_LAB",
                    focusRequester = navLabRequester,
                    onFocus = { recordFocus("NAV_LAB") },
                    onClick = {},
                    upRequester = FocusRequester.Cancel,
                    downRequester = rowARequesters[lastRowAIndex],
                    leftRequester = FocusRequester.Cancel
                )

                FocusLabButton(
                    label = "⬅ Volver al Catálogo",
                    tag = "NAV_BACK",
                    onFocus = { recordFocus("NAV_BACK") },
                    onClick = onBack,
                    upRequester = FocusRequester.Cancel,
                    downRequester = rowARequesters[lastRowAIndex],
                    rightRequester = FocusRequester.Cancel
                )
            }

            // ROW A
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "ROW A (Recents)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (0 until 5).forEach { index ->
                        val cardTag = "A${index + 1}"
                        FocusLabButton(
                            label = "[$cardTag]",
                            tag = cardTag,
                            focusRequester = rowARequesters[index],
                            onFocus = {
                                lastRowAIndex = index
                                recordFocus(cardTag)
                            },
                            upRequester = navLabRequester,
                            downRequester = rowBRequesters[lastRowBIndex],
                            leftRequester = if (index == 0) FocusRequester.Cancel else null,
                            rightRequester = if (index == 4) FocusRequester.Cancel else null
                        )
                    }
                }
            }

            // ROW B
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "ROW B (Top Rated)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (0 until 5).forEach { index ->
                        val cardTag = "B${index + 1}"
                        FocusLabButton(
                            label = "[$cardTag]",
                            tag = cardTag,
                            focusRequester = rowBRequesters[index],
                            onFocus = {
                                lastRowBIndex = index
                                recordFocus(cardTag)
                            },
                            upRequester = rowARequesters[lastRowAIndex],
                            downRequester = rowCRequesters[lastRowCIndex],
                            leftRequester = if (index == 0) FocusRequester.Cancel else null,
                            rightRequester = if (index == 4) FocusRequester.Cancel else null
                        )
                    }
                }
            }

            // ROW C
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "ROW C (Action)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (0 until 5).forEach { index ->
                        val cardTag = "C${index + 1}"
                        FocusLabButton(
                            label = "[$cardTag]",
                            tag = cardTag,
                            focusRequester = rowCRequesters[index],
                            onFocus = {
                                lastRowCIndex = index
                                recordFocus(cardTag)
                            },
                            upRequester = rowBRequesters[lastRowBIndex],
                            downRequester = FocusRequester.Cancel, // Límite inferior: nunca perder foco
                            leftRequester = if (index == 0) FocusRequester.Cancel else null,
                            rightRequester = if (index == 4) FocusRequester.Cancel else null
                        )
                    }
                }
            }

            // Registro de Transiciones
            Text(text = "HISTORIAL DE EVENTOS D-PAD:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                logs.takeLast(4).forEach { entry ->
                    Text(text = entry, color = LelouchCyanAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun FocusLabButton(
    label: String,
    tag: String,
    onFocus: () -> Unit,
    onClick: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    var modifier: Modifier = Modifier
    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }

    modifier = modifier.focusProperties {
        if (upRequester != null) up = upRequester
        if (downRequester != null) down = downRequester
        if (leftRequester != null) left = leftRequester
        if (rightRequester != null) right = rightRequester
    }

    Box(
        modifier = modifier
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) LelouchCardFocused else LelouchSurfaceVariant)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) LelouchCyanAccent else Color.DarkGray,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused) LelouchCyanAccent else Color.White,
            fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
