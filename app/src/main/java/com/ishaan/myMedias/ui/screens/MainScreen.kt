package com.ishaan.myMedias.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ishaan.myMedias.data.model.Album
import com.ishaan.myMedias.data.model.MediaItem
import com.ishaan.myMedias.ui.components.MoveCopySheet
import com.ishaan.myMedias.ui.screens.MediaFoldersScreen
import com.ishaan.myMedias.viewmodel.GalleryViewModel

enum class NavTab { MEDIA, ALBUMS, FAVORITES, SETTINGS }

data class NavItem(val tab: NavTab, val label: String, val icon: ImageVector)

val navItems = listOf(
    NavItem(NavTab.MEDIA,     "Media",     Icons.Rounded.Photo),
    NavItem(NavTab.ALBUMS,    "Albums",    Icons.Rounded.PhotoLibrary),
    NavItem(NavTab.FAVORITES, "Favorites", Icons.Rounded.Favorite),
    NavItem(NavTab.SETTINGS,  "Settings",  Icons.Rounded.Settings),
)

/**
 * Viewer state — null means no viewer is open
 */
data class ViewerState(
    val items: List<MediaItem>,
    val index: Int,
)

@Composable
fun MainScreen(viewModel: GalleryViewModel) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    var activeTab   by remember { mutableStateOf(NavTab.MEDIA) }

    // Album detail navigation
    var activeAlbum by remember { mutableStateOf<Album?>(null) }

    // Viewer
    var viewerState by remember { mutableStateOf<ViewerState?>(null) }

    // Recycle bin navigation
    var showRecycleBin by remember { mutableStateOf(false) }

    // Media folders navigation
    var showMediaFolders by remember { mutableStateOf(false) }

    // Media Actions State
    var itemToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var itemToMove   by remember { mutableStateOf<MediaItem?>(null) }
    var playingVideo by remember { mutableStateOf<MediaItem?>(null) }
    var editingItem  by remember { mutableStateOf<MediaItem?>(null) }
    var saveResult   by remember { mutableStateOf<String?>(null) } // toast-like message
    val context = LocalContext.current

    // Handle system back button
    BackHandler(enabled = editingItem != null || itemToMove != null || playingVideo != null || viewerState != null || activeAlbum != null || showRecycleBin || showMediaFolders || activeTab != NavTab.MEDIA) {
        when {
            editingItem  != null -> editingItem = null
            itemToMove   != null -> itemToMove = null
            playingVideo != null -> playingVideo = null
            viewerState  != null -> viewerState = null
            activeAlbum  != null -> activeAlbum = null
            showRecycleBin      -> showRecycleBin = false
            showMediaFolders    -> showMediaFolders = false
            activeTab != NavTab.MEDIA -> activeTab = NavTab.MEDIA
        }
    }

    // Delete confirmation dialog
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
                        if (viewerState != null) {
                            // Close viewer after deletion
                            viewerState = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Bin")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Move/Copy bottom sheet
    if (itemToMove != null) {
        MoveCopySheet(
            item    = itemToMove!!,
            albums  = uiState.albums,
            onDismiss = { itemToMove = null },
            onMove  = { dest -> viewModel.moveMedia(itemToMove!!, dest) },
            onCopy  = { dest -> viewModel.copyMedia(itemToMove!!, dest) }
        )
    }

    // Save result snackbar
    saveResult?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            saveResult = null
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(modifier = Modifier.padding(16.dp)) {
                Text(msg)
            }
        }
    }

    // If editor is open, show it full-screen
    editingItem?.let { item ->
        ImageEditorScreen(
            item   = item,
            onBack = { editingItem = null },
            onSave = { bitmap ->
                viewModel.saveEditedImage(item, bitmap) { uri ->
                    editingItem = null
                    saveResult = if (uri != null) "Saved to Pictures/myMedias" else "Save failed"
                }
            }
        )
        return
    }

    // If viewer is open, show it full-screen
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
                // Reflect favorite change in viewer list
                viewerState = vs.copy(
                    items = vs.items.map {
                        if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
                    }
                )
            },
            onDelete = { item -> itemToDelete = item },
            onShare  = { item ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share with"))
            },
            onPlayVideo = { item -> playingVideo = item },
            onMove = { item -> itemToMove = item },
            onEdit = { item -> editingItem = item },
        )
        return
    }

    // Album detail
    activeAlbum?.let { album ->
        val groups = viewModel.getGroupsForAlbum(album.id)
        AlbumDetailScreen(
            album           = album,
            groups          = groups,
            onBack          = { activeAlbum = null },
            onMediaClick    = { item, items, idx ->
                viewModel.loadMetadataFor(item)
                viewerState = ViewerState(items, idx)
            },
            columns         = uiState.gridColumns,
            onColumnsChange = viewModel::updateGridColumns,
        )
        return
    }

    // Recycle bin screen
    if (showRecycleBin) {
        RecycleBinScreen(
            deletedItems        = uiState.deletedItems,
            onRestore           = viewModel::restoreDeleted,
            onPermanentlyDelete = viewModel::permanentlyDelete,
            onBack              = { showRecycleBin = false },
        )
        return
    }

    // Media folders screen
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

    Scaffold(
        bottomBar = {
            GalleryNavBar(
                activeTab = activeTab,
            ) { activeTab = it }
        },
        contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header with Page title and Sort action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (activeTab) {
                        NavTab.MEDIA -> "Media"
                        NavTab.ALBUMS -> "Albums"
                        NavTab.FAVORITES -> "Favorites"
                        NavTab.SETTINGS -> "Settings"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (activeTab == NavTab.MEDIA || activeTab == NavTab.FAVORITES) {
                    SortMenu(
                        currentOrder = uiState.sortOrder,
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
                )
                NavTab.ALBUMS -> AlbumsScreen(
                    uiState      = uiState,
                    onAlbumClick = { activeAlbum = it },
                )
                NavTab.FAVORITES -> FavoritesScreen(
                    uiState         = uiState,
                    onMediaClick    = { item, items, idx ->
                        viewModel.loadMetadataFor(item)
                        viewerState = ViewerState(items, idx)
                    },
                    columns         = uiState.gridColumns,
                    onColumnsChange = viewModel::updateGridColumns,
                )
                NavTab.SETTINGS -> SettingsScreen(
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
    currentOrder: com.ishaan.myMedias.data.model.SortOrder,
    onOrderChange: (com.ishaan.myMedias.data.model.SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        com.ishaan.myMedias.data.model.SortOrder.DATE_DESC to "Date (Newest)",
        com.ishaan.myMedias.data.model.SortOrder.DATE_ASC  to "Date (Oldest)",
        com.ishaan.myMedias.data.model.SortOrder.NAME_ASC  to "Name (A-Z)",
        com.ishaan.myMedias.data.model.SortOrder.SIZE_DESC to "Size (Largest)"
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