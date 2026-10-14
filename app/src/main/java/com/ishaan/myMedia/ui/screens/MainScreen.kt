package com.ishaan.myMedia.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ishaan.myMedia.data.model.Album
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.ui.components.MoveCopySheet
import com.ishaan.myMedia.viewmodel.GalleryViewModel

enum class NavTab { MEDIA, ALBUMS, FAVORITES, SETTINGS }

data class NavItem(val tab: NavTab, val label: String, val icon: ImageVector)

val navItems = listOf(
    NavItem(NavTab.MEDIA,     "Media",     Icons.Rounded.Photo),
    NavItem(NavTab.ALBUMS,    "Albums",    Icons.Rounded.PhotoLibrary),
    NavItem(NavTab.FAVORITES, "Favorites", Icons.Rounded.Favorite),
    NavItem(NavTab.SETTINGS,  "Settings",  Icons.Rounded.Settings),
)

enum class ViewerCollection { MEDIA, ALBUM, FAVORITES }

data class ViewerState(
    val items: List<MediaItem>,
    val index: Int,
    val collection: ViewerCollection = ViewerCollection.MEDIA,
    val albumId: String? = null,
    val pendingMediaId: Long? = null,
)

@Composable
fun MainScreen(viewModel: GalleryViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    Box(Modifier.fillMaxSize()) {
        MainScreenContent(viewModel, snackbarHostState)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 80.dp))
    }
}

@Composable
private fun MainScreenContent(viewModel: GalleryViewModel, snackbarHostState: SnackbarHostState) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.operationBusy) AlertDialog(
        onDismissRequest = {}, title = { Text("Organizing media") },
        text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(16.dp)); Text("Please wait…") } },
        confirmButton = {},
    )
    uiState.operationMessage?.let { message -> AlertDialog(
        onDismissRequest = viewModel::clearFeedback,
        title = { Text("Media operation") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = viewModel::clearFeedback) { Text("OK") } },
    ) }
    uiState.errorMessage?.let { message -> AlertDialog(
        onDismissRequest = viewModel::clearFeedback,
        title = { Text("Could not load media") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { viewModel.clearFeedback(); viewModel.loadMedia() }) { Text("Retry") } },
        dismissButton = { TextButton(onClick = viewModel::clearFeedback) { Text("Dismiss") } },
    ) }
    var activeTab   by rememberSaveable { mutableStateOf(NavTab.MEDIA) }
    var albumSortOrder by rememberSaveable { mutableStateOf(AlbumSortOrder.NAME_ASC) }

    // Hoisted scroll state — survives viewer open/close
    val mediaListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()

    val selectionSaver = Saver<SelectionState, LongArray>(save = { it.selectedIds.toLongArray() }, restore = { SelectionState(it.toSet()) })
    var selection by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf(SelectionState()) }
    var albumSelection by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf(SelectionState()) }

    var activeAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeAlbum = uiState.albums.firstOrNull { it.id == activeAlbumId }

    // Keep the album's position while the viewer is shown; reset for a different album.
    val albumListState = key(activeAlbumId) { rememberLazyListState() }

    // Viewer
    val viewerSaver = Saver<ViewerState?, List<String>>(
        save = { value -> value?.let { state ->
            (state.pendingMediaId ?: state.items.getOrNull(state.index)?.id)?.let { id ->
                listOf(state.collection.name, state.albumId.orEmpty(), id.toString())
            }
        } },
        restore = { saved -> ViewerState(emptyList(), 0, ViewerCollection.valueOf(saved[0]), saved[1].ifEmpty { null }, saved[2].toLong()) },
    )
    var viewerState by rememberSaveable(stateSaver = viewerSaver) { mutableStateOf<ViewerState?>(null) }

    LaunchedEffect(uiState.allMedia, uiState.favorites, uiState.isLoading) {
        if (uiState.isLoading) return@LaunchedEffect
        val vs = viewerState ?: return@LaunchedEffect
        val ordered = uiState.mediaGroups.flatMap { it.items }
        val favoriteIds = uiState.favorites.map { it.id }.toSet()
        val newItems = when (vs.collection) {
            ViewerCollection.MEDIA -> ordered
            ViewerCollection.ALBUM -> ordered.filter { it.albumId == vs.albumId }
            ViewerCollection.FAVORITES -> ordered.filter { it.id in favoriteIds }
        }
        if (newItems == vs.items) return@LaunchedEffect
        val currentId = vs.pendingMediaId ?: vs.items.getOrNull(vs.index)?.id
        val newIndex = if (currentId != null) newItems.indexOfFirst { it.id == currentId } else -1
        if (newIndex < 0) {
            viewerState = null
        } else {
            viewerState = vs.copy(items = newItems, index = newIndex, pendingMediaId = null)
        }
    }

    var showRecycleBin   by rememberSaveable { mutableStateOf(false) }
    var showMediaFolders by rememberSaveable { mutableStateOf(false) }

    var itemToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var itemToMove   by remember { mutableStateOf<MediaItem?>(null) }
    var playingVideoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    val playingVideo = uiState.allMedia.firstOrNull { it.id == playingVideoId }
    val editingItem = uiState.allMedia.firstOrNull { it.id == editingItemId }
    var saveResult   by remember { mutableStateOf<String?>(null) }

    // Bulk action state
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkMoveSheet    by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(activeTab) {
        if (activeTab != NavTab.MEDIA) selection = SelectionState()
    }

    LaunchedEffect(activeAlbumId) {
        if (activeAlbumId == null) albumSelection = SelectionState()
    }

    BackHandler(enabled = selection.isActive || albumSelection.isActive || editingItem != null || itemToMove != null || playingVideo != null || viewerState != null || activeAlbum != null || showRecycleBin || showMediaFolders || activeTab != NavTab.MEDIA) {
        when {
            albumSelection.isActive -> albumSelection = SelectionState()
            selection.isActive  -> selection = SelectionState()
            editingItem != null -> editingItemId = null
            itemToMove  != null -> itemToMove = null
            playingVideo != null -> playingVideoId = null
            viewerState != null -> viewerState = null
            activeAlbum != null -> activeAlbumId = null
            showRecycleBin      -> showRecycleBin = false
            showMediaFolders    -> showMediaFolders = false
            activeTab != NavTab.MEDIA -> activeTab = NavTab.MEDIA
        }
    }

    // ── Dialogs & sheets ──────────────────────────────────────────────────

    // Single-item delete
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Move to Recycle Bin?") },
            text = { Text("Items in the recycle bin will be deleted forever after 30 days.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedia(item)
                        itemToDelete = null
                        if (viewerState != null) viewerState = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Move to Bin") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    var showAlbumBulkDeleteDialog by remember { mutableStateOf(false) }
    var showAlbumBulkMoveSheet    by remember { mutableStateOf(false) }

    if (showAlbumBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showAlbumBulkDeleteDialog = false },
            title = { Text("Move ${albumSelection.count} items to Recycle Bin?") },
            text = { Text("They will be permanently deleted after 30 days.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMediaBulk(albumSelection.selectedIds)
                        albumSelection = SelectionState()
                        showAlbumBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Move to Bin") }
            },
            dismissButton = {
                TextButton(onClick = { showAlbumBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAlbumBulkMoveSheet) {
        val proxyItem = uiState.allMedia.firstOrNull { it.id in albumSelection.selectedIds }
        if (proxyItem != null) {
            MoveCopySheet(
                item      = proxyItem.copy(name = "${albumSelection.count} items"),
                albums    = uiState.destinationAlbums,
                onDismiss = { showAlbumBulkMoveSheet = false },
                onMove    = { dest ->
                    viewModel.organizeMedia(uiState.allMedia.filter { it.id in albumSelection.selectedIds }, dest, copy = false)
                    albumSelection = SelectionState()
                    showAlbumBulkMoveSheet = false
                },
                onCopy    = { dest ->
                    viewModel.organizeMedia(uiState.allMedia.filter { it.id in albumSelection.selectedIds }, dest, copy = true)
                    albumSelection = SelectionState()
                    showAlbumBulkMoveSheet = false
                },
            )
        }
    }

    // Bulk delete confirmation
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Move ${selection.count} items to Recycle Bin?") },
            text = { Text("They will be permanently deleted after 30 days.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMediaBulk(selection.selectedIds)
                        selection = SelectionState()
                        showBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Move to Bin") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Single-item move/copy sheet
    if (itemToMove != null) {
        MoveCopySheet(
            item      = itemToMove!!,
            albums    = uiState.destinationAlbums,
            onDismiss = { itemToMove = null },
            onMove    = { dest -> viewModel.moveMedia(itemToMove!!, dest) },
            onCopy    = { dest -> viewModel.copyMedia(itemToMove!!, dest) }
        )
    }

    // Bulk move sheet — reuse MoveCopySheet with a proxy item for the label
    if (showBulkMoveSheet) {
        val proxyItem = uiState.allMedia.firstOrNull { it.id in selection.selectedIds }
        if (proxyItem != null) {
            MoveCopySheet(
                item      = proxyItem.copy(name = "${selection.count} items"),
                albums    = uiState.destinationAlbums,
                onDismiss = { showBulkMoveSheet = false },
                onMove    = { dest ->
                    viewModel.organizeMedia(uiState.allMedia.filter { it.id in selection.selectedIds }, dest, copy = false)
                    selection = SelectionState()
                    showBulkMoveSheet = false
                },
                onCopy    = { dest ->
                    viewModel.organizeMedia(uiState.allMedia.filter { it.id in selection.selectedIds }, dest, copy = true)
                    selection = SelectionState()
                    showBulkMoveSheet = false
                },
            )
        }
    }

    // Save result snackbar
    saveResult?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            saveResult = null
        }
    }

    // ── Full-screen overlays (return early) ───────────────────────────────

    editingItem?.let { item ->
        ImageEditorScreen(
            item   = item,
            onBack = { editingItemId = null },
            onSave = { bitmap, completed ->
                viewModel.saveEditedImage(item, bitmap) { uri ->
                    completed(uri != null)
                    if (uri != null) {
                        editingItemId = null
                        saveResult = "Saved to Pictures/myMedia"
                    }
                }
            }
        )
        return
    }

    viewerState?.let { vs ->
        if (playingVideo != null) {
            VideoPlayerScreen(
                item   = playingVideo!!,
                onBack = { playingVideoId = null }
            )
            return
        }

        MediaViewerScreen(
            items         = vs.items,
            initialIndex  = vs.index,
            onCurrentIndexChange = { index -> viewerState = viewerState?.copy(index = index) },
            onBack        = { viewerState = null },
            onToggleFavorite = { item ->
                viewModel.toggleFavorite(item)
                viewerState = vs.copy(
                    items = vs.items.map {
                        if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
                    }
                )
            },
            onDelete     = { item -> itemToDelete = item },
            onShare      = { item ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share with"))
            },
            onPlayVideo = { item -> playingVideoId = item.id },
            onMove      = { item -> itemToMove = item },
            onEdit      = { item -> editingItemId = item.id },
        )
        return
    }

    activeAlbum?.let { album ->
        val groups = viewModel.getGroupsForAlbum(album.id)
        AlbumDetailScreen(
            album             = album,
            listState         = albumListState,
            groups            = groups,
            onBack            = { activeAlbumId = null },
            onMediaClick      = { item, items, idx ->
                viewModel.loadMetadataFor(item)
                viewerState = ViewerState(items, idx, ViewerCollection.ALBUM, album.id)
            },
            columns           = uiState.gridColumns,
            onColumnsChange   = viewModel::updateGridColumns,
            selection         = albumSelection,
            onSelectionChange = { albumSelection = it },
            onBulkDelete      = { showAlbumBulkDeleteDialog = true },
            onBulkShare       = {
                val uris = viewModel.getUrisForIds(albumSelection.selectedIds)
                if (uris.isNotEmpty()) {
                    val mimeType = if (uris.size == 1)
                        uiState.allMedia.firstOrNull { it.id in albumSelection.selectedIds }?.mimeType ?: "*/*"
                    else "*/*"
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = mimeType
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} items"))
                    albumSelection = SelectionState()
                }
            },
            onBulkMove        = { showAlbumBulkMoveSheet = true },
        )
        return
    }

    if (showRecycleBin) {
        RecycleBinScreen(
            deletedItems        = uiState.deletedItems,
            onRestore           = viewModel::restoreDeleted,
            onPermanentlyDelete = viewModel::permanentlyDelete,
            onBack              = { showRecycleBin = false },
        )
        return
    }

    if (showMediaFolders) {
        MediaFoldersScreen(
            folderTree         = uiState.folderTree,
            onAddFolder        = viewModel::addFolder,
            onToggleVisibility = viewModel::toggleFolderVisibility,
            onRemoveRoot       = viewModel::removeChosenFolder,
            onBack             = { showMediaFolders = false }
        )
        return
    }

    // ── Main scaffold ─────────────────────────────────────────────────────

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !selection.isActive,
                enter = slideInVertically(initialOffsetY = { it }),
                exit  = slideOutVertically(targetOffsetY = { it }),
            ) {
                GalleryNavBar(activeTab = activeTab) { activeTab = it }
            }
        },
        contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (activeTab) {
                        NavTab.MEDIA     -> "Media"
                        NavTab.ALBUMS    -> "Albums"
                        NavTab.FAVORITES -> "Favorites"
                        NavTab.SETTINGS  -> "Settings"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (activeTab == NavTab.MEDIA || activeTab == NavTab.FAVORITES) {
                    SortMenu(
                        currentOrder  = uiState.sortOrder,
                        onOrderChange = viewModel::updateSortOrder
                    )
                }
            }

            when (activeTab) {
                NavTab.MEDIA -> MediaScreen(
                    uiState         = uiState,
                    onMediaClick    = { item, idx ->
                        viewModel.loadMetadataFor(item)
                        viewerState = ViewerState(uiState.mediaGroups.flatMap { it.items }, idx)
                    },
                    columns         = uiState.gridColumns,
                    onColumnsChange = viewModel::updateGridColumns,
                    listState       = mediaListState,
                    selection       = selection,
                    onSelectionChange = { selection = it },
                    onBulkDelete    = { showBulkDeleteDialog = true },
                    onBulkShare     = {
                        val uris = viewModel.getUrisForIds(selection.selectedIds)
                        if (uris.isNotEmpty()) {
                            val mimeType = if (uris.size == 1)
                                uiState.allMedia.firstOrNull { it.id in selection.selectedIds }?.mimeType ?: "*/*"
                            else "*/*"
                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = mimeType
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} items"))
                            selection = SelectionState()
                        }
                    },
                    onBulkMove      = { showBulkMoveSheet = true },
                )
                NavTab.ALBUMS -> AlbumsScreen(
                    sortOrder = albumSortOrder,
                    onSortOrderChange = { albumSortOrder = it },
                    onCreateAlbum = viewModel::createAlbum,
                    gridState       = albumsGridState,
                    uiState         = uiState,
                    onAlbumClick    = { activeAlbumId = it.id },
                    pinnedAlbumIds  = uiState.pinnedAlbumIds,
                    onTogglePin     = { viewModel.togglePinAlbum(it.id) },
                )
                NavTab.FAVORITES -> FavoritesScreen(
                    listState       = favoritesListState,
                    uiState         = uiState,
                    onMediaClick    = { item, items, idx ->
                        viewModel.loadMetadataFor(item)
                        viewerState = ViewerState(items, idx, ViewerCollection.FAVORITES)
                    },
                    columns         = uiState.gridColumns,
                    onColumnsChange = viewModel::updateGridColumns,
                )
                NavTab.SETTINGS -> SettingsScreen(
                    listState           = settingsListState,
                    uiState             = uiState,
                    onThemeChange       = viewModel::setTheme,
                    onAccentColorChange = viewModel::setAccentColor,
                    onRestore           = viewModel::restoreDeleted,
                    onPermanentlyDelete = viewModel::permanentlyDelete,
                    onSavePin           = viewModel::savePin,
                    onClearPin          = viewModel::clearPin,
                    onSetBiometrics     = viewModel::setBiometricsEnabled,
                    onSetInstantLock    = viewModel::setInstantLockEnabled,
                    onOpenRecycleBin    = { showRecycleBin = true },
                    onOpenMediaFolders  = { showMediaFolders = true },
                )
            }
        }
    }
}

@Composable
fun SortMenu(
    currentOrder: com.ishaan.myMedia.data.model.SortOrder,
    onOrderChange: (com.ishaan.myMedia.data.model.SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        com.ishaan.myMedia.data.model.SortOrder.DATE_DESC to "Date (Newest)",
        com.ishaan.myMedia.data.model.SortOrder.DATE_ASC  to "Date (Oldest)",
        com.ishaan.myMedia.data.model.SortOrder.NAME_ASC  to "Name (A-Z)",
        com.ishaan.myMedia.data.model.SortOrder.SIZE_DESC to "Size (Largest)"
    )

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.Sort, contentDescription = "Sort")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (order, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onOrderChange(order)
                        expanded = false
                    },
                    leadingIcon = {
                        if (currentOrder == order) {
                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GalleryNavBar(activeTab: NavTab, onTabChange: (NavTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = activeTab == item.tab,
                onClick  = { onTabChange(item.tab) },
                icon     = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label    = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
