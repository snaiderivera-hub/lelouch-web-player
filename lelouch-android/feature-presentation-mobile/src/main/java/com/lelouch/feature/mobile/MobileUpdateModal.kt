package com.lelouch.feature.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lelouch.core.designsystem.*
import com.lelouch.core.network.update.AppUpdateManager
import com.lelouch.core.network.update.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

sealed interface MobileUpdateState {
    data object Idle : MobileUpdateState
    data object Checking : MobileUpdateState
    data class Available(val info: UpdateInfo) : MobileUpdateState
    data object UpToDate : MobileUpdateState
    data class Downloading(val progress: Float, val downloadedMb: Float, val totalMb: Float) : MobileUpdateState
    data class ReadyToInstall(val apkFile: File) : MobileUpdateState
    data class Error(val message: String) : MobileUpdateState
}

@Composable
fun MobileUpdateModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<MobileUpdateState>(MobileUpdateState.Idle) }
    var customUrlInput by remember { mutableStateOf("") }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var showCustomUrlField by remember { mutableStateOf(false) }

    fun checkUpdates(customUrl: String? = null) {
        updateState = MobileUpdateState.Checking
        coroutineScope.launch {
            val result = AppUpdateManager.checkForUpdates(context, customUrl)
            result.fold(
                onSuccess = { info ->
                    updateState = if (info != null) MobileUpdateState.Available(info) else MobileUpdateState.UpToDate
                },
                onFailure = { error ->
                    updateState = MobileUpdateState.Error(
                        "No se pudo verificar: ${error.message ?: "Sin conexión a internet"}"
                    )
                }
            )
        }
    }

    fun startDownload(url: String) {
        updateState = MobileUpdateState.Downloading(0f, 0f, 0f)
        coroutineScope.launch {
            val result = AppUpdateManager.downloadApk(context, url) { prog, dMb, tMb ->
                updateState = MobileUpdateState.Downloading(prog, dMb, tMb)
            }
            result.fold(
                onSuccess = { file ->
                    downloadedFile = file
                    updateState = MobileUpdateState.ReadyToInstall(file)
                    AppUpdateManager.installApk(context, file)
                },
                onFailure = { error ->
                    updateState = MobileUpdateState.Error("Fallo en la descarga: ${error.message}")
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        checkUpdates()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LelouchSurface)
                    .border(1.5.dp, LelouchCyanAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LelouchCyanAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = LelouchCyanAccent
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Actualizaciones OTA",
                                color = LelouchTextPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Para Teléfonos y TV Boxes",
                                color = LelouchCyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = LelouchTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Descarga e instala las nuevas versiones directamente por Wi-Fi sin transferir APKs por USB ni cables.",
                    color = LelouchTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Estado Dinámico
                when (val state = updateState) {
                    is MobileUpdateState.Checking -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSurfaceVariant)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = LelouchCyanAccent,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Buscando nueva versión en GitHub...",
                                    color = LelouchCyanAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    is MobileUpdateState.UpToDate -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSuccess.copy(alpha = 0.15f))
                                .border(1.dp, LelouchSuccess.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "✅ ¡App al Día!",
                                    color = LelouchSuccess,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tienes instalada la última versión disponible.",
                                    color = LelouchTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    is MobileUpdateState.Available -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchCyanAccent.copy(alpha = 0.12f))
                                .border(1.dp, LelouchCyanAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎉 ¡Nueva Versión!",
                                    color = LelouchCyanAccent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LelouchCyanAccent)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = state.info.versionName,
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.info.changelog.ifBlank { "Corrección de canales, adelantar/retroceder en reproductor VOD y mejoras de estabilidad." },
                                color = LelouchTextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { startDownload(state.info.downloadUrl) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descargar e Instalar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    is MobileUpdateState.Downloading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSurfaceVariant)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val percent = (state.progress * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Descargando actualización...",
                                    color = LelouchCyanAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "$percent%",
                                    color = LelouchCyanAccent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = LelouchCyanAccent,
                                trackColor = LelouchBorder
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${String.format("%.1f", state.downloadedMb)} MB / ${String.format("%.1f", state.totalMb)} MB",
                                color = LelouchTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    is MobileUpdateState.ReadyToInstall -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSuccess.copy(alpha = 0.15f))
                                .border(1.dp, LelouchSuccess, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📦 Descarga completada",
                                color = LelouchSuccess,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "El paquete de instalación está listo en el almacenamiento temporal.",
                                color = LelouchTextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { downloadedFile?.let { AppUpdateManager.installApk(context, it) } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LelouchSuccess),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Instalar Actualización Ahora", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is MobileUpdateState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchLiveRed.copy(alpha = 0.15f))
                                .border(1.dp, LelouchLiveRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "⚠️ ${state.message}",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { checkUpdates() },
                                colors = ButtonDefaults.buttonColors(containerColor = LelouchSurfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reintentar Búsqueda", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    is MobileUpdateState.Idle -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opción para alternar URL directa o IP local
                TextButton(
                    onClick = { showCustomUrlField = !showCustomUrlField }
                ) {
                    Text(
                        text = if (showCustomUrlField) "Ocultar URL personalizada ▲" else "Instalar desde IP local o URL directa ▼",
                        color = LelouchCyanAccent,
                        fontSize = 12.sp
                    )
                }

                AnimatedVisibility(visible = showCustomUrlField) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Ejemplo: http://192.168.1.5:8080/app-debug.apk",
                            color = LelouchTextMuted,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            placeholder = { Text("http://...", fontSize = 12.sp, color = LelouchTextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LelouchCyanAccent,
                                unfocusedBorderColor = LelouchBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (customUrlInput.isNotBlank()) {
                                    startDownload(customUrlInput.trim())
                                }
                            },
                            enabled = customUrlInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Descargar desde URL Personalizada", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botones inferiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { checkUpdates() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = LelouchSurfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verificar", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = LelouchBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cerrar", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
