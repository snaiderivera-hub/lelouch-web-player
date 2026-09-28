package com.lelouch.feature.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.lelouch.core.designsystem.*
import com.lelouch.feature.tv.update.TvUpdateManager
import com.lelouch.feature.tv.update.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val progress: Float, val downloadedMb: Float, val totalMb: Float) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvUpdateModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var customUrlInput by remember { mutableStateOf("") }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    val defaultFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        defaultFocusRequester.requestFocus()
        // Auto-verificar actualización al abrir
        updateState = UpdateState.Checking
        coroutineScope.launch {
            val result = TvUpdateManager.checkForUpdates(context)
            result.fold(
                onSuccess = { info ->
                    updateState = if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
                },
                onFailure = { error ->
                    updateState = UpdateState.Error("No se pudo verificar actualización: ${error.message ?: "Sin conexión"}")
                }
            )
        }
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
            Column(
                modifier = Modifier
                    .width(620.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(LelouchSurface)
                    .border(1.5.dp, LelouchCyanAccent.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Título
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🚀",
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "ACTUALIZACIONES OVER-THE-AIR (OTA)",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Actualiza Lelouch TV directamente por Wi-Fi sin necesidad de memorias USB.",
                    color = LelouchTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Estado Visual
                when (val state = updateState) {
                    is UpdateState.Checking -> {
                        Text(
                            text = "Buscando nueva versión en línea...",
                            color = LelouchCyanAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is UpdateState.UpToDate -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSuccess.copy(alpha = 0.15f))
                                .border(1.dp, LelouchSuccess.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✅ ¡Tienes la versión más reciente instalada!",
                                color = LelouchSuccess,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    is UpdateState.Available -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchCyanAccent.copy(alpha = 0.12f))
                                .border(1.dp, LelouchCyanAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "🎉 ¡Nueva versión disponible: ${state.info.versionName}!",
                                color = LelouchCyanAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.info.changelog.ifBlank { "Mejoras de rendimiento y corrección de categorías." },
                                color = LelouchTextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val percent = (state.progress * 100).toInt()
                            Text(
                                text = "Descargando actualización: $percent%",
                                color = LelouchCyanAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${String.format("%.1f", state.downloadedMb)} MB / ${String.format("%.1f", state.totalMb)} MB",
                                color = LelouchTextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(LelouchSurfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(state.progress)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(LelouchCyanAccent, LelouchBlueSecondary)
                                            )
                                        )
                                )
                            }
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSuccess.copy(alpha = 0.2f))
                                .border(1.dp, LelouchSuccess, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📦 Descarga completa. Listo para instalar.",
                                color = LelouchSuccess,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    is UpdateState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchLiveRed.copy(alpha = 0.15f))
                                .border(1.dp, LelouchLiveRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚠️ ${state.message}",
                                color = Color(0xFFFF6B6B),
                                fontSize = 13.sp
                            )
                        }
                    }

                    is UpdateState.Idle -> {}
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Campo opcional para URL directa de APK (servidor local en casa / PC)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Opcional: Instalar desde URL directa o IP local (ej: http://192.168.1.X:8080/app-debug.apk):",
                        color = LelouchTextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LelouchSurfaceVariant)
                            .border(1.dp, LelouchBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (customUrlInput.isEmpty()) {
                            Text("URL personalizada de APK...", color = LelouchTextMuted, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(LelouchCyanAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (val state = updateState) {
                        is UpdateState.Available -> {
                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        updateState = UpdateState.Downloading(0f, 0f, 0f)
                                        val res = TvUpdateManager.downloadApk(context, state.info.downloadUrl) { prog, dMb, tMb ->
                                            updateState = UpdateState.Downloading(prog, dMb, tMb)
                                        }
                                        res.fold(
                                            onSuccess = { file ->
                                                downloadedFile = file
                                                updateState = UpdateState.ReadyToInstall(file)
                                                TvUpdateManager.installApk(context, file)
                                            },
                                            onFailure = { err ->
                                                updateState = UpdateState.Error("Error al descargar APK: ${err.message}")
                                            }
                                        )
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = LelouchCyanAccent,
                                    focusedContainerColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(defaultFocusRequester)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text("⬇️ DESCARGAR E INSTALAR (OK)", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }

                        is UpdateState.ReadyToInstall -> {
                            Surface(
                                onClick = {
                                    downloadedFile?.let { TvUpdateManager.installApk(context, it) }
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = LelouchSuccess,
                                    focusedContainerColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(defaultFocusRequester)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text("🚀 INSTALAR AHORA (OK)", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }

                        else -> {
                            Surface(
                                onClick = {
                                    if (customUrlInput.isNotBlank()) {
                                        coroutineScope.launch {
                                            updateState = UpdateState.Downloading(0f, 0f, 0f)
                                            val res = TvUpdateManager.downloadApk(context, customUrlInput.trim()) { prog, dMb, tMb ->
                                                updateState = UpdateState.Downloading(prog, dMb, tMb)
                                            }
                                            res.fold(
                                                onSuccess = { file ->
                                                    downloadedFile = file
                                                    updateState = UpdateState.ReadyToInstall(file)
                                                    TvUpdateManager.installApk(context, file)
                                                },
                                                onFailure = { err ->
                                                    updateState = UpdateState.Error("Error al descargar APK: ${err.message}")
                                                }
                                            )
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            updateState = UpdateState.Checking
                                            val result = TvUpdateManager.checkForUpdates(context)
                                            result.fold(
                                                onSuccess = { info ->
                                                    updateState = if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
                                                },
                                                onFailure = { error ->
                                                    updateState = UpdateState.Error("Error al verificar: ${error.message}")
                                                }
                                            )
                                        }
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = LelouchCyanAccent,
                                    focusedContainerColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(defaultFocusRequester)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (customUrlInput.isNotBlank()) "⬇️ DESCARGAR APK PERSONALIZADO" else "🔄 REINTENTAR BÚSQUEDA",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = LelouchSurfaceVariant,
                            focusedContainerColor = LelouchBorderFocused
                        ),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("CERRAR (BACK)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
