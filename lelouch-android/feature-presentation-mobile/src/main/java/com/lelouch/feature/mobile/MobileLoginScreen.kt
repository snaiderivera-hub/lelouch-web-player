package com.lelouch.feature.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig

@Composable
fun MobileLoginScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    savedSources: List<SourceConfig> = emptyList(),
    onSelectSavedSource: (SourceConfig) -> Unit = {},
    onSyncCloudSources: () -> Unit = {},
    onLoginClick: (serverUrl: String, user: String, pass: String) -> Unit = { _, _, _ -> }
) {
    var isManualFormVisible by remember { mutableStateOf(false) }

    var serverUrl by remember { mutableStateOf("http://67.220.71.35:8880") }
    var username by remember { mutableStateOf("@full2") }
    var password by remember { mutableStateOf("JdC2QtxtSDda") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Isotipo y Título LELOUCH
            Text(
                text = "LELOUCH",
                style = LelouchTypography.displayMedium.copy(
                    color = LelouchCyanAccent,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            )
            Text(
                text = "Gestor de Listas IPTV",
                style = LelouchTypography.bodyMedium.copy(color = LelouchTextSecondary)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Selector de Modo: Listas Guardadas vs Ingreso Manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!isManualFormVisible && savedSources.isNotEmpty()) "Tus Listas Guardadas:" else "Credenciales Xtream:",
                    style = LelouchTypography.titleSmall.copy(color = LelouchTextPrimary, fontWeight = FontWeight.Bold)
                )

                TextButton(onClick = { isManualFormVisible = !isManualFormVisible }) {
                    Text(
                        text = if (isManualFormVisible) "📋 Ver Listas Guardadas" else "➕ Ingresar Otra Cuenta",
                        color = LelouchCyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isManualFormVisible && savedSources.isNotEmpty()) {
                // Tarjetas de Cuentas Guardadas
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    savedSources.forEach { source ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (source.isActive) Color(0xFF10B981) else LelouchBorder,
                                    shape = RoundedCornerShape(12.dp)
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
                                            style = LelouchTypography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (source.isActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${source.serverUrl} (${source.username})",
                                        color = LelouchTextSecondary,
                                        style = LelouchTypography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = { onSelectSavedSource(source) },
                                    colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Entrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onSyncCloudSources,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = LelouchCyanAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizar Cuentas de Supabase Cloud", color = LelouchTextPrimary, fontSize = 13.sp)
                }
            } else {
                // Formulario Manual Xtream
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("URL del Servidor") },
                    placeholder = { Text("http://ejemplo.com:8080") },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = LelouchCyanAccent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LelouchCyanAccent,
                        unfocusedBorderColor = LelouchBorder,
                        focusedLabelColor = LelouchCyanAccent,
                        cursorColor = LelouchCyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario") },
                    placeholder = { Text("ejemplo_usuario") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = LelouchCyanAccent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LelouchCyanAccent,
                        unfocusedBorderColor = LelouchBorder,
                        focusedLabelColor = LelouchCyanAccent,
                        cursorColor = LelouchCyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = LelouchCyanAccent) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = LelouchTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            onLoginClick(serverUrl.trim(), username.trim(), password.trim())
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LelouchCyanAccent,
                        unfocusedBorderColor = LelouchBorder,
                        focusedLabelColor = LelouchCyanAccent,
                        cursorColor = LelouchCyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onLoginClick(serverUrl.trim(), username.trim(), password.trim())
                    },
                    enabled = !isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LelouchCyanAccent,
                        contentColor = LelouchBackground,
                        disabledContainerColor = LelouchBorder,
                        disabledContentColor = LelouchTextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = LelouchBackground,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Conectando y Sincronizando...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Iniciar Sesión y Sincronizar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // Mensaje de Error
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LelouchError.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = LelouchTypography.bodySmall.copy(color = LelouchError)
                    )
                }
            }
        }
    }
}
