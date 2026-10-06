package com.lelouch.feature.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig
import com.lelouch.feature.tv.R

/**
 * FASE 32: Portal Dashboard Canónico para Android TV (TV Box)
 * Replica con máxima fidelidad la pantalla de inicio del reproductor web:
 * - Cabecera con marca "REPRODUCTOR LELOUCH", subtítulo y píldora de cuenta activa.
 * - 4 Tarjetas Gigantes Hero: TV EN VIVO, PELÍCULAS, SERIES, DEPORTES con badges numéricos.
 * - Fila inferior de acciones rápidas: Descargar M3U, Recargar Catálogo, Ajustes y Listas, Diagnóstico.
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvPortalDashboard(
    activeSource: SourceConfig?,
    liveChannelsCount: Int,
    moviesCount: Int,
    seriesCount: Int,
    sportsCount: Int,
    onNavigateToLive: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToSports: () -> Unit,
    onDownloadM3U: () -> Unit,
    onReloadCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    modifier: Modifier = Modifier,
    sidebarRequester: FocusRequester? = null,
    firstItemRequester: FocusRequester? = null
) {
    val cardFocusRequesters = remember(firstItemRequester) {
        List(4) { idx -> if (idx == 0 && firstItemRequester != null) firstItemRequester else FocusRequester() }
    }
    val actionFocusRequesters = remember { List(4) { FocusRequester() } }
    val reloadBtnRequester = remember { FocusRequester() }
    val searchBtnRequester = remember { FocusRequester() }
    val favBtnRequester = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_category_card),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
        )

        var currentTime by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("America/Guatemala")
            while (true) {
                currentTime = sdf.format(java.util.Date())
                kotlinx.coroutines.delay(1000L)
            }
        }

        Text(
            text = currentTime,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 48.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 36.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // ══════════════════════════════════════════════════════════════════════
        // 1. CABECERA CENTRAL DE MARCA Y ESTADO
        // ══════════════════════════════════════════════════════════════════════
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "REPRODUCTOR LELOUCH",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "M3U • XTREAM CODES • HLS",
                color = LelouchTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Fila de estado: Servidor Activo + Botón Recargar + Fecha de Vencimiento
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pill Servidor Activo
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF061826))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🌐", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeSource?.name ?: activeSource?.serverUrl?.removePrefix("http://")?.take(22) ?: "liontv.es (Activa)",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Botón RECARGAR (Focusable con D-Pad)
                var isReloadFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onReloadCatalog,
                    modifier = Modifier
                        .focusRequester(reloadBtnRequester)
                        .focusProperties {
                            right = FocusRequester.Cancel
                            down = cardFocusRequesters[0]
                        }
                        .onFocusChanged { isReloadFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isReloadFocused) Color(0xFF00E5FF) else Color(0xFF061826),
                        focusedContainerColor = Color(0xFF00E5FF)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))),
                        focusedBorder = Border(BorderStroke(1.5.dp, Color.White))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔄", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "RECARGAR",
                            color = if (isReloadFocused) Color(0xFF02070D) else Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                /* BUSCAR/FAVORITOS retirados del Home TV (vista minimalista).
                var isSearchFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onNavigateToSearch,
                    modifier = Modifier
                        .focusRequester(searchBtnRequester)
                        .focusProperties {
                            left = reloadBtnRequester
                            right = favBtnRequester
                            down = cardFocusRequesters[1]
                        }
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSearchFocused) Color(0xFF00E5FF) else Color(0xFF061826),
                        focusedContainerColor = Color(0xFF00E5FF)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))),
                        focusedBorder = Border(BorderStroke(1.5.dp, Color.White))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔍", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "BUSCAR",
                            color = if (isSearchFocused) Color(0xFF02070D) else Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Botón FAVORITOS (Focusable con D-Pad)
                var isFavFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onNavigateToFavorites,
                    modifier = Modifier
                        .focusRequester(favBtnRequester)
                        .focusProperties {
                            left = searchBtnRequester
                            down = cardFocusRequesters[2]
                        }
                        .onFocusChanged { isFavFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isFavFocused) Color(0xFF00E5FF) else Color(0xFF061826),
                        focusedContainerColor = Color(0xFF00E5FF)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))),
                        focusedBorder = Border(BorderStroke(1.5.dp, Color.White))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⭐", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "FAVORITOS",
                            color = if (isFavFocused) Color(0xFF02070D) else Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                */

                // Indicador de Vencimiento
                val expiryText = activeSource?.expireDate ?: "13/10/2026"
                Text(
                    text = "Vence: $expiryText",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Auto-foco en la primera tarjeta (TV EN VIVO) al iniciar
        LaunchedEffect(Unit) {
            try {
                cardFocusRequesters[0].requestFocus()
            } catch (_: Exception) {}
        }

        // ══════════════════════════════════════════════════════════════════════
        // 2. LAS 4 TARJETAS GIGANTES HERO (PORTAL DASHBOARD)
        // ══════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. TV EN VIVO
            PortalHeroCard(
                title = "TV EN VIVO",
                iconContent = { TvCardVectorIcon(size = 72.dp) },
                count = liveChannelsCount,
                accentColor = Color(0xFF00E5FF),
                onClick = onNavigateToLive,
                focusRequester = cardFocusRequesters[0],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = sidebarRequester ?: FocusRequester.Cancel
                        right = cardFocusRequesters[1]
                        up = reloadBtnRequester
                        down = actionFocusRequesters[0]
                    }
            )

            // 2. PELÍCULAS
            PortalHeroCard(
                title = "PELÍCULAS",
                iconContent = { MovieCardVectorIcon(size = 72.dp) },
                count = moviesCount,
                accentColor = Color(0xFF38BDF8),
                onClick = onNavigateToMovies,
                focusRequester = cardFocusRequesters[1],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = cardFocusRequesters[0]
                        right = cardFocusRequesters[2]
                        up = reloadBtnRequester
                        down = actionFocusRequesters[1]
                    }
            )

            // 3. SERIES
            PortalHeroCard(
                title = "SERIES",
                iconContent = { SeriesCardVectorIcon(size = 72.dp) },
                count = seriesCount,
                accentColor = Color(0xFFA855F7),
                onClick = onNavigateToSeries,
                focusRequester = cardFocusRequesters[2],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = cardFocusRequesters[1]
                        right = cardFocusRequesters[3]
                        up = reloadBtnRequester
                        down = actionFocusRequesters[2]
                    }
            )

            // 4. DEPORTES
            PortalHeroCard(
                title = "DEPORTES",
                iconContent = { SportsCardVectorIcon(size = 72.dp) },
                count = sportsCount,
                accentColor = Color(0xFF10B981),
                onClick = onNavigateToSports,
                focusRequester = cardFocusRequesters[3],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = cardFocusRequesters[2]
                        right = cardFocusRequesters[3]
                        up = reloadBtnRequester
                        down = actionFocusRequesters[3]
                    }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 3. FILA INFERIOR DE ACCIONES RÁPIDAS
        // ══════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Acción 1: Descargar M3U
            PortalActionPill(
                icon = "📄",
                label = "Descargar M3U",
                onClick = onDownloadM3U,
                focusRequester = actionFocusRequesters[0],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = sidebarRequester ?: FocusRequester.Cancel
                        right = actionFocusRequesters[1]
                        up = cardFocusRequesters[0]
                    }
            )

            // Acción 2: Recargar Catálogo
            PortalActionPill(
                icon = "🔄",
                label = "Recargar Catálogo",
                onClick = onReloadCatalog,
                focusRequester = actionFocusRequesters[1],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = actionFocusRequesters[0]
                        right = actionFocusRequesters[2]
                        up = cardFocusRequesters[1]
                    }
            )

            // Acción 3: Ajustes y Listas
            PortalActionPill(
                icon = "⚙️",
                label = "Ajustes y Listas",
                onClick = onOpenSettings,
                focusRequester = actionFocusRequesters[2],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = actionFocusRequesters[1]
                        right = actionFocusRequesters[3]
                        up = cardFocusRequesters[2]
                    }
            )

            // Acción 4: Diagnóstico
            PortalActionPill(
                icon = "🩺",
                label = "Diagnóstico",
                onClick = onOpenDiagnostics,
                focusRequester = actionFocusRequesters[3],
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        left = actionFocusRequesters[2]
                        right = actionFocusRequesters[3]
                        up = cardFocusRequesters[3]
                    }
            )
        }
    }
}
}

/**
 * Tarjeta Hero grande del Portal Dashboard con resplandor neón cian al enfocarse con D-Pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PortalHeroCard(
    title: String,
    iconContent: @Composable () -> Unit,
    count: Int,
    accentColor: Color,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(185.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF040D18),
            focusedContainerColor = Color(0xFF0A1B2E)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))),
            focusedBorder = Border(BorderStroke(3.dp, Color(0xFF00E5FF)))
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isFocused) {
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.30f), Color(0xFF040D18)),
                            radius = 450f
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF071424), Color(0xFF02070D))
                        )
                    }
                )
                .padding(14.dp)
        ) {
            // Badge contador superior derecho con resplandor
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF00E5FF).copy(alpha = if (isFocused) 0.25f else 0.12f))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = if (isFocused) 0.7f else 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (count > 999) "%,d".format(count) else "$count",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Contenido central: Ilustración Vectorial + Título
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                iconContent()

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = title,
                    color = if (isFocused) Color(0xFF00E5FF) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Píldora de acción rápida en la barra inferior (Descargar M3U, Recargar, Ajustes, Diagnóstico).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PortalActionPill(
    icon: String,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF040D18),
            focusedContainerColor = Color(0xFF071F36)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF1E3A5F).copy(alpha = 0.6f))),
            focusedBorder = Border(BorderStroke(1.5.dp, Color(0xFF00E5FF)))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isFocused) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
