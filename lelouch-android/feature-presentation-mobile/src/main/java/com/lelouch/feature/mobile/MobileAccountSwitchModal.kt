package com.lelouch.feature.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType

/**
 * Modal para Android Mobile que permite cambiar de lista / cuenta IPTV
 * instantáneamente al tocar el nombre de la lista en la cabecera.
 */
@Composable
fun MobileAccountSwitchModal(
    activeSource: SourceConfig?,
    allSources: List<SourceConfig>,
    onSelectSource: (SourceConfig) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF141721))
                    .border(1.dp, LelouchCyanAccent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Evita propagar al fondo
                    )
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "CAMBIAR LISTA / CUENTA",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    "Selecciona una cuenta para activar:",
                                    color = LelouchTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = LelouchTextSecondary)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (allSources.isEmpty()) {
                        Text(
                            "No hay otras cuentas configuradas",
                            color = LelouchTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(allSources, key = { it.id }) { source ->
                                val isActive = (source.id == activeSource?.id || source.isActive)
                                val borderColor = if (isActive) LelouchCyanAccent else Color.White.copy(alpha = 0.08f)
                                val bgColor = if (isActive) LelouchCyanAccent.copy(alpha = 0.12f) else Color(0xFF1C1F2B)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bgColor)
                                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                        .clickable {
                                            onSelectSource(source)
                                        }
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isActive) Color(0xFF10B981) else Color(0xFF6B7280))
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = source.name.ifBlank { "Lista IPTV" },
                                                    color = if (isActive) LelouchCyanAccent else Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                val typeLabel = if (source.type == SourceType.M3U) "Lista M3U" else "Xtream Codes"
                                                val expText = if (source.expireDate.isNullOrBlank()) "Ilimitado" else "Vence: ${source.formattedExpireDate}"
                                                Text(
                                                    text = "$typeLabel • $expText",
                                                    color = LelouchTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVA",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            onDismiss()
                            onOpenSettings()
                        }) {
                            Text("⚙️ Gestionar en Ajustes", color = LelouchCyanAccent, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

