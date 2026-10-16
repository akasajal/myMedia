package com.ishaan.myMedia.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.painter.ColorPainter
import coil.request.ImageRequest
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.data.model.MediaType
import com.ishaan.myMedia.ui.theme.PrimaryAccent
import com.ishaan.myMedia.ui.theme.AccentForeground
import com.ishaan.myMedia.ui.theme.SoftPurple

// ── Media thumbnail with selection support ────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    item: MediaItem,
    thumbnailSizePx: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    isSelected: Boolean = false,
    selectionActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val request = remember(context, item.uri, item.mediaType, item.dateTaken, item.size, thumbnailSizePx) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(thumbnailSizePx.coerceAtLeast(1))
            .apply { if (item.mediaType == MediaType.VIDEO) setParameter("videoFrameMicros", 1_000_000L) }
            .crossfade(false)
            .memoryCacheKey("thumbnail:${item.uri}:${item.dateTaken}:${item.size}:$thumbnailSizePx")
            .diskCacheKey(item.uri.toString())
            .build()
    }
    val overlayColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black.copy(alpha = 0.35f) else Color.Transparent,
        label = "selection_overlay"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryAccent else Color.Transparent,
        label = "selection_border"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .semantics {
                contentDescription = "${item.name}, ${item.mediaType.name.lowercase()}"
                if (selectionActive) {
                    role = Role.Checkbox
                    selected = isSelected
                    stateDescription = if (isSelected) "Selected" else "Not selected"
                }
            }
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.combinedClickable(
                        onClickLabel = if (selectionActive) { if (isSelected) "Deselect media" else "Select media" } else "Open media",
                        onLongClickLabel = "Select media",
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                else Modifier
            )
            .then(
                if (isSelected)
                    Modifier.border(2.dp, borderColor, RoundedCornerShape(cornerRadius))
                else Modifier
            )
    ) {
        AsyncImage(
            model = request,
            placeholder = placeholder,
            error = placeholder,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Selection overlay
        if (selectionActive || isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor)
            )
        }

        // Checkmark badge
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(PrimaryAccent, CircleShape)
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = AccentForeground,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            }
        } else if (selectionActive) {
            // Empty circle when selection mode active but not selected
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color.Black.copy(0.3f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }

        if (item.mediaType == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    if (item.duration > 0) {
                        Text(
                            text = formatDuration(item.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (item.mediaType == MediaType.GIF) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(PrimaryAccent.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("GIF", style = MaterialTheme.typography.labelSmall, color = AccentForeground)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins  = minutes % 60
        "%d:%02d:%02d".format(hours, mins, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
fun DateGroupHeader(label: String) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun PurpleGradientFab(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(listOf(PrimaryAccent, SoftPurple))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content               = content,
        )
    }
}
