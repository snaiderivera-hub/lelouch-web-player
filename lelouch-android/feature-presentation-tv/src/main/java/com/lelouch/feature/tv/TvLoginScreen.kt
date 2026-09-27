package com.lelouch.feature.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.lelouch.core.designsystem.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLoginScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    syncStatusText: String = "",
    onLoginClick: (serverUrl: String, user: String, pass: String) -> Unit = { _, _, _ -> }
) {
    var serverUrl by remember { mutableStateOf("http://67.220.71.35:8880") }
    var username by remember { mutableStateOf("@full2") }
    var password by remember { mutableStateOf("JdC2QtxtSDda") }

    var isButtonFocused by remember { mutableStateOf(false) }

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
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(20.dp))
                .background(LelouchSurface.copy(alpha = 0.9f))
                .border(1.dp, LelouchBorder, RoundedCornerShape(20.dp))
                .padding(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Columna Izquierda: Identidad LELOUCH
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 40.dp)
            ) {
                Text(
                    text = "LELOUCH",
                    color = LelouchCyanAccent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "REPRODUCTOR IPTV NATIVO",
                    color = LelouchTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Conecta tus credenciales Xtream Codes para sincronizar canales en vivo, películas y series con experiencia cinemática estilo consola.",
                    color = LelouchTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(30.dp))

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
            }

            // Columna Derecha: Formulario D-Pad
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { androidx.compose.material3.Text("URL del Servidor IPTV") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Dns, contentDescription = null, tint = LelouchCyanAccent) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LelouchCyanAccent,
                        unfocusedBorderColor = LelouchBorder,
                        focusedLabelColor = LelouchCyanAccent,
                        focusedTextColor = LelouchTextPrimary,
                        unfocusedTextColor = LelouchTextPrimary,
                        cursorColor = LelouchCyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { androidx.compose.material3.Text("Usuario") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Person, contentDescription = null, tint = LelouchCyanAccent) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LelouchCyanAccent,
                        unfocusedBorderColor = LelouchBorder,
                        focusedLabelColor = LelouchCyanAccent,
                        focusedTextColor = LelouchTextPrimary,
                        unfocusedTextColor = LelouchTextPrimary,
                        cursorColor = LelouchCyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { androidx.compose.material3.Text("Contraseña") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Lock, contentDescription = null, tint = LelouchCyanAccent) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LelouchCyanAccent,
                        unfocusedBorderColor = LelouchBorder,
                        focusedLabelColor = LelouchCyanAccent,
                        focusedTextColor = LelouchTextPrimary,
                        unfocusedTextColor = LelouchTextPrimary,
                        cursorColor = LelouchCyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Botón TV interactivo con foco D-Pad y glow cian
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .onFocusChanged { isButtonFocused = it.isFocused }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isButtonFocused -> LelouchCyanAccent
                                isLoading -> LelouchBorder
                                else -> LelouchSurfaceVariant
                            }
                        )
                        .border(
                            width = if (isButtonFocused) 2.5.dp else 1.dp,
                            color = if (isButtonFocused) Color.White else LelouchBorder,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.tv.material3.Button(
                        onClick = {
                            if (!isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                onLoginClick(serverUrl.trim(), username.trim(), password.trim())
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = if (isLoading) "Sincronizando..." else "Iniciar Sesión en TV (OK)",
                            color = if (isButtonFocused) LelouchBackground else LelouchTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
