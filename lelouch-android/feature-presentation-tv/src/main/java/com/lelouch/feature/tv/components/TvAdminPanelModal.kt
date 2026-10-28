package com.lelouch.feature.tv.components

import android.view.KeyEvent
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
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
    onCheckUpdates: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Listas Guardadas, 1 = Agregar Nueva, 2 = Mantenimiento

    // Formulario de nueva cuenta
    var newName by remember { mutableStateOf("") }
    var newServerUrl by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isAddingLoading by remember { mutableStateOf(false) }
    val firstTabFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstTabFocusRequester.requestFocus()
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
                        val closeInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .clip(CircleShape)
                                .background(if (isCloseFocused) LelouchLiveRed else LelouchSurfaceVariant)
                                .onKeyEvent { keyEvent ->
                                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                                    val isCenterKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                      keyCode == KeyEvent.KEYCODE_ENTER ||
                                                      keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                                      keyCode == KeyEvent.KEYCODE_BUTTON_A

                                    if (isCenterKey) {
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                                            onDismiss()
                                            true
                                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .clickable(
                                    interactionSource = closeInteractionSource,
                                    indication = null
                                ) { onDismiss() }
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
                            val tabInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstTabFocusRequester) else Modifier)
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
                                    .onKeyEvent { keyEvent ->
                                        val keyCode = keyEvent.nativeKeyEvent.keyCode
                                        val isCenterKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                          keyCode == KeyEvent.KEYCODE_ENTER ||
                                                          keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                                          keyCode == KeyEvent.KEYCODE_BUTTON_A

                                        if (isCenterKey) {
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                                                selectedTab = index
                                                true
                                            } else if (keyEvent.type == KeyEventType.KeyUp) {
                                                true
                                            } else {
                                                false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .clickable(
                                        interactionSource = tabInteractionSource,
                                        indication = null
                                    ) { selectedTab = index }
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
                                itemsIndexed(
                                    items = allSources,
                                    key = { _, source -> "source_${source.id}" }
                                ) { _, source ->
                                    val isCurrentActive = (source.id == activeSource?.id || source.serverUrl == activeSource?.serverUrl)
                                    var isCardFocused by remember { mutableStateOf(false) }

                                    Surface(
                                        onClick = {
                                            if (!isCurrentActive) {
                                                onActivateSource(source.id)
                                                onDismiss()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isCardFocused = it.isFocused },
                                        shape = ClickableSurfaceDefaults.shape(
                                            shape = RoundedCornerShape(12.dp),
                                            focusedShape = RoundedCornerShape(12.dp)
                                        ),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = if (isCurrentActive) Color(0xFF0F2338) else LelouchSurfaceVariant,
                                            focusedContainerColor = Color(0xFF1E3A5F),
                                            pressedContainerColor = Color(0xFF1E3A5F)
                                        ),
                                        border = ClickableSurfaceDefaults.border(
                                            border = Border(androidx.compose.foundation.BorderStroke(if (isCurrentActive) 1.5.dp else 1.dp, if (isCurrentActive) Color(0xFF10B981) else LelouchBorder)),
                                            focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, LelouchCyanAccent))
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = source.name,
                                                        color = if (isCardFocused) Color.White else (if (isCurrentActive) LelouchCyanAccent else LelouchTextPrimary),
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
                                                    if (isCardFocused) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(LelouchCyanAccent)
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "PRESIONA OK",
                                                                color = Color.Black,
                                                                fontSize = 9.sp,
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
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val daysRem = source.expireDaysRemaining
                                                    val expColor = when {
                                                        daysRem != null && daysRem <= 0 -> Color(0xFFEF4444)
                                                        daysRem != null && daysRem <= 15 -> Color(0xFFF59E0B)
                                                        else -> Color(0xFF10B981)
                                                    }
                                                    val expLabel = when {
                                                        source.expireDate.isNullOrBlank() -> "♾️ Sin fecha / Ilimitado"
                                                        daysRem != null && daysRem <= 0 -> "🔴 Venció: ${source.formattedExpireDate}"
                                                        daysRem != null -> "📅 Vence: ${source.formattedExpireDate} (${daysRem} días)"
                                                        else -> "📅 Vence: ${source.formattedExpireDate}"
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(expColor.copy(alpha = 0.18f))
                                                            .border(1.dp, expColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = expLabel,
                                                            color = expColor,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    val maxC = source.maxConnections.coerceAtLeast(1)
                                                    val actC = source.activeConnections.coerceAtLeast(0)
                                                    val isBusy = actC >= maxC
                                                    val connColor = if (isBusy) Color(0xFFEF4444) else Color(0xFF10B981)
                                                    val connText = if (isBusy) "🔴 LISTA OCUPADA ($actC/$maxC Conex.)" else "🟢 LIBRE ($actC/$maxC Conex.)"
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(connColor.copy(alpha = 0.18f))
                                                            .border(1.dp, connColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = connText,
                                                            color = connColor,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(5.dp))
                                                Text(
                                                    text = "Servidor: ${source.serverUrl}  •  Usuario: ${source.username}",
                                                    color = if (isCardFocused) Color(0xFFE2E8F0) else LelouchTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (!isCurrentActive) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isCardFocused) Color.White else LelouchCyanAccent)
                                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "⚡ Activar",
                                                            color = Color.Black,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                var isDelFocused by remember { mutableStateOf(false) }
                                                Box(
                                                    modifier = Modifier
                                                        .clickable {
                                                            onDeleteSource(source.id)
                                                        }
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isDelFocused) LelouchLiveRed else Color(0xFF1E293B))
                                                        .padding(horizontal = 10.dp, vertical = 8.dp)
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

                                    var isUpdateFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .focusable()
                                            .onFocusChanged { isUpdateFocused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isUpdateFocused) LelouchCyanAccent else LelouchSurfaceVariant)
                                            .border(1.dp, if (isUpdateFocused) Color.White else LelouchBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                onCheckUpdates()
                                            }
                                            .padding(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "🚀",
                                                fontSize = 15.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Actualizar App (OTA)",
                                                color = if (isUpdateFocused) Color.Black else LelouchTextPrimary,
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
