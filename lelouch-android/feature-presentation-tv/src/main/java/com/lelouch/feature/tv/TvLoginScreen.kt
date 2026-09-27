package com.lelouch.feature.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLoginScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    syncStatusText: String = "",
    savedSources: List<SourceConfig> = emptyList(),
    onSelectSavedSource: (SourceConfig) -> Unit = {},
    onSyncCloudSources: () -> Unit = {},
    onLoginClick: (serverUrl: String, user: String, pass: String) -> Unit = { _, _, _ -> }
) {
    var isManualFormVisible by remember { mutableStateOf(false) }

    var serverUrl by remember { mutableStateOf("http://67.220.71.35:8880") }
    var username by remember { mutableStateOf("@full2") }
    var password by remember { mutableStateOf("JdC2QtxtSDda") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(LelouchSurfaceVariant.copy(alpha = 0.4f), LelouchBackground),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(LelouchSurface.copy(alpha = 0.92f))
                .border(1.dp, LelouchBorder, RoundedCornerShape(20.dp))
                .padding(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Columna Izquierda: Identidad LELOUCH
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .padding(end = 32.dp)
            ) {
                Text(
                    text = "LELOUCH",
                    color = LelouchCyanAccent,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "GESTOR DE LISTAS IPTV NATIVO",
                    color = LelouchTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Selecciona una de tus cuentas IPTV registradas o conecta un nuevo proveedor Xtream Codes para sincronizar canales, películas y series.",
                    color = LelouchTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Estado de Sincronización en vivo
                AnimatedVisibility(visible = isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LelouchCyanAccent.copy(alpha = 0.12f))
                            .border(1.dp, LelouchCyanAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = if (syncStatusText.isNotEmpty()) syncStatusText else "Sincronizando catálogo con el servidor...",
                            color = LelouchCyanAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Mensaje de Error
                if (errorMessage != null && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LelouchError.copy(alpha = 0.15f))
                            .border(1.dp, LelouchError.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = LelouchError,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Botón Sincronizar desde Nube Supabase
                var isCloudBtnFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .focusable()
                        .onFocusChanged { isCloudBtnFocused = it.isFocused }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCloudBtnFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                        .border(1.dp, if (isCloudBtnFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(8.dp))
                        .clickable { onSyncCloudSources() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (isCloudBtnFocused) Color.Black else LelouchCyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "☁️ Sincronizar Cuentas de la Nube",
                            color = if (isCloudBtnFocused) Color.Black else LelouchTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Columna Derecha: Selector de Listas / Formulario
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Barra de Conmutación: Listas Guardadas vs Ingreso Manual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!isManualFormVisible) "Tus Listas IPTV Guardadas:" else "Ingresar Credenciales:",
                        color = LelouchTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    var isToggleFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .focusable()
                            .onFocusChanged { isToggleFocused = it.isFocused }
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isToggleFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                            .clickable { isManualFormVisible = !isManualFormVisible }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isManualFormVisible) "📋 Ver Listas Guardadas" else "➕ Ingresar Otra Cuenta",
                            color = if (isToggleFocused) Color.Black else LelouchCyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isManualFormVisible && savedSources.isNotEmpty()) {
                    // Selector de Listas Guardadas (Cada tarjeta es focusable y con 1 clic activa)
                    TvLazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(savedSources) { _, source ->
                            var isCardFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusable()
                                    .onFocusChanged { isCardFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCardFocused) LelouchCardFocused else LelouchSurfaceVariant)
                                    .border(
                                        width = if (isCardFocused) 2.dp else 1.dp,
                                        color = if (isCardFocused) LelouchCyanAccent else LelouchBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onSelectSavedSource(source)
                                    }
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = source.name,
                                                color = LelouchTextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (source.isActive) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF10B981))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIVA",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Servidor: ${source.serverUrl}  •  Usuario: ${source.username}",
                                            color = LelouchTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCardFocused) Color.White else LelouchCyanAccent)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Conectar (OK)",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Formulario Manual Xtream Codes
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("URL del Servidor IPTV") },
                        placeholder = { Text("http://67.220.71.35:8880") },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Default.Dns,
                                contentDescription = null,
                                tint = LelouchCyanAccent
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LelouchCyanAccent,
                            unfocusedBorderColor = LelouchBorder,
                            focusedTextColor = LelouchTextPrimary,
                            unfocusedTextColor = LelouchTextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Usuario") },
                        placeholder = { Text("@full2") },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = LelouchCyanAccent
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LelouchCyanAccent,
                            unfocusedBorderColor = LelouchBorder,
                            focusedTextColor = LelouchTextPrimary,
                            unfocusedTextColor = LelouchTextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = LelouchCyanAccent
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LelouchCyanAccent,
                            unfocusedBorderColor = LelouchBorder,
                            focusedTextColor = LelouchTextPrimary,
                            unfocusedTextColor = LelouchTextPrimary
                        )
                    )

                    var isButtonFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .onFocusChanged { isButtonFocused = it.isFocused }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isButtonFocused) Color.White else LelouchCyanAccent)
                            .border(1.dp, if (isButtonFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable(enabled = !isLoading) {
                                onLoginClick(serverUrl, username, password)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoading) "Conectando con el Servidor..." else "Iniciar Sesión y Sincronizar",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
