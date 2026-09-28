package com.lelouch.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lelouch.core.designsystem.LelouchCyanAccent

/**
 * Anillo de foco unificado para LELOUCH.
 *
 * Utiliza [LelouchCyanAccent] para indicar que el elemento tiene el foco actual en el D-Pad.
 */
@Composable
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(12.dp), width: Dp = 3.dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(width, if (focused) LelouchCyanAccent else Color.Transparent, shape)
}
