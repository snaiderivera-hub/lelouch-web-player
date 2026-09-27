package com.lelouch.feature.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.*
import com.lelouch.core.designsystem.*

data class TvHeroContent(
    val title: String,
    val subtitle: String,
    val description: String,
    val badge: String = "4K UHD",
    val rating: String = "★ 9.2",
    val year: String = "2024",
    val genre: String = "Acción / Fantasía"
)

@Composable
fun TvHomeScreen(
    onNavigateToPlayer: (contentId: String) -> Unit = {}
) {
    var activeHeroContent by remember {
        mutableStateOf(
            TvHeroContent(
                title = "DEMON SLAYER",
                subtitle = "KIMETSU NO YAIBA",
                description = "Tanjiro y sus aliados emprenden una nueva batalla decisiva contra las lunas superiores en el castillo infinito...",
                badge = "🔴 LIVE / 4K UHD",
                genre = "Anime / Acción"
            )
        )
    }

    var selectedTopTab by remember { mutableIntStateOf(0) }
    val topTabs = listOf("Inicio", "En Vivo", "Películas", "Series", "Animes", "Favoritos")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground)
    ) {
        // Spotlight Hero Cinemático (Fondo con degradado hacia el fondo oscuro)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            LelouchSurfaceVariant.copy(alpha = 0.6f),
                            LelouchSurface.copy(alpha = 0.85f),
                            LelouchBackground
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 58.dp, bottom = 28.dp, end = 200.dp)
            ) {
                Crossfade(targetState = activeHeroContent, label = "HeroCrossfade") { hero ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LelouchLiveRed)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = hero.badge,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${hero.rating}  •  ${hero.year}  •  ${hero.genre}",
                                color = LelouchTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = hero.title,
                            color = LelouchTextPrimary,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        if (hero.subtitle.isNotEmpty()) {
                            Text(
                                text = hero.subtitle,
                                color = LelouchCyanAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = hero.description,
                            color = LelouchTextSecondary,
                            fontSize = 14.sp,
                            maxLines = 2,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // Estructura vertical desplazable con TV Lazy Column
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)
        ) {
            // Barra de Navegación Superior Fina (Top Navigation Bar)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 58.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo LELOUCH
                    Text(
                        text = "LELOUCH",
                        color = LelouchCyanAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.width(48.dp))

                    // Pestañas Horizontales
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        topTabs.forEachIndexed { index, title ->
                            var isTabFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isTabFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isTabFocused -> LelouchCyanAccent.copy(alpha = 0.2f)
                                            selectedTopTab == index -> LelouchSurfaceVariant
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (isTabFocused) 2.dp else 0.dp,
                                        color = if (isTabFocused) LelouchCyanAccent else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = if (isTabFocused || selectedTopTab == index) LelouchTextPrimary else LelouchTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTopTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "🔍 Buscar",
                        color = LelouchTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 24.dp)
                    )
                    Text(
                        text = "👤 Ajustes",
                        color = LelouchTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            // Espacio para contemplar el Spotlight Hero
            item {
                Spacer(modifier = Modifier.height(240.dp))
            }

            // Riel 1: "Em Destaque" / Continuar Viendo (Tarjetas con Foco Reactivo)
            item {
                Text(
                    text = "Em Destaque",
                    color = LelouchTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 58.dp, bottom = 14.dp)
                )
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    val featuredTitles = listOf(
                        "DUNA: PARTE DOS" to "Paul Atreides se une a Chani y a los Fremen mientras busca venganza...",
                        "THE LAST OF US" to "Joel y Ellie deben sobrevivir a un viaje épico a través de un Estados Unidos devastado...",
                        "VINGADORES: ULTIMATO" to "Los héroes supervivientes deben revertir las acciones devastadoras de Thanos...",
                        "GODZILLA Y KONG" to "Un colosal combate épico mientras la humanidad descubre sus misteriosos orígenes...",
                        "STRANGER THINGS" to "En el pueblo de Hawkins suceden eventos sobrenaturales e inexplicables..."
                    )

                    items(featuredTitles.size) { index ->
                        val (title, synopsis) = featuredTitles[index]
                        var isCardFocused by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(targetValue = if (isCardFocused) 1.08f else 1.0f, label = "cardScale")

                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(120.dp)
                                .scale(scale)
                                .onFocusChanged {
                                    isCardFocused = it.isFocused
                                    if (it.isFocused) {
                                        activeHeroContent = TvHeroContent(
                                            title = title,
                                            subtitle = "EN TENDENCIA",
                                            description = synopsis,
                                            badge = "4K HDR",
                                            genre = "Cine Estelar"
                                        )
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCardFocused) LelouchCardFocused else LelouchSurface)
                                .border(
                                    width = if (isCardFocused) 2.5.dp else 1.dp,
                                    color = if (isCardFocused) LelouchCyanAccent else LelouchBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text(
                                    text = title,
                                    color = LelouchTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Ver ahora",
                                    color = if (isCardFocused) LelouchCyanAccent else LelouchTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Riel 2: Canales en Vivo con Video en Fondo Reactivo (Live Video Zapping)
            item {
                Text(
                    text = "⚽ Deportes en Directo / Canales Populares",
                    color = LelouchTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 58.dp, bottom = 14.dp)
                )
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    val channels = listOf(
                        "ESPN HD" to "UEFA Champions League en Vivo",
                        "Fox Sports" to "Fórmula 1: Gran Premio",
                        "TyC Sports" to "Fútbol de Primera en Directo",
                        "DirecTV Sports" to "Copa Libertadores en Vivo",
                        "GolTV HD" to "Resumen de Goles y Partidos"
                    )

                    items(channels.size) { index ->
                        val (channelName, liveProgram) = channels[index]
                        var isChannelFocused by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(targetValue = if (isChannelFocused) 1.08f else 1.0f, label = "channelScale")

                        Box(
                            modifier = Modifier
                                .width(185.dp)
                                .height(105.dp)
                                .scale(scale)
                                .onFocusChanged {
                                    isChannelFocused = it.isFocused
                                    if (it.isFocused) {
                                        activeHeroContent = TvHeroContent(
                                            title = channelName,
                                            subtitle = liveProgram,
                                            description = "Transmitiendo en directo en 1080p a 60fps con audio digital envolvente.",
                                            badge = "🔴 EN VIVO",
                                            genre = "Deportes"
                                        )
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isChannelFocused) LelouchCardFocused else LelouchSurfaceVariant)
                                .border(
                                    width = if (isChannelFocused) 2.5.dp else 1.dp,
                                    color = if (isChannelFocused) LelouchCyanAccent else LelouchBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LelouchLiveRed)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text(
                                    text = channelName,
                                    color = LelouchTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = liveProgram,
                                    color = LelouchTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
