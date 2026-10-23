package com.lelouch.feature.tv.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import coil.compose.AsyncImage
import com.lelouch.core.designsystem.LelouchBorder
import com.lelouch.core.designsystem.LelouchCardFocused
import com.lelouch.core.designsystem.LelouchCyanAccent
import com.lelouch.core.designsystem.LelouchError
import com.lelouch.core.designsystem.LelouchSurface
import com.lelouch.core.designsystem.LelouchSurfaceVariant
import com.lelouch.core.designsystem.LelouchTextMuted
import com.lelouch.core.designsystem.LelouchTextPrimary
import com.lelouch.core.designsystem.LelouchTextSecondary
import com.lelouch.core.model.ContinueWatchingItem

/**
 * FASE 37 · Historial / «Continuar viendo» para Android TV.
 *
 * Cada fila es enfocable con el D-pad: OK reanuda en el punto guardado y el botón ROJO
 * (o DEL) quita la entrada. Un chip superior permite borrar todo el historial.
 */
@Composable
fun TvHistoryContent(
    items: List<ContinueWatchingItem>,
    onResume: (ContinueWatchingItem) -> Unit,
    onRemove: (ContinueWatchingItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "🕘 CONTINUAR VIENDO",
                    color = LelouchCyanAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "OK reanuda · ROJO quita de la lista",
                    color = LelouchTextMuted,
                    fontSize = 11.sp
                )
            }

            if (items.isNotEmpty()) {
                ClearAllChip(onClick = onClearAll)
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🕘", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Todavía no has visto nada",
                        color = LelouchTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Las películas y episodios que empieces aparecerán aquí.",
                        color = LelouchTextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.contentId }) { index, item ->
                    HistoryRow(
                        item = item,
                        onResume = { onResume(item) },
                        onRemove = { onRemove(item) },
                        // El foco inicial debe caer en una fila real: un TvLazyColumn vacío
                        // no es un objetivo de foco válido y dejaría el D-pad sin destino.
                        modifier = if (index == 0 && focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: ContinueWatchingItem,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_PROG_RED,
                    KeyEvent.KEYCODE_DEL,
                    KeyEvent.KEYCODE_FORWARD_DEL -> {
                        onRemove()
                        true
                    }
                    else -> false
                }
            }
            .clickable { onResume() }
            .background(
                color = if (isFocused) LelouchCardFocused else LelouchSurface,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) LelouchCyanAccent else LelouchBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.fraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(LelouchCyanAccent)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = if (isFocused) LelouchTextPrimary else LelouchTextSecondary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(
                item.episodeLabel,
                item.remainingLabel.takeIf { it.isNotBlank() },
                "${(item.fraction * 100).toInt()} %"
            ).joinToString("  ·  ")
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = LelouchTextMuted, fontSize = 12.sp)
        }

        if (isFocused) {
            Text(
                text = "▶  OK",
                color = LelouchCyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun ClearAllChip(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .background(
                color = if (isFocused) LelouchSurfaceVariant else LelouchSurface,
                shape = RoundedCornerShape(50)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) LelouchError else LelouchBorder,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = "🗑  Borrar todo",
            color = if (isFocused) LelouchError else LelouchTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
