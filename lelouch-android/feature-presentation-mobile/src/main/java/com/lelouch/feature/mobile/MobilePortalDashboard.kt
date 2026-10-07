package com.lelouch.feature.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig

/**
 * FASE 32: Portal Dashboard Canónico para Teléfonos Móviles
 * Adapta el diseño de 4 tarjetas del reproductor web a una experiencia táctil 2x2 de respuesta inmediata.
 */
@Composable
fun MobilePortalDashboard(
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
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = com.lelouch.feature.mobile.R.drawable.bg_category_card),
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
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("America/Guatemala")
            while (true) {
                currentTime = sdf.format(Date())
                delay(1000L)
            }
        }

        Text(
            text = currentTime,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // ══════════════════════════════════════════════════════════════════════
        // 1. CABECERA CENTRAL DE MARCA Y ESTADO
        // ══════════════════════════════════════════════════════════════════════
        Text(
            text = "REPRODUCTOR LELOUCH",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "M3U • XTREAM CODES • HLS",
            color = LelouchTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Fila de estado táctil: Servidor + Recargar + Vencimiento
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pill Servidor Activo
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF061826))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🌐", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeSource?.name ?: activeSource?.serverUrl?.removePrefix("http://")?.take(18) ?: "liontv.es",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón RECARGAR Táctil
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00E5FF))
                    .clickable { onReloadCatalog() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔄", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RECARGAR",
                        color = Color(0xFF02070D),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fecha de Expiración
            val expiryText = activeSource?.formattedExpireDate ?: "13/10/2026"
            Text(
                text = "Vence: $expiryText",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 2. LAS 4 TARJETAS EN FILA HORIZONTAL
        // ══════════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MobileHeroCard(
                    title = "TV EN VIVO",
                    iconContent = { TvCardVectorIcon(size = 48.dp) },
                    count = liveChannelsCount,
                    onClick = onNavigateToLive,
                    modifier = Modifier.weight(1f).height(115.dp)
                )
                MobileHeroCard(
                    title = "PELÍCULAS",
                    iconContent = { MovieCardVectorIcon(size = 48.dp) },
                    count = moviesCount,
                    onClick = onNavigateToMovies,
                    modifier = Modifier.weight(1f).height(115.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MobileHeroCard(
                    title = "SERIES",
                    iconContent = { SeriesCardVectorIcon(size = 48.dp) },
                    count = seriesCount,
                    onClick = onNavigateToSeries,
                    modifier = Modifier.weight(1f).height(115.dp)
                )
                MobileHeroCard(
                    title = "DEPORTES",
                    iconContent = { SportsCardVectorIcon(size = 48.dp) },
                    count = sportsCount,
                    onClick = onNavigateToSports,
                    modifier = Modifier.weight(1f).height(115.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 3. FILA INFERIOR DE ACCIONES RÁPIDAS
        // ══════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MobileActionPill(
                icon = "📄",
                label = "Descargar M3U",
                onClick = onDownloadM3U,
                modifier = Modifier.weight(1f)
            )

            MobileActionPill(
                icon = "🔄",
                label = "Recargar",
                onClick = onReloadCatalog,
                modifier = Modifier.weight(1f)
            )

            MobileActionPill(
                icon = "🩺",
                label = "Ajustes",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
}

@Composable
private fun MobileHeroCard(
    title: String,
    iconContent: @Composable () -> Unit,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(125.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF071424), Color(0xFF02070D))
                )
            )
            .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        // Badge contador
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (count > 999) "%,d".format(count) else "$count",
                color = Color(0xFF00E5FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Centro: Ilustración Vectorial + Título
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            iconContent()
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MobileActionPill(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF040D18))
            .border(1.dp, Color(0xFF1E3A5F).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
