package com.lelouch.feature.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.BixDarkBackground
import com.lelouch.core.designsystem.BixOrangeAccent
import com.lelouch.core.designsystem.BixSurfaceCard
import com.lelouch.core.designsystem.BixTextMuted
import com.lelouch.core.designsystem.LelouchCyanAccent
import com.lelouch.core.player.cast.DlnaDevice

/**
 * FASE 38 · Selector de dispositivos DLNA para móvil.
 *
 * Se pinta como capa sobre el reproductor (no como BottomSheet) para no depender de la
 * ventana del Activity ni interferir con la orientación forzada a horizontal.
 */
@Composable
fun MobileCastSheet(
    devices: List<DlnaDevice>,
    isSearching: Boolean,
    message: String?,
    castingDeviceName: String?,
    statusText: String? = null,
    onRefresh: () -> Unit,
    onSelect: (DlnaDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(BixDarkBackground)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                // Evita que un toque dentro del panel cierre la capa por bubbling.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = LelouchCyanAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enviar a pantalla",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Dispositivos DLNA en tu red Wi-Fi",
                        color = BixTextMuted,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = BixTextMuted,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isSearching -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = LelouchCyanAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = statusText ?: "Buscando dispositivos…",
                            color = BixTextMuted,
                            fontSize = 13.sp
                        )
                    }
                    if (statusText != null) {
                        TextButton(onClick = onDismiss) {
                            Text(text = "Cancelar", color = BixOrangeAccent, fontSize = 13.sp)
                        }
                    }
                }

                devices.isEmpty() -> Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message ?: "No se encontraron dispositivos.",
                        color = BixTextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = onRefresh) {
                        Text(text = "Volver a buscar", color = BixOrangeAccent, fontSize = 13.sp)
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices, key = { it.udn }) { device ->
                        DeviceRow(
                            device = device,
                            isCasting = castingDeviceName == device.name,
                            onClick = { onSelect(device) }
                        )
                    }
                }
            }

            if (message != null && devices.isNotEmpty() && !isSearching) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, color = BixTextMuted, fontSize = 11.sp)
            }

            if (castingDeviceName != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reproduciendo en $castingDeviceName",
                        color = LelouchCyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDisconnect) {
                        Text(text = "Detener", color = BixOrangeAccent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DlnaDevice, isCasting: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCasting) LelouchCyanAccent.copy(alpha = 0.12f) else BixSurfaceCard)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = null,
            tint = if (isCasting) LelouchCyanAccent else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            device.modelName?.takeIf { it.isNotBlank() && it != device.name }?.let {
                Text(text = it, color = BixTextMuted, fontSize = 10.sp, maxLines = 1)
            }
        }
        if (isCasting) {
            Text(text = "Activo", color = LelouchCyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
