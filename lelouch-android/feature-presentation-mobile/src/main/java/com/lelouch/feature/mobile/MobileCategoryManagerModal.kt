package com.lelouch.feature.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.Category

enum class MobileCategoryScope(val title: String, val shortTitle: String, val iconText: String, val color: Color) {
    LIVE("TV en Vivo", "En Vivo", "📺", Color(0xFF00E5FF)),
    MOVIES("Películas", "Películas", "🎬", Color(0xFF38BDF8)),
    SERIES("Series", "Series", "🎞️", Color(0xFF818CF8))
}

@Composable
fun MobileCategoryVisibilitySection(
    liveTotal: Int,
    liveHiddenCount: Int,
    moviesTotal: Int,
    moviesHiddenCount: Int,
    seriesTotal: Int,
    seriesHiddenCount: Int,
    onOpenManager: (initialScope: MobileCategoryScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LelouchSurface)
            .border(1.dp, LelouchBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "Gestión de Categorías Visibles",
                color = LelouchTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Selecciona qué categorías deseas que aparezcan y cuáles ocultar en tus listas.",
                color = LelouchTextSecondary,
                fontSize = 13.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MobileCategorySummaryRow(
                scope = MobileCategoryScope.LIVE,
                total = liveTotal,
                hiddenCount = liveHiddenCount,
                onClick = { onOpenManager(MobileCategoryScope.LIVE) }
            )

            MobileCategorySummaryRow(
                scope = MobileCategoryScope.MOVIES,
                total = moviesTotal,
                hiddenCount = moviesHiddenCount,
                onClick = { onOpenManager(MobileCategoryScope.MOVIES) }
            )

            MobileCategorySummaryRow(
                scope = MobileCategoryScope.SERIES,
                total = seriesTotal,
                hiddenCount = seriesHiddenCount,
                onClick = { onOpenManager(MobileCategoryScope.SERIES) }
            )
        }
    }
}

@Composable
private fun MobileCategorySummaryRow(
    scope: MobileCategoryScope,
    total: Int,
    hiddenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = (total - hiddenCount).coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LelouchSurfaceVariant)
            .border(1.dp, LelouchBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${scope.iconText} ${scope.title}",
                color = scope.color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (hiddenCount > 0) "🚫 $hiddenCount ocultas • $visibleCount visibles" else "✅ Todas visibles ($total)",
                color = if (hiddenCount > 0) Color(0xFFFF6E6E) else Color(0xFF34D399),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(scope.color.copy(alpha = 0.15f))
                .border(1.dp, scope.color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Gestionar",
                color = scope.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MobileCategoryManagerModal(
    initialScope: MobileCategoryScope,
    liveCategories: List<Category>,
    hiddenLiveCategoryNames: Set<String>,
    movieCategories: List<Category>,
    hiddenMovieCategoryNames: Set<String>,
    seriesCategories: List<Category>,
    hiddenSeriesCategoryNames: Set<String>,
    onToggleLiveCategory: (Category) -> Unit,
    onToggleMovieCategory: (Category) -> Unit,
    onToggleSeriesCategory: (Category) -> Unit,
    onShowAll: ((MobileCategoryScope) -> Unit)? = null,
    onHideAll: ((MobileCategoryScope) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedScope by remember { mutableStateOf(initialScope) }
    var searchQuery by remember { mutableStateOf("") }

    val currentCategories = when (selectedScope) {
        MobileCategoryScope.LIVE -> liveCategories
        MobileCategoryScope.MOVIES -> movieCategories
        MobileCategoryScope.SERIES -> seriesCategories
    }

    val currentHiddenNames = when (selectedScope) {
        MobileCategoryScope.LIVE -> hiddenLiveCategoryNames
        MobileCategoryScope.MOVIES -> hiddenMovieCategoryNames
        MobileCategoryScope.SERIES -> hiddenSeriesCategoryNames
    }

    val onToggleCurrent: (Category) -> Unit = when (selectedScope) {
        MobileCategoryScope.LIVE -> onToggleLiveCategory
        MobileCategoryScope.MOVIES -> onToggleMovieCategory
        MobileCategoryScope.SERIES -> onToggleSeriesCategory
    }

    val displayedCategories = remember(currentCategories, searchQuery) {
        if (searchQuery.isBlank()) currentCategories
        else currentCategories.filter { it.categoryName.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LelouchSurface)
                    .border(1.dp, LelouchBorder, RoundedCornerShape(16.dp))
            ) {
                // HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LelouchBackground)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gestor de Categorías",
                            color = LelouchTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Las categorías ocultas no aparecerán en el catálogo principal.",
                            color = LelouchTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }

                HorizontalDivider(color = LelouchBorder)

                // TABS SUPERIORES HORIZONTALES
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LelouchSurfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MobileCategoryScope.values().forEach { scope ->
                        val isSelected = selectedScope == scope
                        val count = when (scope) {
                            MobileCategoryScope.LIVE -> liveCategories.size
                            MobileCategoryScope.MOVIES -> movieCategories.size
                            MobileCategoryScope.SERIES -> seriesCategories.size
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) scope.color.copy(alpha = 0.2f) else LelouchSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) scope.color else LelouchBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedScope = scope }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${scope.iconText} ${scope.shortTitle}",
                                    color = if (isSelected) scope.color else LelouchTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "$count cats",
                                    color = if (isSelected) scope.color.copy(alpha = 0.85f) else LelouchTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = LelouchBorder)

                // BARRA DE ACCIONES RAPIDAS + BUSQUEDA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LelouchSurface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Campo de búsqueda de categoría
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar categoría...", color = LelouchTextMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = LelouchTextSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, tint = LelouchTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = selectedScope.color,
                            unfocusedBorderColor = LelouchBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )

                    // Botones rápidos: Mostrar Todas / Ocultar Todas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onShowAll?.invoke(selectedScope) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mostrar Todas", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onHideAll?.invoke(selectedScope) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ocultar Todas", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = LelouchBorder)

                // LISTA DE CATEGORÍAS
                if (displayedCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "📂",
                                fontSize = 36.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No hay categorías con '$searchQuery'" else "Sin categorías en ${selectedScope.shortTitle}",
                                color = LelouchTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayedCategories, key = { it.categoryId }) { cat ->
                            val isHidden = currentHiddenNames.contains(cat.categoryName) || currentHiddenNames.contains(cat.categoryId)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isHidden) Color.Black.copy(alpha = 0.35f) else LelouchSurfaceVariant)
                                    .border(
                                        1.dp,
                                        if (isHidden) LelouchBorder else selectedScope.color.copy(alpha = 0.45f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggleCurrent(cat) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.categoryName,
                                            color = if (isHidden) LelouchTextMuted else LelouchTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isHidden) FontWeight.Normal else FontWeight.Bold
                                        )
                                        if (cat.itemCount > 0) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${cat.itemCount} items",
                                                color = LelouchTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isHidden) LelouchSurface else selectedScope.color)
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = if (isHidden) LelouchTextSecondary else Color.Black,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isHidden) "OCULTO" else "VISIBLE",
                                                color = if (isHidden) LelouchTextSecondary else Color.Black,
                                                fontSize = 11.sp,
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
