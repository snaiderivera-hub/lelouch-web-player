package com.lelouch.feature.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

@Composable
fun MobileLoginScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLoginClick: (serverUrl: String, user: String, pass: String) -> Unit = { _, _, _ -> }
) {
    var serverUrl by remember { mutableStateOf("http://67.220.71.35:8880") }
    var username by remember { mutableStateOf("@full2") }
    var password by remember { mutableStateOf("JdC2QtxtSDda") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground)
            .padding(24.dp),
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
                text = "Conecta tu proveedor Xtream Codes",
                style = LelouchTypography.bodyMedium.copy(color = LelouchTextSecondary)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Campo Servidor URL
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

            Spacer(modifier = Modifier.height(16.dp))

            // Campo Usuario
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                placeholder = { Text("Tu usuario IPTV") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = LelouchCyanAccent) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Campo Contraseña
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                placeholder = { Text("Tu contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = LelouchCyanAccent) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = LelouchTextSecondary
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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

            Spacer(modifier = Modifier.height(28.dp))

            // Botón de Inicio de Sesión
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
    }
}
