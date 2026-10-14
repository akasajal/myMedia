package com.ishaan.myMedia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ishaan.myMedia.data.model.Album
import com.ishaan.myMedia.data.model.MediaGroup
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.ui.components.DateGroupHeader
import com.ishaan.myMedia.ui.components.MediaGridRow
import com.ishaan.myMedia.ui.theme.AccentForeground
import com.ishaan.myMedia.ui.theme.PrimaryAccent
import com.ishaan.myMedia.ui.theme.SoftPurple
import com.ishaan.myMedia.viewmodel.GalleryUiState

enum class AlbumSortOrder { NAME_ASC, ITEM_COUNT_DESC, DATE_RECENT }

@Composable
fun AlbumsScreen(
    uiState: GalleryUiState,
    onAlbumClick: (Album) -> Unit,
    onCreateAlbum: (String, Album) -> Unit,
    sortOrder: AlbumSortOrder,
    onSortOrderChange: (AlbumSortOrder) -> Unit,
    pinnedAlbumIds: Set<String> = emptySet(),
    onTogglePin: (Album) -> Unit = {},
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
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

    val sorted = remember(uiState.albums, sortOrder, pinnedAlbumIds) {
        val comparator: Comparator<Album> = when (sortOrder) {
            AlbumSortOrder.NAME_ASC         -> compareBy { it.name.lowercase() }
            AlbumSortOrder.ITEM_COUNT_DESC  -> compareByDescending { it.mediaCount }
            AlbumSortOrder.DATE_RECENT      -> compareByDescending { it.mostRecentDate }
        }
        val pinned   = uiState.albums.filter { it.id in pinnedAlbumIds }.sortedWith(comparator)
        val unpinned = uiState.albums.filter { it.id !in pinnedAlbumIds }.sortedWith(comparator)
        pinned + unpinned
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AlbumSortBar(
                current  = sortOrder,
                onChange = onSortOrderChange,
            )
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted.size, key = { sorted[it].id }) { idx ->
                    val album = sorted[idx]
                    AlbumCard(
                        album       = album,
                        isPinned    = album.id in pinnedAlbumIds,
                        onClick     = { onAlbumClick(album) },
                        onLongClick = { onTogglePin(album) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick        = { showCreateDialog = true },
            containerColor = PrimaryAccent,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Rounded.CreateNewFolder, "Create album", tint = AccentForeground)
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            parents = uiState.destinationAlbums,
            onDismiss = { showCreateDialog = false },
            onCreate  = { name, parent ->
                onCreateAlbum(name, parent)
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
            TextButton(onClick = { expanded = true }) {
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
            .semantics {
                contentDescription = "${album.name}, ${album.mediaCount} items"
                stateDescription = if (isPinned) "Pinned" else "Not pinned"
            }
            .combinedClickable(role = Role.Button, onClickLabel = "Open album", onLongClickLabel = if (isPinned) "Unpin album" else "Pin album", onClick = onClick, onLongClick = onLongClick)
    ) {
        if (album.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
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
                    text     = album.name,
                    style    = MaterialTheme.typography.titleSmall,
                    color    = Color.White,
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

        Icon(
            imageVector        = Icons.Rounded.PushPin,
            contentDescription = null,
            tint               = pinTint,
            modifier           = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun CreateAlbumDialog(
    parents: List<Album>,
    onDismiss: () -> Unit,
    onCreate: (String, Album) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    var parentId by remember { mutableStateOf(parents.firstOrNull()?.id) }
    val parent = parents.firstOrNull { it.id == parentId }
    var expanded by remember { mutableStateOf(false) }
    val validName = trimmed.isNotEmpty() && trimmed !in setOf(".", "..") && '/' !in trimmed && '\\' !in trimmed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Album") },
        text = {
            Column {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Album name") },
                singleLine    = true,
            )
            Text("Create in", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Box {
                TextButton(onClick = { expanded = true }, enabled = parents.isNotEmpty()) {
                    Text(parent?.id ?: "Add a folder in Settings first")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    parents.forEach { destination -> DropdownMenuItem(
                        text = { Text(destination.id) },
                        onClick = { parentId = destination.id; expanded = false },
                    ) }
                }
            }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parent?.let { onCreate(trimmed, it) } },
                enabled = validName && parent != null,
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
    listState: LazyListState = rememberLazyListState(),
) {
    val scope = rememberCoroutineScope()
    val flatItems = groups.flatMap { it.items }

    BackHandler(enabled = selection.isActive) {
        onSelectionChange(SelectionState())
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    .pinchToAdjustColumns(columns, onColumnsChange),
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
