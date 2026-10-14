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

data class ViewerState(
    val items: List<MediaItem>,
    val index: Int,
)

@Composable
fun MainScreen(viewModel: GalleryViewModel) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    var activeTab   by remember { mutableStateOf(NavTab.MEDIA) }

    // Hoisted scroll state — survives viewer open/close
    val mediaListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()

    var selection by remember { mutableStateOf(SelectionState()) }
    var albumSelection by remember { mutableStateOf(SelectionState()) }

    var activeAlbum by remember { mutableStateOf<Album?>(null) }

    // Keep the album's position while the viewer is shown; reset for a different album.
    val albumListState = key(activeAlbum?.id) { rememberLazyListState() }

    // Viewer
    var viewerState by remember { mutableStateOf<ViewerState?>(null) }

    LaunchedEffect(uiState.allMedia) {
        val vs = viewerState ?: return@LaunchedEffect
        val newItems = uiState.allMedia
        if (newItems == vs.items) return@LaunchedEffect
        val currentId = vs.items.getOrNull(vs.index)?.id
        val newIndex = if (currentId != null) newItems.indexOfFirst { it.id == currentId } else -1
        if (newIndex < 0) {
            viewerState = null
        } else {
            viewerState = vs.copy(items = newItems, index = newIndex)
        }
    }

    var showRecycleBin   by remember { mutableStateOf(false) }
    var showMediaFolders by remember { mutableStateOf(false) }

    var itemToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var itemToMove   by remember { mutableStateOf<MediaItem?>(null) }
    var playingVideo by remember { mutableStateOf<MediaItem?>(null) }
    var editingItem  by remember { mutableStateOf<MediaItem?>(null) }
    var saveResult   by remember { mutableStateOf<String?>(null) }

    // Bulk action state
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkMoveSheet    by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(activeTab) {
        if (activeTab != NavTab.MEDIA) selection = SelectionState()
    }

    LaunchedEffect(activeAlbum) {
        if (activeAlbum == null) albumSelection = SelectionState()
    }

    BackHandler(enabled = selection.isActive || albumSelection.isActive || editingItem != null || itemToMove != null || playingVideo != null || viewerState != null || activeAlbum != null || showRecycleBin || showMediaFolders || activeTab != NavTab.MEDIA) {
        when {
            albumSelection.isActive -> albumSelection = SelectionState()
            selection.isActive  -> selection = SelectionState()
            editingItem != null -> editingItem = null
            itemToMove  != null -> itemToMove = null
            playingVideo != null -> playingVideo = null
            viewerState != null -> viewerState = null
            activeAlbum != null -> activeAlbum = null
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
            title = { Text("Move \${albumSelection.count} items to Recycle Bin?") },
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
                item      = proxyItem.copy(name = "\${albumSelection.count} items"),
                albums    = uiState.albums,
                onDismiss = { showAlbumBulkMoveSheet = false },
                onMove    = { dest ->
                    albumSelection.selectedIds.forEach { id ->
                        uiState.allMedia.firstOrNull { it.id == id }?.let { viewModel.moveMedia(it, dest) }
                    }
                    albumSelection = SelectionState()
                    showAlbumBulkMoveSheet = false
                },
                onCopy    = { dest ->
                    albumSelection.selectedIds.forEach { id ->
                        uiState.allMedia.firstOrNull { it.id == id }?.let { viewModel.copyMedia(it, dest) }
                    }
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
            albums    = uiState.albums,
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
                albums    = uiState.albums,
                onDismiss = { showBulkMoveSheet = false },
                onMove    = { dest ->
                    selection.selectedIds.forEach { id ->
                        uiState.allMedia.firstOrNull { it.id == id }?.let { viewModel.moveMedia(it, dest) }
                    }
                    selection = SelectionState()
                    showBulkMoveSheet = false
                },
                onCopy    = { dest ->
                    selection.selectedIds.forEach { id ->
                        uiState.allMedia.firstOrNull { it.id == id }?.let { viewModel.copyMedia(it, dest) }
                    }
                    selection = SelectionState()
                    showBulkMoveSheet = false
                },
            )
        }
    }

    // Save result snackbar
    saveResult?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            saveResult = null
        }
        Box(
            Modifier.fillMaxSize().padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
        }
    }

    // ── Full-screen overlays (return early) ───────────────────────────────

    editingItem?.let { item ->
        ImageEditorScreen(
            item   = item,
            onBack = { editingItem = null },
            onSave = { bitmap ->
                viewModel.saveEditedImage(item, bitmap) { uri ->
                    editingItem = null
                    saveResult = if (uri != null) "Saved to Pictures/myMedia" else "Save failed"
                }
            }
        )
        return
    }

    viewerState?.let { vs ->
        if (playingVideo != null) {
            VideoPlayerScreen(
                item   = playingVideo!!,
                onBack = { playingVideo = null }
            )
            return
        }

        MediaViewerScreen(
            items         = vs.items,
            initialIndex  = vs.index,
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
            onPlayVideo = { item -> playingVideo = item },
            onMove      = { item -> itemToMove = item },
            onEdit      = { item -> editingItem = item },
        )
        return
    }

    activeAlbum?.let { album ->
        val groups = viewModel.getGroupsForAlbum(album.id)
        AlbumDetailScreen(
            album             = album,
            listState         = albumListState,
            groups            = groups,
            onBack            = { activeAlbum = null },
            onMediaClick      = { item, items, idx ->
                viewModel.loadMetadataFor(item)
                viewerState = ViewerState(items, idx)
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
                    context.startActivity(Intent.createChooser(shareIntent, "Share \${uris.size} items"))
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
                        viewerState = ViewerState(uiState.allMedia, idx)
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
                    gridState       = albumsGridState,
                    uiState         = uiState,
                    onAlbumClick    = { activeAlbum = it },
                    pinnedAlbumIds  = uiState.pinnedAlbumIds,
                    onTogglePin     = { viewModel.togglePinAlbum(it.id) },
                )
                NavTab.FAVORITES -> FavoritesScreen(
                    listState       = favoritesListState,
                    uiState         = uiState,
                    onMediaClick    = { item, items, idx ->
                        viewModel.loadMetadataFor(item)
                        viewerState = ViewerState(items, idx)
                    },
                    columns         = uiState.gridColumns,
                    onColumnsChange = viewModel::updateGridColumns,
                )
                NavTab.SETTINGS -> SettingsScreen(
                    listState           = settingsListState,
                    uiState             = uiState,
                    onThemeChange       = viewModel::setTheme,
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
