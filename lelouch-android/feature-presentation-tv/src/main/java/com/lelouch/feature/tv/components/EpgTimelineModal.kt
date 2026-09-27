package com.lelouch.feature.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.EpgProgram
import com.lelouch.core.model.LiveStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * TV Electronic Program Guide (EPG) Timeline Grid dialog with 2-hour horizontal timeline slots.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpgTimelineModal(
    channels: List<LiveStream>,
    onSelectChannel: (LiveStream) -> Unit,
    onDismiss: () -> Unit
) {
    val currentTime = remember { System.currentTimeMillis() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val timeSlots = remember(currentTime) {
        listOf(
            timeFormat.format(Date(currentTime)),
            timeFormat.format(Date(currentTime + 30 * 60 * 1000)),
            timeFormat.format(Date(currentTime + 60 * 60 * 1000)),
            timeFormat.format(Date(currentTime + 90 * 60 * 1000)),
            timeFormat.format(Date(currentTime + 120 * 60 * 1000))
        )
    }

    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LelouchBackground.copy(alpha = 0.97f))
                .padding(36.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra Superior: Título y Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GUÍA ELECTRÓNICA DE PROGRAMACIÓN (EPG)",
                            color = LelouchTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Programación en vivo y próximos eventos para hoy",
                            color = LelouchCyanAccent,
                            fontSize = 13.sp
                        )
                    }

                    var isCloseFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .focusRequester(firstItemFocusRequester)
                            .focusable()
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCloseFocused) LelouchCyanAccent else LelouchSurface)
                            .border(1.dp, if (isCloseFocused) Color.White else LelouchBorder, RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = if (isCloseFocused) Color.Black else Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fila de Horas de la Línea de Tiempo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 200.dp, bottom = 8.dp)
                ) {
                    timeSlots.forEach { slotTime ->
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "⏰ $slotTime",
                                color = LelouchCyanAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Grid de Canales y Programación
                TvLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    val channelsToDisplay = if (channels.isNotEmpty()) channels.take(30) else listOf(
                        LiveStream(id = "1", streamId = 101, num = 101, name = "ESPN HD", categoryId = "1"),
                        LiveStream(id = "2", streamId = 102, num = 102, name = "Fox Sports", categoryId = "1"),
                        LiveStream(id = "3", streamId = 103, num = 103, name = "TyC Sports", categoryId = "1"),
                        LiveStream(id = "4", streamId = 104, num = 104, name = "DirecTV Sports", categoryId = "1"),
                        LiveStream(id = "5", streamId = 105, num = 105, name = "HBO Max HD", categoryId = "1"),
                        LiveStream(id = "6", streamId = 106, num = 106, name = "Star Channel", categoryId = "1")
                    )

                    itemsIndexed(channelsToDisplay) { _, channel ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Columna Canal Izquierda
                            var isChannelFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .width(190.dp)
                                    .height(65.dp)
                                    .focusable()
                                    .onFocusChanged { isChannelFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChannelFocused) LelouchCardFocused else LelouchSurface)
                                    .border(1.dp, if (isChannelFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(8.dp))
                                    .clickable { onSelectChannel(channel) }
                                    .padding(8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(
                                        text = "CH ${channel.num} - ${channel.name}",
                                        color = if (isChannelFocused) LelouchCyanAccent else LelouchTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = channel.categoryName ?: "En Vivo",
                                        color = LelouchTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Riel de Programas Horizontales (Bloque Now & Next)
                            TvLazyRow(
                                modifier = Modifier.focusRestorer(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val programs = listOf(
                                    EpgProgram(
                                        id = "${channel.streamId}_1",
                                        channelId = channel.streamId.toString(),
                                        title = channel.epgChannelId ?: "Fútbol Internacional en Directo",
                                        startTimestamp = currentTime - 30 * 60 * 1000,
                                        stopTimestamp = currentTime + 45 * 60 * 1000,
                                        startTimeFormatted = timeSlots[0],
                                        stopTimeFormatted = timeSlots[2],
                                        progressPercent = 0.55f
                                    ),
                                    EpgProgram(
                                        id = "${channel.streamId}_2",
                                        channelId = channel.streamId.toString(),
                                        title = "Edición Central de Noticias",
                                        startTimestamp = currentTime + 45 * 60 * 1000,
                                        stopTimestamp = currentTime + 105 * 60 * 1000,
                                        startTimeFormatted = timeSlots[2],
                                        stopTimeFormatted = timeSlots[4],
                                        progressPercent = 0f
                                    ),
                                    EpgProgram(
                                        id = "${channel.streamId}_3",
                                        channelId = channel.streamId.toString(),
                                        title = "Resumen de la Jornada y Goles",
                                        startTimestamp = currentTime + 105 * 60 * 1000,
                                        stopTimestamp = currentTime + 180 * 60 * 1000,
                                        startTimeFormatted = timeSlots[4],
                                        stopTimeFormatted = "22:00",
                                        progressPercent = 0f
                                    )
                                )

                                itemsIndexed(
                                    items = programs,
                                    key = { _, prog -> prog.id }
                                ) { progIndex, program ->
                                    var isProgFocused by remember { mutableStateOf(false) }
                                    val isLiveNow = (progIndex == 0)

                                    Box(
                                        modifier = Modifier
                                            .width(220.dp)
                                            .height(65.dp)
                                            .focusable()
                                            .onFocusChanged { isProgFocused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isProgFocused -> LelouchCardFocused
                                                    isLiveNow -> LelouchSurfaceVariant
                                                    else -> LelouchSurface
                                                }
                                            )
                                            .border(
                                                width = if (isProgFocused) 2.dp else 1.dp,
                                                color = if (isProgFocused) LelouchCyanAccent else if (isLiveNow) LelouchCyanAccent.copy(alpha = 0.4f) else LelouchBorder,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onSelectChannel(channel) }
                                            .padding(8.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${program.startTimeFormatted} - ${program.stopTimeFormatted}",
                                                    color = if (isLiveNow) LelouchCyanAccent else LelouchTextMuted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (isLiveNow) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(LelouchLiveRed)
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("EN VIVO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = program.title,
                                                color = if (isProgFocused) LelouchCyanAccent else LelouchTextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (isLiveNow && program.progressPercent > 0f) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(LelouchSurface)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(program.progressPercent)
                                                            .fillMaxHeight()
                                                            .background(LelouchCyanAccent)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
