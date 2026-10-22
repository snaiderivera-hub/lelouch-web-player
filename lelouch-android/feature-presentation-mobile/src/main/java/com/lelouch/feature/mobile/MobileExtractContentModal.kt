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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.Category
import com.lelouch.core.model.SourceConfig

/**
 * FASE 33 — Modal de Extracción de Contenido a "Mi Lista LELOUCH" (Paridad con Web Player)
 * Permite seleccionar categorías de canales de TV, películas o series de cualquier proveedor
 * para enriquecer la lista personalizada local y sincronizar con Supabase Cloud.
 */
@Composable
fun MobileExtractContentModal(
    source: SourceConfig,
    liveCategories: List<Category>,
    vodCategories: List<Category>,
    seriesCategories: List<Category>,
    onExtract: (contentType: String, selectedCategoryNames: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedContentType by remember { mutableStateOf("LIVE") } // "LIVE", "VOD", "SERIES"
    var searchQuery by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }

    // Conjuntos de selección por tipo
    val selectedLive = remember { mutableStateListOf<String>() }
    val selectedVod = remember { mutableStateListOf<String>() }
    val selectedSeries = remember { mutableStateListOf<String>() }

    val currentCategories = when (selectedContentType) {
        "VOD" -> vodCategories
        "SERIES" -> seriesCategories
        else -> liveCategories
    }

    val currentSelected = when (selectedContentType) {
        "VOD" -> selectedVod
        "SERIES" -> selectedSeries
        else -> selectedLive
    }

    val filteredCategories = if (searchQuery.isBlank()) {
        currentCategories
    } else {
        currentCategories.filter { it.categoryName.contains(searchQuery, ignoreCase = true) }
    }

    val totalSelectedItems = currentCategories
        .filter { currentSelected.contains(it.categoryName) }
        .sumOf { it.itemCount }

    val contentTypeLabel = when (selectedContentType) {
        "VOD" -> "películas"
        "SERIES" -> "series"
        else -> "canales"
    }

    Dialog(
        onDismissRequest = { if (!isExtracting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, LelouchCyanAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                color = LelouchSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // ── CABECERA ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "📥 Extraer a Mi Lista LELOUCH",
                                    color = LelouchTextPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Desde: ${source.name} (${source.serverUrl.take(28)})",
                                color = LelouchCyanAccent,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            enabled = !isExtracting,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LelouchSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = LelouchTextSecondary)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── SELECTOR DE TIPO (TV, PELÍCULAS, SERIES) ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExtractTypePill(
                            title = "📺 Canales (${liveCategories.size})",
                            isSelected = selectedContentType == "LIVE",
                            onClick = { selectedContentType = "LIVE" },
                            modifier = Modifier.weight(1f)
                        )
                        ExtractTypePill(
                            title = "🎬 Películas (${vodCategories.size})",
                            isSelected = selectedContentType == "VOD",
                            onClick = { selectedContentType = "VOD" },
                            modifier = Modifier.weight(1f)
                        )
                        ExtractTypePill(
                            title = "🎞️ Series (${seriesCategories.size})",
                            isSelected = selectedContentType == "SERIES",
                            onClick = { selectedContentType = "SERIES" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── BARRA DE BÚSQUEDA ──
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("🔍 Filtrar categorías de $contentTypeLabel...", fontSize = 12.sp, color = LelouchTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LelouchCyanAccent, modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LelouchCyanAccent,
                            unfocusedBorderColor = LelouchBorder,
                            focusedTextColor = LelouchTextPrimary,
                            unfocusedTextColor = LelouchTextPrimary,
                            focusedContainerColor = LelouchBackground,
                            unfocusedContainerColor = LelouchBackground
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── BOTONES DE MARCAR / DESMARCAR TODAS ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentSelected.size} cat. seleccionadas (~$totalSelectedItems $contentTypeLabel)",
                            color = if (currentSelected.isNotEmpty()) LelouchCyanAccent else LelouchTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    currentSelected.clear()
                                    currentSelected.addAll(currentCategories.map { it.categoryName })
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, LelouchBorder)
                            ) {
                                Text("☑️ Todas", color = LelouchTextPrimary, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { currentSelected.clear() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, LelouchBorder)
                            ) {
                                Text("⬜ Ninguna", color = LelouchTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── LISTADO DE CATEGORÍAS DISPONIBLES ──
                    if (filteredCategories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (currentCategories.isEmpty()) "No hay categorías de $contentTypeLabel en este proveedor.\nSincroniza la lista primero con [🔄 Recargar]."
                                else "No se encontraron categorías que coincidan con la búsqueda.",
                                color = LelouchTextSecondary,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredCategories, key = { it.categoryId }) { cat ->
                                val isChecked = currentSelected.contains(cat.categoryName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChecked) LelouchCyanAccent.copy(alpha = 0.12f) else LelouchSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isChecked) LelouchCyanAccent.copy(alpha = 0.5f) else LelouchBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (isChecked) currentSelected.remove(cat.categoryName)
                                            else currentSelected.add(cat.categoryName)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked) currentSelected.add(cat.categoryName)
                                                else currentSelected.remove(cat.categoryName)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = LelouchCyanAccent,
                                                checkmarkColor = Color.Black
                                            ),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "📁 ${cat.categoryName}",
                                            color = if (isChecked) LelouchCyanAccent else LelouchTextPrimary,
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (cat.itemCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(LelouchCyanAccent.copy(alpha = 0.15f))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "${cat.itemCount}",
                                                    color = LelouchCyanAccent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Botón Rápido: Extraer solo esta categoría
                                        Button(
                                            onClick = {
                                                isExtracting = true
                                                onExtract(selectedContentType, setOf(cat.categoryName))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = LelouchCyanAccent.copy(alpha = 0.85f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("⚡ Extraer", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── BOTÓN DE ACCIÓN PRINCIPAL ──
                    Button(
                        onClick = {
                            if (currentSelected.isNotEmpty()) {
                                isExtracting = true
                                onExtract(selectedContentType, currentSelected.toSet())
                            }
                        },
                        enabled = currentSelected.isNotEmpty() && !isExtracting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LelouchCyanAccent,
                            disabledContainerColor = LelouchSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Extrayendo y guardando en Mi Lista...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                "📥 Extraer ${currentSelected.size} Categorías a Mi Lista LELOUCH",
                                color = if (currentSelected.isNotEmpty()) Color.Black else LelouchTextSecondary,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractTypePill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) LelouchCyanAccent else LelouchSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else LelouchTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
