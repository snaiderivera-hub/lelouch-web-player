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
import androidx.compose.material.icons.filled.Close
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
    SERIES("Series", "Series", "🎭", Color(0xFF818CF8))
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
                text = "Selecciona qué categorías deseas que aparezcan y cuáles ocultar.",
                color = LelouchTextSecondary,
                fontSize = 13.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MobileCategorySummaryCard(
                scope = MobileCategoryScope.LIVE,
                total = liveTotal,
                hiddenCount = liveHiddenCount,
                buttonLabel = "Editar",
                onClick = { onOpenManager(MobileCategoryScope.LIVE) },
                modifier = Modifier.weight(1f)
            )

            MobileCategorySummaryCard(
                scope = MobileCategoryScope.MOVIES,
                total = moviesTotal,
                hiddenCount = moviesHiddenCount,
                buttonLabel = "Editar",
                onClick = { onOpenManager(MobileCategoryScope.MOVIES) },
                modifier = Modifier.weight(1f)
            )

            MobileCategorySummaryCard(
                scope = MobileCategoryScope.SERIES,
                total = seriesTotal,
                hiddenCount = seriesHiddenCount,
                buttonLabel = "Editar",
                onClick = { onOpenManager(MobileCategoryScope.SERIES) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MobileCategorySummaryCard(
    scope: MobileCategoryScope,
    total: Int,
    hiddenCount: Int,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = (total - hiddenCount).coerceAtLeast(0)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LelouchSurfaceVariant)
            .border(1.dp, LelouchBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${scope.iconText} ${scope.shortTitle}",
                    color = scope.color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, LelouchBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$visibleCount/$total",
                    color = LelouchTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (hiddenCount > 0) "🚫 $hiddenCount ocultas" else "✓ Todas visibles",
                color = if (hiddenCount > 0) Color(0xFFFF5252) else Color(0xFF34D399),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, LelouchBorder, RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonLabel,
                    color = LelouchTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
    onDismiss: () -> Unit
) {
    var selectedScope by remember { mutableStateOf(initialScope) }

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
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LelouchSurface)
                    .border(1.dp, LelouchBorder, RoundedCornerShape(16.dp))
            ) {
                // HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LelouchBackground)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gestor de Categorías Visibles",
                            color = LelouchTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Las categorías ocultas no aparecerán en el catálogo principal.",
                            color = LelouchTextSecondary,
                            fontSize = 13.sp
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
                
                Divider(color = LelouchBorder)

                // TABS SUPERIORES HORIZONTALES (Adaptado a móviles verticales)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LelouchSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                .padding(vertical = 10.dp, horizontal = 4.dp),
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

                Divider(color = LelouchBorder)

                // LISTA DE CATEGORÍAS (100% ANCHO CON ESTADO VACÍO)
                if (currentCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "📭",
                                fontSize = 36.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Sin categorías en ${selectedScope.shortTitle}",
                                color = LelouchTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Asegúrate de que la cuenta esté activa y sincronizada en Ajustes > Sync.",
                                color = LelouchTextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentCategories, key = { it.categoryId }) { cat ->
                            val isHidden = currentHiddenNames.contains(cat.categoryName) || currentHiddenNames.contains(cat.categoryId)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isHidden) Color.Black.copy(alpha = 0.3f) else LelouchSurfaceVariant)
                                    .border(
                                        1.dp,
                                        if (isHidden) LelouchBorder else selectedScope.color.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggleCurrent(cat) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                            fontSize = 14.sp,
                                            fontWeight = if (isHidden) FontWeight.Normal else FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${cat.itemCount} items",
                                            color = LelouchTextSecondary,
                                            fontSize = 11.sp
                                        )
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
                                                modifier = Modifier.size(15.dp)
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
