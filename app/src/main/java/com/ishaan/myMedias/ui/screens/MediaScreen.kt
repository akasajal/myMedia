package com.ishaan.myMedias.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.unit.dp
import com.ishaan.myMedias.data.model.MediaGroup
import com.ishaan.myMedias.data.model.MediaItem
import com.ishaan.myMedias.ui.components.DateGroupHeader
import com.ishaan.myMedias.ui.components.MediaGridRow
import com.ishaan.myMedias.viewmodel.GalleryUiState

@Composable
fun MediaScreen(
    uiState: GalleryUiState,
    onMediaClick: (MediaItem, Int) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.allMedia.isEmpty()) {
        EmptyState(
            message = if (uiState.chosenFolders.isEmpty())
                "No folders selected.\nGo to Settings to choose folders."
            else "No media found in the selected folders.",
            modifier = modifier,
        )
        return
    }

    val flatItems = uiState.mediaGroups.flatMap { it.items }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pinchToAdjustColumns(columns, onColumnsChange),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        uiState.mediaGroups.forEach { group ->
            item(key = "header_${group.label}") {
                DateGroupHeader(group.label)
            }
            val rows = group.items.chunked(columns)
            itemsIndexed(rows, key = { idx, _ -> "row_${group.label}_$idx" }) { _, row ->
                MediaGridRow(
                    items   = row,
                    columns = columns,
                    onItemClick = { item ->
                        val flatIndex = flatItems.indexOfFirst { it.id == item.id }
                        onMediaClick(item, flatIndex.coerceAtLeast(0))
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Pinch gesture modifier that adjusts column count.
 * Pinch in (zoom < 1) → more columns (max 6); pinch out (zoom > 1) → fewer columns (min 2).
 */
fun Modifier.pinchToAdjustColumns(
    columns: Int,
    onColumnsChange: (Int) -> Unit,
): Modifier = this.pointerInput(columns) {
    var accumulatedZoom = 1f
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        accumulatedZoom = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                accumulatedZoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                // Threshold: require ~20% zoom change before triggering a column step
                if (accumulatedZoom < 0.8f) {
                    // Pinch in → more columns (smaller cells)
                    if (columns < 6) onColumnsChange(columns + 1)
                    accumulatedZoom = 1f
                } else if (accumulatedZoom > 1.25f) {
                    // Pinch out → fewer columns (larger cells)
                    if (columns > 2) onColumnsChange(columns - 1)
                    accumulatedZoom = 1f
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
