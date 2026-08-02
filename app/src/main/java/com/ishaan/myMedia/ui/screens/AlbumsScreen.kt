package com.ishaan.myMedia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ishaan.myMedia.data.model.Album
import com.ishaan.myMedia.data.model.MediaGroup
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.ui.components.DateGroupHeader
import com.ishaan.myMedia.ui.components.MediaGridRow
import com.ishaan.myMedia.ui.theme.PrimaryAccent
import com.ishaan.myMedia.ui.theme.SoftPurple
import com.ishaan.myMedia.viewmodel.GalleryUiState

enum class AlbumSortOrder { NAME_ASC, ITEM_COUNT_DESC, DATE_RECENT }

@Composable
fun AlbumsScreen(
    uiState: GalleryUiState,
    onAlbumClick: (Album) -> Unit,
    onCreateAlbum: (String) -> Unit = {},
    pinnedAlbumIds: Set<String> = emptySet(),
    onTogglePin: (Album) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sortOrder by remember { mutableStateOf(AlbumSortOrder.NAME_ASC) }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.albums.isEmpty()) {
        EmptyState("No albums found.\nChoose folders in Settings.", modifier)
        return
    }

    // Sort albums, pinned always first
    val sorted = remember(uiState.albums, sortOrder, pinnedAlbumIds) {
        val comparator: Comparator<Album> = when (sortOrder) {
            AlbumSortOrder.NAME_ASC         -> compareBy { it.name.lowercase() }
            AlbumSortOrder.ITEM_COUNT_DESC  -> compareByDescending { it.mediaCount }
            AlbumSortOrder.DATE_RECENT      -> compareByDescending { it.mediaCount } // proxy; real date needs repo
        }
        val pinned   = uiState.albums.filter { it.id in pinnedAlbumIds }.sortedWith(comparator)
        val unpinned = uiState.albums.filter { it.id !in pinnedAlbumIds }.sortedWith(comparator)
        pinned + unpinned
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Sort bar
            AlbumSortBar(
                current = sortOrder,
                onChange = { sortOrder = it },
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted.size, key = { sorted[it].id }) { idx ->
                    val album = sorted[idx]
                    AlbumCard(
                        album    = album,
                        isPinned = album.id in pinnedAlbumIds,
                        onClick  = { onAlbumClick(album) },
                        onLongClick = { onTogglePin(album) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = PrimaryAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Rounded.CreateNewFolder, "Create album", tint = Color.White)
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            onDismiss = { showCreateDialog = false },
            onCreate  = { name ->
                onCreateAlbum(name)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun AlbumSortBar(
    current: AlbumSortOrder,
    onChange: (AlbumSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (current) {
        AlbumSortOrder.NAME_ASC        -> "Name (A–Z)"
        AlbumSortOrder.ITEM_COUNT_DESC -> "Most items"
        AlbumSortOrder.DATE_RECENT     -> "Recent"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(
                onClick = { expanded = true },
            ) {
                Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AlbumSortOrder.entries.forEach { order ->
                    val optLabel = when (order) {
                        AlbumSortOrder.NAME_ASC        -> "Name (A–Z)"
                        AlbumSortOrder.ITEM_COUNT_DESC -> "Most items"
                        AlbumSortOrder.DATE_RECENT     -> "Recent"
                    }
                    DropdownMenuItem(
                        text = { Text(optLabel) },
                        onClick = { onChange(order); expanded = false },
                        leadingIcon = {
                            if (current == order)
                                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: Album,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val pinTint by animateColorAsState(
        targetValue = if (isPinned) PrimaryAccent else Color.White.copy(0.7f),
        label = "pin_color"
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (album.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(PrimaryAccent.copy(0.4f), SoftPurple.copy(0.3f)))
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))
                )
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text  = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "${album.mediaCount} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.75f),
                )
            }
        }

        // Pin icon — top-end corner
        Icon(
            imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.PushPin,
            contentDescription = if (isPinned) "Pinned" else "Long-press to pin",
            tint = pinTint,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun CreateAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Album") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Album name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (trimmed.isNotEmpty()) onCreate(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun AlbumDetailScreen(
    album: Album,
    groups: List<MediaGroup>,
    onBack: () -> Unit,
    onMediaClick: (MediaItem, List<MediaItem>, Int) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    selection: SelectionState = SelectionState(),
    onSelectionChange: (SelectionState) -> Unit = {},
    onBulkDelete: () -> Unit = {},
    onBulkShare: () -> Unit = {},
    onBulkMove: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val flatItems = groups.flatMap { it.items }

    data class RowEntry(val startFlatIndex: Int, val count: Int)

    val lazyRowEntries: List<RowEntry?> = remember(groups, columns) {
        val result = mutableListOf<RowEntry?>()
        var flatIdx = 0
        groups.forEach { group ->
            result.add(null)
            group.items.chunked(columns).forEach { row ->
                result.add(RowEntry(flatIdx, row.size))
                flatIdx += row.size
            }
        }
        result
    }

    var dragSelect by remember { mutableStateOf(DragSelectState()) }
    var preSelectionIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val screenWidthPx = remember { mutableFloatStateOf(0f) }

    fun yToFlatIndex(y: Float, pointerX: Float): Int {
        val info = listState.layoutInfo
        val viewportStart = info.viewportStartOffset
        val rowListIdx = info.visibleItemsInfo.firstOrNull { visItem ->
            val top = visItem.offset - viewportStart
            y >= top && y < top + visItem.size
        }?.index ?: return -1
        if (rowListIdx >= lazyRowEntries.size) return -1
        val entry = lazyRowEntries[rowListIdx] ?: return -1
        val col = (pointerX / (screenWidthPx.floatValue / columns)).toInt().coerceIn(0, columns - 1)
        val flatIdx = entry.startFlatIndex + col
        return if (flatIdx < flatItems.size) flatIdx else flatItems.size - 1
    }

    LaunchedEffect(dragSelect, preSelectionIds) {
        if (!dragSelect.isActive) return@LaunchedEffect
        val rangeIds = dragSelect.selectedRange().map { flatItems[it].id }.toSet()
        onSelectionChange(SelectionState(preSelectionIds + rangeIds))
    }

    BackHandler(enabled = selection.isActive) {
        onSelectionChange(SelectionState())
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { screenWidthPx.floatValue = it.size.width.toFloat() }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = {
                    if (selection.isActive) onSelectionChange(SelectionState()) else onBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text  = album.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (groups.isEmpty()) {
                EmptyState("No media in this album.")
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToAdjustColumns(columns, onColumnsChange)
                    .pointerInput(selection.isActive, columns, flatItems) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var currentPos = down.position
                            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                            var longPressTriggered = false
                            val downTime = System.currentTimeMillis()

                            do {
                                val event = awaitPointerEvent()
                                val elapsed = System.currentTimeMillis() - downTime
                                val change = event.changes.firstOrNull() ?: break

                                if (!longPressTriggered && elapsed >= longPressTimeout) {
                                    longPressTriggered = true
                                    val anchorFlat = yToFlatIndex(down.position.y, down.position.x)
                                    if (anchorFlat >= 0) {
                                        preSelectionIds = selection.selectedIds
                                        dragSelect = DragSelectState(anchorFlat, anchorFlat)
                                        change.consume()
                                    }
                                }

                                if (longPressTriggered && dragSelect.isActive) {
                                    currentPos = change.position
                                    val newFlat = yToFlatIndex(currentPos.y, currentPos.x)
                                    if (newFlat >= 0 && newFlat != dragSelect.currentIndex) {
                                        dragSelect = dragSelect.copy(currentIndex = newFlat)
                                    }
                                    change.consume()
                                    val topEdge = 80f
                                    val bottomEdge = size.height - 80f
                                    when {
                                        currentPos.y < topEdge -> scope.launch { listState.scrollBy(-18f) }
                                        currentPos.y > bottomEdge -> scope.launch { listState.scrollBy(18f) }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            dragSelect = DragSelectState()
                        }
                    },
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                groups.forEach { group ->
                    item(key = "header_${group.label}") { DateGroupHeader(group.label) }
                    val rows = group.items.chunked(columns)
                    itemsIndexed(rows, key = { i, _ -> "row_${group.label}_$i" }) { _, row ->
                        MediaGridRow(
                            items           = row,
                            columns         = columns,
                            selectedIds     = selection.selectedIds,
                            selectionActive = selection.isActive,
                            onItemClick = { item ->
                                if (selection.isActive) {
                                    onSelectionChange(selection.toggle(item.id))
                                } else {
                                    val idx = flatItems.indexOfFirst { it.id == item.id }
                                    onMediaClick(item, flatItems, idx.coerceAtLeast(0))
                                }
                            },
                            onItemLongClick = { item ->
                                onSelectionChange(selection.toggle(item.id))
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = selection.isActive,
            enter    = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            AlbumSelectionToolbar(
                count       = selection.count,
                allSelected = selection.count == flatItems.size,
                onClose     = { onSelectionChange(SelectionState()) },
                onSelectAll = { onSelectionChange(selection.selectAll(flatItems.map { it.id })) },
                onShare     = onBulkShare,
                onMove      = onBulkMove,
                onDelete    = onBulkDelete,
            )
        }
    }
}

@Composable
private fun AlbumSelectionToolbar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        tonalElevation  = 4.dp,
        shadowElevation = 4.dp,
        modifier        = Modifier.fillMaxWidth().statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, "Cancel selection")
            }
            Text(
                "$count selected",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) {
                Icon(
                    if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    if (allSelected) "Deselect all" else "Select all",
                )
            }
            IconButton(onClick = onShare, enabled = count > 0) {
                Icon(Icons.Rounded.Share, "Share")
            }
            IconButton(onClick = onMove, enabled = count > 0) {
                Icon(Icons.Rounded.DriveFileMove, "Move")
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}