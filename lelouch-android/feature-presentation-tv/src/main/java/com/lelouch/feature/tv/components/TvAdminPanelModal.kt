package com.lelouch.feature.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig

/**
 * Panel Administrador completo para Android TV:
 * - Selector y gestor de playlists/cuentas guardadas.
 * - Activación instantánea de listas.
 * - Adición de nuevas cuentas Xtream Codes.
 * - Sincronización bidireccional con Supabase Cloud.
 * - Opciones de mantenimiento y logout.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAdminPanelModal(
    activeSource: SourceConfig?,
    allSources: List<SourceConfig>,
    onActivateSource: (sourceId: String) -> Unit,
    onDeleteSource: (sourceId: String) -> Unit,
    onAddSource: (serverUrl: String, user: String, pass: String, name: String) -> Unit,
    onSyncCloudSources: () -> Unit,
    onForceSync: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Listas Guardadas, 1 = Agregar Nueva, 2 = Mantenimiento

    // Formulario de nueva cuenta
    var newName by remember { mutableStateOf("") }
    var newServerUrl by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isAddingLoading by remember { mutableStateOf(false) }

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
                    .fillMaxWidth(0.90f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LelouchSurface)
                    .border(1.dp, LelouchBorder, RoundedCornerShape(16.dp))
                    .padding(28.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ENCABEZADO SUPERIOR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = LelouchCyanAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PANEL ADMINISTRADOR",
                                    color = LelouchTextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Gestión de Listas IPTV y Sincronización en la Nube",
                                    color = LelouchTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Botón Cerrar
                        var isCloseFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .focusable()
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .clip(CircleShape)
                                .background(if (isCloseFocused) LelouchLiveRed else LelouchSurfaceVariant)
                                .clickable { onDismiss() }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // PESTAÑAS DEL PANEL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val tabs = listOf(
                            "📋 Listas Guardadas (${allSources.size})",
                            "➕ Conectar Nueva Cuenta",
                            "⚡ Sincronización & Ajustes"
                        )
                        tabs.forEachIndexed { index, title ->
                            var isTabFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .focusable()
                                    .onFocusChanged { isTabFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isTabFocused -> LelouchCyanAccent.copy(alpha = 0.25f)
                                            selectedTab == index -> LelouchSurfaceVariant
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (isTabFocused) 2.dp else 1.dp,
                                        color = if (isTabFocused) LelouchCyanAccent else if (selectedTab == index) LelouchCyanAccent.copy(alpha = 0.5f) else LelouchBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = if (isTabFocused || selectedTab == index) LelouchTextPrimary else LelouchTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // CONTENIDO DINÁMICO SEGÚN PESTAÑA
                    when (selectedTab) {
                        0 -> {
                            // PESTAÑA 0: LISTAS GUARDADAS
                            TvLazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(allSources) { _, source ->
                                    val isCurrentActive = (source.id == activeSource?.id || source.serverUrl == activeSource?.serverUrl)
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
                                                color = if (isCardFocused) LelouchCyanAccent else if (isCurrentActive) Color(0xFF10B981) else LelouchBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Información de la Lista
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = source.name,
                                                        color = LelouchTextPrimary,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    if (isCurrentActive) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF10B981))
                                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "✓ EN USO",
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(LelouchCyanAccent.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "☁️ Supabase Cloud",
                                                            color = LelouchCyanAccent,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Servidor: ${source.serverUrl}  •  Usuario: ${source.username}  •  Contraseña: ••••••••",
                                                    color = LelouchTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            // Botones de Acción
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (!isCurrentActive) {
                                                    var isActFocused by remember { mutableStateOf(false) }
                                                    Box(
                                                        modifier = Modifier
                                                            .focusable()
                                                            .onFocusChanged { isActFocused = it.isFocused }
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isActFocused) Color.White else Color(0xFF10B981))
                                                            .clickable {
                                                                onActivateSource(source.id)
                                                            }
                                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "⚡ Activar",
                                                            color = if (isActFocused) Color.Black else Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                var isDelFocused by remember { mutableStateOf(false) }
                                                Box(
                                                    modifier = Modifier
                                                        .focusable()
                                                        .onFocusChanged { isDelFocused = it.isFocused }
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isDelFocused) LelouchLiveRed else LelouchSurface)
                                                        .clickable {
                                                            onDeleteSource(source.id)
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Eliminar",
                                                        tint = if (isDelFocused) Color.White else LelouchTextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // PESTAÑA 1: CONECTAR NUEVA CUENTA
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Ingresa las credenciales Xtream Codes para registrar una nueva cuenta IPTV:",
                                    color = LelouchTextSecondary,
                                    fontSize = 14.sp
                                )

                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("Nombre Personalizado (Opcional)") },
                                    placeholder = { Text("Ej. Mi IPTV Deportivo") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(0.7f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LelouchCyanAccent,
                                        unfocusedBorderColor = LelouchBorder,
                                        focusedTextColor = LelouchTextPrimary,
                                        unfocusedTextColor = LelouchTextPrimary
                                    )
                                )

                                OutlinedTextField(
                                    value = newServerUrl,
                                    onValueChange = { newServerUrl = it },
                                    label = { Text("URL del Servidor IPTV") },
                                    placeholder = { Text("http://servidor.com:8880") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(0.7f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LelouchCyanAccent,
                                        unfocusedBorderColor = LelouchBorder,
                                        focusedTextColor = LelouchTextPrimary,
                                        unfocusedTextColor = LelouchTextPrimary
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(0.7f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newUsername,
                                        onValueChange = { newUsername = it },
                                        label = { Text("Usuario") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = LelouchCyanAccent,
                                            unfocusedBorderColor = LelouchBorder,
                                            focusedTextColor = LelouchTextPrimary,
                                            unfocusedTextColor = LelouchTextPrimary
                                        )
                                    )

                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text("Contraseña") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = LelouchCyanAccent,
                                            unfocusedBorderColor = LelouchBorder,
                                            focusedTextColor = LelouchTextPrimary,
                                            unfocusedTextColor = LelouchTextPrimary
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    var isSubmitFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .focusable()
                                            .onFocusChanged { isSubmitFocused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSubmitFocused) Color.White else LelouchCyanAccent)
                                            .clickable {
                                                if (newServerUrl.isNotBlank() && newUsername.isNotBlank() && newPassword.isNotBlank()) {
                                                    isAddingLoading = true
                                                    onAddSource(newServerUrl, newUsername, newPassword, newName)
                                                    isAddingLoading = false
                                                    selectedTab = 0
                                                }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = if (isAddingLoading) "Conectando..." else "Conectar y Guardar Cuenta",
                                            color = Color.Black,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    var isSyncCloudFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .focusable()
                                            .onFocusChanged { isSyncCloudFocused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSyncCloudFocused) LelouchCyanAccent.copy(alpha = 0.25f) else LelouchSurfaceVariant)
                                            .border(1.dp, if (isSyncCloudFocused) LelouchCyanAccent else LelouchBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                onSyncCloudSources()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = LelouchCyanAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Sincronizar desde Supabase Cloud",
                                                color = LelouchTextPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // PESTAÑA 2: SINCRONIZACIÓN Y MANTENIMIENTO
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Opciones Avanzadas de Sincronización y Mantenimiento:",
                                    color = LelouchTextSecondary,
                                    fontSize = 14.sp
                                )

                                // Ficha del Proveedor Activo
                                activeSource?.let { src ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.75f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LelouchSurfaceVariant)
                                            .border(1.dp, LelouchBorder, RoundedCornerShape(10.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "Proveedor Activo: ${src.name}",
                                                color = LelouchCyanAccent,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Servidor: ${src.serverUrl}  •  Usuario: ${src.username}",
                                                color = LelouchTextSecondary,
                                                fontSize = 12.sp
                                            )
                                            src.expireDate?.let { exp ->
                                                Text(
                                                    text = "Vencimiento: $exp",
                                                    color = LelouchTextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    var isForceSyncFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .focusable()
                                            .onFocusChanged { isForceSyncFocused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isForceSyncFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                                            .border(1.dp, if (isForceSyncFocused) Color.White else LelouchBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                onForceSync()
                                                onDismiss()
                                            }
                                            .padding(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = if (isForceSyncFocused) Color.Black else LelouchCyanAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Forzar Resincronización Completa",
                                                color = if (isForceSyncFocused) Color.Black else LelouchTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    var isLogoutFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .focusable()
                                            .onFocusChanged { isLogoutFocused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isLogoutFocused) LelouchLiveRed else Color.Transparent)
                                            .border(1.dp, if (isLogoutFocused) Color.White else LelouchLiveRed, RoundedCornerShape(8.dp))
                                            .clickable {
                                                onLogout()
                                            }
                                            .padding(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ExitToApp,
                                                contentDescription = null,
                                                tint = if (isLogoutFocused) Color.White else LelouchLiveRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Cerrar Sesión / Cambiar Cuenta",
                                                color = if (isLogoutFocused) Color.White else LelouchLiveRed,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
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
