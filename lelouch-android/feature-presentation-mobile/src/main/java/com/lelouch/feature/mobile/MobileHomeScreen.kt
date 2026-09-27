package com.lelouch.feature.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.*

import com.lelouch.core.model.LiveStream
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.network.XtreamUrlBuilder
import com.lelouch.core.player.LelouchVideoPlayer
import com.lelouch.core.player.rememberLelouchPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage


import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun MobileHomeScreen(
    activeSource: SourceConfig? = null,
    allSources: List<SourceConfig> = emptyList(),
    liveChannels: List<LiveStream> = emptyList(),
    onNavigateToLive: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSeries: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onActivateSource: (String) -> Unit = {},
    onDeleteSource: (String) -> Unit = {},
    onAddSource: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onSyncCloudSources: () -> Unit = {},
    onForceSync: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var activeChannelName by remember { mutableStateOf("") }
    val playerEngine = rememberLelouchPlayer()

    // Formulario para nueva cuenta en móvil
    var newServerUrl by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(activeStreamUrl) {
        activeStreamUrl?.let { url ->
            playerEngine.playStream(url, isLive = true)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = LelouchSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    label = { Text("Inicio") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LelouchCyanAccent,
                        selectedTextColor = LelouchCyanAccent,
                        indicatorColor = LelouchSurfaceVariant,
                        unselectedIconColor = LelouchTextSecondary,
                        unselectedTextColor = LelouchTextSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { 
                        selectedTab = 1
                        onNavigateToLive()
                    },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = "En Vivo") },
                    label = { Text("En Vivo") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LelouchCyanAccent,
                        selectedTextColor = LelouchCyanAccent,
                        indicatorColor = LelouchSurfaceVariant,
                        unselectedIconColor = LelouchTextSecondary,
                        unselectedTextColor = LelouchTextSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { 
                        selectedTab = 2
                        onNavigateToMovies()
                    },
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Películas") },
                    label = { Text("Películas") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LelouchCyanAccent,
                        selectedTextColor = LelouchCyanAccent,
                        indicatorColor = LelouchSurfaceVariant,
                        unselectedIconColor = LelouchTextSecondary,
                        unselectedTextColor = LelouchTextSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { 
                        selectedTab = 3
                        onNavigateToSeries()
                    },
                    icon = { Icon(Icons.Default.Tv, contentDescription = "Series") },
                    label = { Text("Series") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LelouchCyanAccent,
                        selectedTextColor = LelouchCyanAccent,
                        indicatorColor = LelouchSurfaceVariant,
                        unselectedIconColor = LelouchTextSecondary,
                        unselectedTextColor = LelouchTextSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Listas") },
                    label = { Text("Listas") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LelouchCyanAccent,
                        selectedTextColor = LelouchCyanAccent,
                        indicatorColor = LelouchSurfaceVariant,
                        unselectedIconColor = LelouchTextSecondary,
                        unselectedTextColor = LelouchTextSecondary
                    )
                )
            }
        },
        containerColor = LelouchBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header con Logo, Chip de Lista Activa y Acciones
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LELOUCH",
                            style = LelouchTypography.titleLarge.copy(
                                color = LelouchTextPrimary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(LelouchSurfaceVariant)
                                .clickable { selectedTab = 4 }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeSource?.name ?: "IPTV",
                                    color = LelouchCyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = LelouchCyanAccent
                            )
                        }
                        IconButton(onClick = { selectedTab = 4 }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Listas / Admin",
                                tint = if (selectedTab == 4) LelouchCyanAccent else LelouchTextSecondary
                            )
                        }
                    }
                }
            }

            if (selectedTab == 4) {
                // PANEL DE ADMINISTRACIÓN Y GESTOR DE LISTAS PARA MÓVIL
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "⚙️ Gestor de Listas IPTV",
                            style = LelouchTypography.titleLarge,
                            color = LelouchTextPrimary,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Cambia entre tus cuentas registradas o conecta un nuevo proveedor.",
                            style = LelouchTypography.bodySmall,
                            color = LelouchTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botones de Mantenimiento
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onSyncCloudSources,
                                colors = ButtonDefaults.buttonColors(containerColor = LelouchSurfaceVariant),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = LelouchCyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nube", fontSize = 12.sp, color = Color.White)
                            }
                            Button(
                                onClick = onForceSync,
                                colors = ButtonDefaults.buttonColors(containerColor = LelouchSurfaceVariant),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = LelouchCyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-sync", fontSize = 12.sp, color = Color.White)
                            }
                            Button(
                                onClick = onLogout,
                                colors = ButtonDefaults.buttonColors(containerColor = LelouchLiveRed.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, tint = LelouchLiveRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salir", fontSize = 12.sp, color = LelouchLiveRed)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Tus Listas Registradas (${allSources.size}):",
                            style = LelouchTypography.titleSmall,
                            color = LelouchCyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                itemsIndexed(allSources) { _, source ->
                    val isActive = source.id == activeSource?.id || source.isActive
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) LelouchSurfaceVariant else LelouchSurface)
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) Color(0xFF10B981) else LelouchBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (!isActive) {
                                    onActivateSource(source.id)
                                    selectedTab = 0
                                }
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
                                        style = LelouchTypography.titleSmall,
                                        color = LelouchTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF10B981))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ACTIVA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${source.serverUrl}  •  ${source.username}",
                                    style = LelouchTypography.bodySmall,
                                    color = LelouchTextSecondary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isActive) {
                                    Button(
                                        onClick = {
                                            onActivateSource(source.id)
                                            selectedTab = 0
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Activar", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (allSources.size > 1) {
                                    IconButton(onClick = { onDeleteSource(source.id) }) {
                                        Text("🗑️", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Formulario para conectar nueva cuenta
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "➕ Conectar Otra Cuenta Xtream",
                            style = LelouchTypography.titleSmall,
                            color = LelouchTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = newServerUrl,
                            onValueChange = { newServerUrl = it },
                            label = { Text("URL Servidor") },
                            placeholder = { Text("http://servidor:puerto") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newUsername,
                            onValueChange = { newUsername = it },
                            label = { Text("Usuario") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Nombre Personalizado (Opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (newServerUrl.isNotBlank() && newUsername.isNotBlank() && newPassword.isNotBlank()) {
                                    onAddSource(newServerUrl.trim(), newUsername.trim(), newPassword.trim(), newName.trim().ifEmpty { newUsername.trim() })
                                    newServerUrl = ""
                                    newUsername = ""
                                    newPassword = ""
                                    newName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Guardar y Conectar Lista", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {

            // Hero Banner Compacto Táctil
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(LelouchCardCornerRadius))
                        .background(
                            Brush.verticalGradient(
                                listOf(LelouchSurfaceVariant, LelouchSurface)
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LelouchLiveRed)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("DESTACADO", style = LelouchTypography.labelMedium.copy(color = Color.White))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bienvenido a LELOUCH",
                            style = LelouchTypography.titleLarge
                        )
                        Text(
                            text = "Tu centro multimedia personal nativo",
                            style = LelouchTypography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Riel: Continuar Viendo
            item {
                Text(
                    text = "Continuar Viendo",
                    style = LelouchTypography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(5) { index ->
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(95.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LelouchSurface)
                                .padding(8.dp)
                        ) {
                            Text(
                                "Canal / Película #${index + 1}",
                                style = LelouchTypography.bodySmall,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Reproductor de Video Flotante / Encabezado Activo si se selecciona un canal
            if (activeStreamUrl != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black)
                        ) {
                            LelouchVideoPlayer(
                                playerEngine = playerEngine,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Botón Cerrar Reproductor
                            IconButton(
                                onClick = {
                                    playerEngine.stop()
                                    activeStreamUrl = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color.White
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeChannelName,
                                color = LelouchTextPrimary,
                                style = LelouchTypography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LelouchLiveRed)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE", color = Color.White, style = LelouchTypography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Riel: Canales en Vivo
            item {
                Text(
                    text = "Canales en Vivo Populares",
                    style = LelouchTypography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (liveChannels.isNotEmpty()) {
                        items(liveChannels.size) { index ->
                            val channel = liveChannels[index]
                            val streamUrl = activeSource?.let {
                                XtreamUrlBuilder.buildLiveStreamUrl(
                                    it.serverUrl,
                                    it.username,
                                    it.password,
                                    channel.streamId,
                                    "m3u8"
                                )
                            } ?: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LelouchSurfaceVariant)
                                    .clickable {
                                        activeChannelName = channel.name
                                        activeStreamUrl = streamUrl
                                    }
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 30.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!channel.streamIcon.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.streamIcon,
                                                contentDescription = channel.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text(
                                                text = channel.name.take(3).uppercase(),
                                                color = LelouchCyanAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(LelouchLiveRed)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                    Text(
                                        text = channel.name,
                                        style = LelouchTypography.labelLarge,
                                        color = LelouchTextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Tocar para ver",
                                        style = LelouchTypography.bodySmall,
                                        color = LelouchCyanAccent
                                    )
                                }
                            }
                        }
                    } else {
                        val sampleChannels = listOf(
                            Triple("ESPN HD", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/512px-ESPN_wordmark.svg.png", "101"),
                            Triple("Fox Sports", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Fox_Sports_logo.svg/512px-Fox_Sports_logo.svg.png", "102"),
                            Triple("TyC Sports", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/TyC_Sports_Logo_2019.svg/512px-TyC_Sports_Logo_2019.svg.png", "103"),
                            Triple("HBO Max", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/512px-HBO_logo.svg.png", "104"),
                            Triple("Star Channel", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Star_Channel_2021.svg/512px-Star_Channel_2021.svg.png", "105")
                        )
                        items(sampleChannels.size) { index ->
                            val (chName, chLogo, _) = sampleChannels[index]
                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LelouchSurfaceVariant)
                                    .clickable {
                                        activeChannelName = chName
                                        activeStreamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                                    }
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 30.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = chLogo,
                                            contentDescription = chName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(LelouchLiveRed)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                    Text(
                                        text = chName,
                                        style = LelouchTypography.labelLarge,
                                        color = LelouchTextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Tocar para ver",
                                        style = LelouchTypography.bodySmall,
                                        color = LelouchCyanAccent
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

