package com.lelouch.feature.tv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.model.SourceType

/**
 * Modal rápido para Android TV que permite intercambiar
 * entre listas/cuentas IPTV directamente desde la cabecera con navegación D-Pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAccountSwitchModal(
    activeSource: SourceConfig?,
    allSources: List<SourceConfig>,
    onSelectSource: (SourceConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100L)
        try {
            firstItemRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .fillMaxHeight(0.75f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(LelouchSurface)
                    .border(1.5.dp, LelouchCyanAccent.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(28.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ENCABEZADO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🔄",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "CAMBIAR LISTA / CUENTA IPTV",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Presiona [OK] para activar y cargar la lista inmediatamente:",
                                color = LelouchTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (allSources.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay otras cuentas configuradas",
                                color = LelouchTextSecondary,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        TvLazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(allSources, key = { _, src -> src.id }) { index, source ->
                                val isActive = (source.id == activeSource?.id || source.isActive)
                                val itemRequester = if (index == 0) firstItemRequester else remember { FocusRequester() }

                                Surface(
                                    onClick = { onSelectSource(source) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(itemRequester),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isActive) LelouchCyanAccent.copy(alpha = 0.12f) else LelouchSurfaceVariant,
                                        focusedContainerColor = LelouchCyanAccent.copy(alpha = 0.35f),
                                        pressedContainerColor = LelouchCyanAccent.copy(alpha = 0.35f)
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border(BorderStroke(1.dp, if (isActive) LelouchCyanAccent else LelouchBorder)),
                                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isActive) Color(0xFF10B981) else Color(0xFF6B7280))
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column {
                                                Text(
                                                    text = source.name.ifBlank { "Lista IPTV" },
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                val typeLabel = if (source.type == SourceType.M3U) "Lista M3U" else "Xtream Codes"
                                                val expLabel = if (source.expireDate.isNullOrBlank()) "♾️ Ilimitado" else "📅 Vence: ${source.formattedExpireDate}"
                                                Text(
                                                    text = "$typeLabel • $expLabel",
                                                    color = LelouchTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.25f))
                                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "✓ ACTIVA",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Presiona [ATRÁS] en el control para cerrar",
                            color = LelouchTextMuted,
                            fontSize = 12.sp
                        )

                        Surface(
                            onClick = onDismiss,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = LelouchSurfaceVariant,
                                focusedContainerColor = Color(0xFF334155)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, LelouchBorder)),
                                focusedBorder = Border(BorderStroke(1.5.dp, Color.White))
                            )
                        ) {
                            Text(
                                text = "Cerrar",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

