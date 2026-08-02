package com.ishaan.myMedia.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.unit.dp
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.ui.components.DateGroupHeader
import com.ishaan.myMedia.ui.components.MediaGridRow
import com.ishaan.myMedia.viewmodel.GalleryUiState
import kotlinx.coroutines.launch

@Composable
fun MediaScreen(
    uiState: GalleryUiState,
    onMediaClick: (MediaItem, Int) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    // Selection callbacks — wired up by MainScreen
    selection: SelectionState = SelectionState(),
    onSelectionChange: (SelectionState) -> Unit = {},
    onBulkDelete: () -> Unit = {},
    onBulkShare: () -> Unit = {},
    onBulkMove: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

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

    // Show scroll-to-top when beyond first visible item
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
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
                        items           = row,
                        columns         = columns,
                        selectedIds     = selection.selectedIds,
                        selectionActive = selection.isActive,
                        onItemClick = { item ->
                            if (selection.isActive) {
                                onSelectionChange(selection.toggle(item.id))
                            } else {
                                val flatIndex = flatItems.indexOfFirst { it.id == item.id }
                                onMediaClick(item, flatIndex.coerceAtLeast(0))
                            }
                        },
                        onItemLongClick = { item ->
                            onSelectionChange(selection.toggle(item.id))
                        },
                    )
                }
            }
        }

        // Selection toolbar
        AnimatedVisibility(
            visible = selection.isActive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit  = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionToolbar(
                count        = selection.count,
                totalCount   = flatItems.size,
                allSelected  = selection.count == flatItems.size,
                onClose      = { onSelectionChange(SelectionState()) },
                onSelectAll  = {
                    onSelectionChange(selection.selectAll(flatItems.map { it.id }))
                },
                onShare      = onBulkShare,
                onMove       = onBulkMove,
                onDelete     = onBulkDelete,
            )
        }

        // Scroll-to-top FAB
        AnimatedVisibility(
            visible = showScrollToTop && !selection.isActive,
            enter = fadeIn() + scaleIn(),
            exit  = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    totalCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
            }
            Text(
                "$count selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // Select all / deselect all
            IconButton(onClick = onSelectAll) {
                Icon(
                    if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                )
            }
            IconButton(onClick = onShare, enabled = count > 0) {
                Icon(Icons.Rounded.Share, contentDescription = "Share")
            }
            IconButton(onClick = onMove, enabled = count > 0) {
                Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move")
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
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
                if (accumulatedZoom < 0.8f) {
                    if (columns < 6) onColumnsChange(columns + 1)
                    accumulatedZoom = 1f
                } else if (accumulatedZoom > 1.25f) {
                    if (columns > 2) onColumnsChange(columns - 1)
                    accumulatedZoom = 1f
                }
            }
        } while (event.changes.any { it.pressed })
    }
}