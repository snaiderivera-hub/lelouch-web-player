package com.lelouch.feature.tv.components

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.*

data class NavItem(
    val index: Int,
    val title: String,
    val icon: ImageVector
)

val TV_NAV_ITEMS = listOf(
    NavItem(0, "Buscar", Icons.Default.Search),
    NavItem(1, "Inicio", Icons.Default.Home),
    NavItem(2, "En Vivo", Icons.Default.LiveTv),
    NavItem(3, "Películas", Icons.Default.Movie),
    NavItem(4, "Series", Icons.Default.Tv),
    NavItem(5, "Favoritos", Icons.Default.Favorite),
    NavItem(6, "Ajustes", Icons.Default.Settings)
)

/**
 * Modern Android TV vertical left sidebar navigation.
 * Eliminates top-bar navigation issues, providing smooth D-Pad navigation,
 * clear focus indication, and seamless transition to main content.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvNavigationSidebar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    sidebarRequesters: List<FocusRequester>,
    onNavigateContent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(LelouchSurface.copy(alpha = 0.95f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        LelouchBorder.copy(alpha = 0.7f),
                        LelouchBorder.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Logo Icon Top (Estilo LELOUCH)
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(LelouchCyanAccent, Color(0xFF0055FF))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "L",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista Vertical de Iconos Circulares de Navegación
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (item in TV_NAV_ITEMS) {
                TvSidebarIconItem(
                    item = item,
                    isSelected = (selectedTab == item.index),
                    requester = sidebarRequesters.getOrNull(item.index) ?: remember { FocusRequester() },
                    onSelect = { onSelectTab(item.index) },
                    onNavigateRight = onNavigateContent
                )
            }
        }

        // Indicador de conexión / En línea en la base
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSidebarIconItem(
    item: NavItem,
    isSelected: Boolean,
    requester: FocusRequester,
    onSelect: () -> Unit,
    onNavigateRight: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1.0f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "navScale"
    )

    // Círculo ultra-visible: Cyan brillante si tiene foco, Cyan transparente si está seleccionado
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> LelouchCyanAccent
            isSelected -> LelouchCyanAccent.copy(alpha = 0.25f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "navBg"
    )

    // Icono de alto contraste: Negro puro cuando tiene foco para máxima legibilidad sobre Cyan
    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isSelected -> LelouchCyanAccent
            else -> LelouchTextSecondary
        },
        animationSpec = tween(150),
        label = "navIcon"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(52.dp)
            .scale(scale)
            .focusRequester(requester)
            .focusProperties {
                // Cancelar escape hacia la izquierda, arriba o abajo fuera de la pantalla
                left = FocusRequester.Cancel
                if (item.index == 0) up = FocusRequester.Cancel
                if (item.index == 6) down = FocusRequester.Cancel
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .onKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                val isCenterKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                  keyCode == KeyEvent.KEYCODE_ENTER ||
                                  keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                  keyCode == KeyEvent.KEYCODE_BUTTON_A

                if (isCenterKey) {
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                        onSelect()
                        onNavigateRight()
                        true
                    } else if (keyEvent.type == KeyEventType.KeyUp) {
                        true
                    } else {
                        false
                    }
                } else if (keyEvent.type == KeyEventType.KeyDown && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    onSelect()
                    onNavigateRight()
                    true
                } else {
                    false
                }
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isFocused) 3.dp else if (isSelected) 2.dp else 0.dp,
                color = if (isFocused) Color.White else if (isSelected) LelouchCyanAccent else Color.Transparent,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onSelect()
                onNavigateRight()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        // Barra indicadora lateral izquierda cuando está seleccionado y no tiene foco
        if (isSelected && !isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .size(width = 3.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LelouchCyanAccent)
            )
        }
    }
}
