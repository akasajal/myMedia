package com.ishaan.myMedias.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ishaan.myMedias.data.model.MediaItem

@Composable
fun MediaGridRow(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    columns: Int = 4,             // default changed to 4
    modifier: Modifier = Modifier,
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
                MediaThumbnail(
                    item         = items[idx],
                    modifier     = Modifier.size(cellSize),
                    cornerRadius = 0.dp,
                    onClick      = { onItemClick(items[idx]) },
                )
            } else {
                Spacer(modifier = Modifier.size(cellSize))
            }
        }
    }
    Spacer(Modifier.height(gap))
}
