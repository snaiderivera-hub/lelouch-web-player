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

@Composable
fun MobileHomeScreen(
    onNavigateToLive: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSeries: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

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
            }
        },
        containerColor = LelouchBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header con Logo y Buscador
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LELOUCH",
                        style = LelouchTypography.titleLarge.copy(
                            color = LelouchTextPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = LelouchCyanAccent
                        )
                    }
                }
            }

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
                    items(6) { index ->
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LelouchSurfaceVariant)
                                .padding(8.dp)
                        ) {
                            Text(
                                "Señal #${index + 101}",
                                style = LelouchTypography.labelLarge,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}
