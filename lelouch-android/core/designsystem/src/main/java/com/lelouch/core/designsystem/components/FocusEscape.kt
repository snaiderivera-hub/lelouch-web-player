package com.lelouch.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Permite que el control remoto salga de campos de texto de una sola línea en Android TV.
 *
 * En Android TV, un TextField consume por defecto DPAD_UP y DPAD_DOWN para mover el cursor
 * de edición de texto, atrapando el foco y haciendo imposible bajar a los resultados con el D-pad.
 *
 * Este modificador intercepta el evento de vista previa y traslada el foco hacia arriba o abajo
 * utilizando el LocalFocusManager del sistema.
 */
@Composable
fun Modifier.releasesFocusVertically(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                else -> false
            }
        }
    }
}
