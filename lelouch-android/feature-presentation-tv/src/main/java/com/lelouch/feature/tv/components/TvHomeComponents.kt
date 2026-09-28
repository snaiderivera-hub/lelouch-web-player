package com.lelouch.feature.tv.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.compose.foundation.BorderStroke
import com.lelouch.core.designsystem.*
import com.lelouch.core.model.SourceConfig
import com.lelouch.core.player.PlaybackState
import com.lelouch.core.player.VideoTrackInfo

/**
 * Top status header displaying active IPTV source and playback resolution.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeHeaderBar(
    activeSource: SourceConfig?,
    videoInfo: VideoTrackInfo,
    playbackState: PlaybackState,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active Source Pill (Clickable & Focusable)
        var isPillFocused by remember { mutableStateOf(false) }
        Surface(
            onClick = onOpenAdmin,
            modifier = Modifier.onFocusChanged { isPillFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(
                shape = RoundedCornerShape(20.dp),
                focusedShape = RoundedCornerShape(20.dp)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = LelouchSurfaceVariant,
                focusedContainerColor = LelouchCyanAccent.copy(alpha = 0.35f),
                pressedContainerColor = LelouchCyanAccent.copy(alpha = 0.35f)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(androidx.compose.foundation.BorderStroke(1.dp, LelouchBorder)),
                focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, LelouchCyanAccent))
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activeSource?.name ?: "IPTV Activa",
                    color = if (isPillFocused) Color.White else LelouchTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ajustes ▼",
                    color = LelouchCyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Connection & Resolution Indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (playbackState) {
                            is PlaybackState.Playing -> LelouchCyanAccent
                            is PlaybackState.Buffering -> Color(0xFFFFB300)
                            is PlaybackState.Error -> LelouchLiveRed
                            else -> LelouchTextMuted
                        }
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = videoInfo.resolutionLabel,
                color = LelouchCyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Section Title for content rails.
 */
@Composable
fun ContentSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = LelouchTextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 32.dp, bottom = 12.dp)
    )
}

/**
 * Cinematic Hero Spotlight (EveryCine / Vix style)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroSpotlight(
    title: String,
    subtitle: String,
    badge: String,
    meta: String,
    playButtonText: String,
    onPlayClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    playButtonRequester: FocusRequester? = null,
    sidebarRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 16.dp, bottom = 22.dp, end = 64.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (badge == "EN VIVO") LelouchLiveRed else LelouchCyanAccent)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badge == "EN VIVO") {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = badge,
                        color = if (badge == "EN VIVO") Color.White else Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = meta,
                color = LelouchCyanAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            color = LelouchTextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            color = LelouchTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            var isPlayFocused by remember { mutableStateOf(false) }
            val playModifier = Modifier
                .then(if (playButtonRequester != null) Modifier.focusRequester(playButtonRequester) else Modifier)
                .then(if (sidebarRequester != null) Modifier.focusProperties { left = sidebarRequester } else Modifier)
                .onFocusChanged { isPlayFocused = it.isFocused }

            Surface(
                onClick = onPlayClick,
                modifier = playModifier,
                shape = ClickableSurfaceDefaults.shape(
                    shape = RoundedCornerShape(8.dp),
                    focusedShape = RoundedCornerShape(8.dp)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = LelouchSurfaceVariant,
                    focusedContainerColor = LelouchCyanAccent,
                    pressedContainerColor = LelouchCyanAccent
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(androidx.compose.foundation.BorderStroke(1.dp, LelouchBorder)),
                    focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, Color.White))
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlayFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playButtonText,
                        color = if (isPlayFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (secondaryButtonText != null && onSecondaryClick != null) {
                var isSecFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onSecondaryClick,
                    modifier = Modifier.onFocusChanged { isSecFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(
                        shape = RoundedCornerShape(8.dp),
                        focusedShape = RoundedCornerShape(8.dp)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = LelouchSurfaceVariant.copy(alpha = 0.9f),
                        pressedContainerColor = LelouchSurfaceVariant
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(androidx.compose.foundation.BorderStroke(1.dp, LelouchBorder)),
                        focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, LelouchCyanAccent))
                    )
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = secondaryButtonText,
                            color = if (isSecFocused) Color.White else LelouchTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Action Button for settings & admin actions.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp),
            focusedShape = RoundedCornerShape(8.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LelouchSurfaceVariant,
            focusedContainerColor = if (isDanger) LelouchLiveRed else LelouchCyanAccent,
            pressedContainerColor = if (isDanger) LelouchLiveRed else LelouchCyanAccent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, LelouchBorder)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, if (isDanger) LelouchLiveRed else LelouchCyanAccent))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) (if (isDanger) Color.White else Color.Black) else (if (isDanger) LelouchLiveRed else LelouchCyanAccent),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isFocused) (if (isDanger) Color.White else Color.Black) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Model representing a category chip for TV filter bars.
 */
data class CategoryUiItem(
    val id: String,
    val name: String
)

/**
 * Leanback category selector bar for filtering Live Channels, Movies, and Series.
 * Supports D-Pad horizontal navigation, active category highlight, and sidebar escape.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCategorySelectorBar(
    categories: List<CategoryUiItem>,
    selectedCategoryId: String,
    onSelectCategory: (CategoryUiItem) -> Unit,
    firstItemRequester: FocusRequester? = null,
    sidebarRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    TvLazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(categories, key = { _, cat -> "cat_${cat.id}_${cat.name}" }) { index, cat ->
            val isSelected = (cat.id == selectedCategoryId)
            var isFocused by remember { mutableStateOf(false) }

            val itemModifier = when {
                index == 0 && firstItemRequester != null && sidebarRequester != null -> {
                    Modifier
                        .focusRequester(firstItemRequester)
                        .focusProperties { left = sidebarRequester }
                }
                index == 0 && firstItemRequester != null -> {
                    Modifier.focusRequester(firstItemRequester)
                }
                else -> Modifier
            }

            Surface(
                onClick = { onSelectCategory(cat) },
                modifier = itemModifier.onFocusChanged { isFocused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(
                    shape = RoundedCornerShape(20.dp),
                    focusedShape = RoundedCornerShape(20.dp)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) LelouchCyanAccent else LelouchSurface,
                    focusedContainerColor = if (isSelected) Color.White else LelouchCardFocused,
                    pressedContainerColor = if (isSelected) Color.White else LelouchCardFocused
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        BorderStroke(
                            1.dp,
                            if (isSelected) LelouchCyanAccent else LelouchBorder
                        )
                    ),
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = cat.name,
                        color = if (isSelected) Color.Black else if (isFocused) Color.White else LelouchTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
