package com.ishaan.myMedia.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ishaan.myMedia.data.model.MediaItem

@Composable
fun MediaGridRow(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    columns: Int = 4,
    modifier: Modifier = Modifier,
    selectedIds: Set<Long> = emptySet(),
    selectionActive: Boolean = false,
    onItemLongClick: ((MediaItem) -> Unit)? = null,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val gap         = 2.dp
    val cellSize    = (screenWidth - gap * (columns - 1)) / columns

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(columns) { idx ->
            if (idx < items.size) {
                val item = items[idx]
                MediaThumbnail(
                    item            = item,
                    modifier        = Modifier.size(cellSize),
                    cornerRadius    = 0.dp,
                    isSelected      = item.id in selectedIds,
                    selectionActive = selectionActive,
                    onClick         = { onItemClick(item) },
                    onLongClick     = onItemLongClick?.let { handler -> { handler(item) } },
                )
            } else {
                Spacer(modifier = Modifier.size(cellSize))
            }
        }
    }
    Spacer(Modifier.height(gap))
}
