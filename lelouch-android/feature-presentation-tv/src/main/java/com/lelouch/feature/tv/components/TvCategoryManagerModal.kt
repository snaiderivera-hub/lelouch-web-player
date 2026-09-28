package com.lelouch.feature.tv.components

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import com.lelouch.core.designsystem.*

/**
 * Modelo de datos de categoría para el gestor de visibilidad.
 */
data class CategoryItemUiModel(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
    val isAdult: Boolean = false
)

/**
 * Ámbitos soportados para filtrado de categorías.
 */
enum class CategoryScope(val title: String, val iconText: String, val color: Color) {
    LIVE("TV en Vivo", "📺", Color(0xFF00E5FF)),
    MOVIES("Películas", "🎬", Color(0xFF38BDF8)),
    SERIES("Series", "🎭", Color(0xFF818CF8))
}

/**
 * Sección de resumen de Gestión de Categorías Visibles para Android TV (Ajustes / Admin).
 * Réplica idéntica y adaptada a TV del panel de configuración de la versión Web.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCategoryVisibilitySection(
    liveTotal: Int,
    liveHiddenCount: Int,
    moviesTotal: Int,
    moviesHiddenCount: Int,
    seriesTotal: Int,
    seriesHiddenCount: Int,
    onOpenManager: (initialScope: CategoryScope) -> Unit,
    modifier: Modifier = Modifier,
    firstItemRequester: FocusRequester? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LelouchSurface)
            .border(1.dp, LelouchBorder, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ENCABEZADO DE LA SECCIÓN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gestión de Categorías Visibles",
                        color = LelouchTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Selecciona qué categorías y listas deseas que aparezcan en pantalla y cuáles ocultar.",
                        color = LelouchTextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Botón: Abrir Selector de Categorías
                var isBtnFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onOpenManager(CategoryScope.LIVE) },
                    modifier = Modifier
                        .then(if (firstItemRequester != null) Modifier.focusRequester(firstItemRequester) else Modifier)
                        .onFocusChanged { isBtnFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(
                        shape = RoundedCornerShape(8.dp),
                        focusedShape = RoundedCornerShape(8.dp)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = LelouchCyanAccent,
                        focusedContainerColor = Color.White,
                        pressedContainerColor = Color.White
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, LelouchCyanAccent)),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Abrir Selector de Categorías",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3 TARJETAS RESUMEN: TV en Vivo, Películas, Series
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta 1: TV en Vivo
                CategorySummaryCard(
                    scope = CategoryScope.LIVE,
                    total = liveTotal,
                    hiddenCount = liveHiddenCount,
                    buttonLabel = "Editar TV",
                    onClick = { onOpenManager(CategoryScope.LIVE) },
                    modifier = Modifier.weight(1f)
                )

                // Tarjeta 2: Películas
                CategorySummaryCard(
                    scope = CategoryScope.MOVIES,
                    total = moviesTotal,
                    hiddenCount = moviesHiddenCount,
                    buttonLabel = "Editar Películas",
                    onClick = { onOpenManager(CategoryScope.MOVIES) },
                    modifier = Modifier.weight(1f)
                )

                // Tarjeta 3: Series
                CategorySummaryCard(
                    scope = CategoryScope.SERIES,
                    total = seriesTotal,
                    hiddenCount = seriesHiddenCount,
                    buttonLabel = "Editar Series",
                    onClick = { onOpenManager(CategoryScope.SERIES) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Tarjeta individual para mostrar el estado y botón de edición de cada sección.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategorySummaryCard(
    scope: CategoryScope,
    total: Int,
    hiddenCount: Int,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = (total - hiddenCount).coerceAtLeast(0)
    var isCardFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isCardFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp),
            focusedShape = RoundedCornerShape(12.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LelouchSurfaceVariant,
            focusedContainerColor = LelouchCardFocused,
            pressedContainerColor = LelouchCardFocused
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, LelouchBorder)),
            focusedBorder = Border(BorderStroke(2.dp, scope.color))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // TÍTULO Y BADGE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${scope.iconText} ${scope.title}",
                        color = scope.color,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Badge de conteo: visibles / total
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, LelouchBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$visibleCount/$total",
                        color = LelouchTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ESTADO: Todas visibles / X ocultas
            Text(
                text = if (hiddenCount > 0) "🚫 $hiddenCount categorías ocultas" else "✓ Todas visibles",
                color = if (hiddenCount > 0) Color(0xFFFF5252) else Color(0xFF34D399),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            // BOTÓN DE ACCIÓN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCardFocused) scope.color else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        1.dp,
                        if (isCardFocused) scope.color else LelouchBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonLabel,
                    color = if (isCardFocused) Color.Black else LelouchTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Diálogo modal para Android TV que permite seleccionar y alternar la visibilidad
 * de categorías con navegación fluida mediante control remoto D-pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCategoryManagerModal(
    initialScope: CategoryScope = CategoryScope.LIVE,
    liveCategories: List<CategoryItemUiModel>,
    hiddenLiveCategoryNames: Set<String>,
    movieCategories: List<CategoryItemUiModel>,
    hiddenMovieCategoryNames: Set<String>,
    seriesCategories: List<CategoryItemUiModel>,
    hiddenSeriesCategoryNames: Set<String>,
    onToggleLiveCategory: (categoryName: String, isVisible: Boolean) -> Unit,
    onToggleMovieCategory: (categoryName: String, isVisible: Boolean) -> Unit,
    onToggleSeriesCategory: (categoryName: String, isVisible: Boolean) -> Unit,
    onShowAllLive: () -> Unit,
    onHideAllLive: () -> Unit,
    onShowAllMovies: () -> Unit,
    onHideAllMovies: () -> Unit,
    onShowAllSeries: () -> Unit,
    onHideAllSeries: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentScope by remember { mutableStateOf(initialScope) }
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        initialFocusRequester.requestFocus()
    }

    val (activeList, activeHiddenSet) = when (currentScope) {
        CategoryScope.LIVE -> Pair(liveCategories, hiddenLiveCategoryNames)
        CategoryScope.MOVIES -> Pair(movieCategories, hiddenMovieCategoryNames)
        CategoryScope.SERIES -> Pair(seriesCategories, hiddenSeriesCategoryNames)
    }

    val total = activeList.size
    val hiddenCount = activeList.count { activeHiddenSet.contains(it.name) || activeHiddenSet.contains(it.id) }
    val visibleCount = (total - hiddenCount).coerceAtLeast(0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.90f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LelouchSurface)
                    .border(1.dp, LelouchBorder, RoundedCornerShape(16.dp))
                    .padding(28.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ENCABEZADO SUPERIOR DEL MODAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = LelouchCyanAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GESTIÓN DE CATEGORÍAS VISIBLES",
                                    color = LelouchTextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Mostrando $visibleCount de $total categorías ${if (hiddenCount > 0) "($hiddenCount ocultas)" else "(todas visibles)"}",
                                    color = if (hiddenCount > 0) Color(0xFFFF5252) else LelouchTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Botón de Cerrar (✕)
                        var isCloseFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .focusable()
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .clip(CircleShape)
                                .background(if (isCloseFocused) LelouchLiveRed else LelouchSurfaceVariant)
                                .onKeyEvent { keyEvent ->
                                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                                    val isCenter = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                            keyCode == KeyEvent.KEYCODE_ENTER ||
                                            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                            keyCode == KeyEvent.KEYCODE_BUTTON_A

                                    if (isCenter && keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                                        onDismiss()
                                        true
                                    } else false
                                }
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

                    // BARRA DE PESTAÑAS (LIVE, MOVIES, SERIES)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CategoryScope.values().forEachIndexed { index, scope ->
                                val isSelected = (currentScope == scope)
                                var isTabFocused by remember { mutableStateOf(false) }

                                val (catList, catHidden) = when (scope) {
                                    CategoryScope.LIVE -> Pair(liveCategories, hiddenLiveCategoryNames)
                                    CategoryScope.MOVIES -> Pair(movieCategories, hiddenMovieCategoryNames)
                                    CategoryScope.SERIES -> Pair(seriesCategories, hiddenSeriesCategoryNames)
                                }
                                val subHidden = catList.count { catHidden.contains(it.name) || catHidden.contains(it.id) }
                                val subVis = (catList.size - subHidden).coerceAtLeast(0)

                                Surface(
                                    onClick = { currentScope = scope },
                                    modifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                        .onFocusChanged { isTabFocused = it.isFocused },
                                    shape = ClickableSurfaceDefaults.shape(
                                        shape = RoundedCornerShape(8.dp),
                                        focusedShape = RoundedCornerShape(8.dp)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isSelected) scope.color.copy(alpha = 0.2f) else LelouchSurfaceVariant,
                                        focusedContainerColor = if (isSelected) scope.color else LelouchCardFocused,
                                        pressedContainerColor = if (isSelected) scope.color else LelouchCardFocused
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) scope.color else LelouchBorder
                                            )
                                        ),
                                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${scope.iconText} ${scope.title}",
                                            color = if (isTabFocused && isSelected) Color.Black else if (isSelected) scope.color else LelouchTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isTabFocused && isSelected) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$subVis/${catList.size}",
                                                color = if (isTabFocused && isSelected) Color.Black else LelouchTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ACCIONES RÁPIDAS: Mostrar Todas / Ocultar Todas
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Mostrar Todas
                            Surface(
                                onClick = {
                                    when (currentScope) {
                                        CategoryScope.LIVE -> onShowAllLive()
                                        CategoryScope.MOVIES -> onShowAllMovies()
                                        CategoryScope.SERIES -> onShowAllSeries()
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(
                                    shape = RoundedCornerShape(6.dp),
                                    focusedShape = RoundedCornerShape(6.dp)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = LelouchSurfaceVariant,
                                    focusedContainerColor = Color(0xFF10B981),
                                    pressedContainerColor = Color(0xFF10B981)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(BorderStroke(1.dp, LelouchBorder)),
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Mostrar Todas",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Ocultar Todas
                            Surface(
                                onClick = {
                                    when (currentScope) {
                                        CategoryScope.LIVE -> onHideAllLive()
                                        CategoryScope.MOVIES -> onHideAllMovies()
                                        CategoryScope.SERIES -> onHideAllSeries()
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(
                                    shape = RoundedCornerShape(6.dp),
                                    focusedShape = RoundedCornerShape(6.dp)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = LelouchSurfaceVariant,
                                    focusedContainerColor = LelouchLiveRed,
                                    pressedContainerColor = LelouchLiveRed
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(BorderStroke(1.dp, LelouchBorder)),
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ocultar Todas",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // LISTA DE CATEGORÍAS (TvLazyColumn)
                    if (activeList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay categorías disponibles en esta sección.",
                                color = LelouchTextSecondary,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        TvLazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = activeList,
                                key = { _, cat -> "cat_${currentScope.name}_${cat.id}_${cat.name}" }
                            ) { _, cat ->
                                val isHidden = activeHiddenSet.contains(cat.name) || activeHiddenSet.contains(cat.id)
                                val isVisible = !isHidden
                                var isRowFocused by remember { mutableStateOf(false) }

                                Surface(
                                    onClick = {
                                        when (currentScope) {
                                            CategoryScope.LIVE -> onToggleLiveCategory(cat.name, !isVisible)
                                            CategoryScope.MOVIES -> onToggleMovieCategory(cat.name, !isVisible)
                                            CategoryScope.SERIES -> onToggleSeriesCategory(cat.name, !isVisible)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isRowFocused = it.isFocused },
                                    shape = ClickableSurfaceDefaults.shape(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedShape = RoundedCornerShape(10.dp)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isVisible) LelouchSurfaceVariant else LelouchSurface,
                                        focusedContainerColor = LelouchCardFocused,
                                        pressedContainerColor = LelouchCardFocused
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border(
                                            BorderStroke(
                                                1.dp,
                                                if (isVisible) LelouchBorder else LelouchBorder.copy(alpha = 0.5f)
                                            )
                                        ),
                                        focusedBorder = Border(
                                            BorderStroke(
                                                2.dp,
                                                if (isVisible) LelouchCyanAccent else Color(0xFFFF5252)
                                            )
                                        )
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // CHECKBOX + NOMBRE + BADGE ADULTO
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Checkbox visual estilizado
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (isVisible) LelouchCyanAccent else Color.Transparent
                                                    )
                                                    .border(
                                                        width = if (isVisible) 0.dp else 1.5.dp,
                                                        color = if (isVisible) Color.Transparent else LelouchTextMuted,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isVisible) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            // Nombre de la categoría
                                            Text(
                                                text = cat.name,
                                                color = if (isRowFocused) Color.White else if (isVisible) LelouchTextPrimary else LelouchTextMuted,
                                                fontSize = 15.sp,
                                                fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (cat.isAdult) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(LelouchLiveRed.copy(alpha = 0.25f))
                                                        .border(1.dp, LelouchLiveRed, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "🔞 +18",
                                                        color = LelouchLiveRed,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        }

                                        // BADGE DE CONTEO (derecha)
                                        val unitLabel = when (currentScope) {
                                            CategoryScope.LIVE -> "canales"
                                            CategoryScope.MOVIES -> "películas"
                                            CategoryScope.SERIES -> "series"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isVisible) LelouchSurface else Color.Black.copy(alpha = 0.3f)
                                                )
                                                .border(1.dp, LelouchBorder, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${cat.itemCount} $unitLabel",
                                                color = if (isVisible) LelouchTextSecondary else LelouchTextMuted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // PIE DE PÁGINA CON INSTRUCCIÓN Y BOTÓN LISTO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 Presiona [OK / Centro] en el control para activar o desactivar. Los cambios se guardan automáticamente.",
                            color = LelouchTextSecondary,
                            fontSize = 12.sp
                        )

                        Surface(
                            onClick = onDismiss,
                            shape = ClickableSurfaceDefaults.shape(
                                shape = RoundedCornerShape(8.dp),
                                focusedShape = RoundedCornerShape(8.dp)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = LelouchCyanAccent,
                                focusedContainerColor = Color.White,
                                pressedContainerColor = Color.White
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, LelouchCyanAccent)),
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            )
                        ) {
                            Text(
                                text = "Listo / Aplicar",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
