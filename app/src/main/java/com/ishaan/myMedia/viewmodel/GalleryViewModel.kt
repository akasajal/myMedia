package com.ishaan.myMedia.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ishaan.myMedia.data.model.*
import com.ishaan.myMedia.data.repository.MediaRepository
import com.ishaan.myMedia.data.repository.PrivacyRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GalleryUiState(
    val isLoading: Boolean = true,
    val allMedia: List<MediaItem> = emptyList(),      // visibility-filtered, used for display
    val rawMedia: List<MediaItem> = emptyList(),       // unfiltered, used for re-applying visibility
    val mediaGroups: List<MediaGroup> = emptyList(),
    val albums: List<Album> = emptyList(),
    val destinationAlbums: List<Album> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val deletedItems: List<DeletedItem> = emptyList(),
    val chosenFolders: Map<String, Boolean> = emptyMap(),   // path -> isVisible
    val folderTree: List<FolderNode> = emptyList(),
    val allFolderPaths: Set<String> = emptySet(),
    val theme: AppTheme = AppTheme.SYSTEM,
    val accentColor: AccentColor = AccentColor.PURPLE,
    val gridColumns: Int = 3,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val totalStorageUsage: Long = 0L,
    val errorMessage: String? = null,
    val operationBusy: Boolean = false,
    val operationMessage: String? = null,
    val privacyPrefs: PrivacyPrefs = PrivacyPrefs(),
    val isLocked: Boolean = false,
    val pinnedAlbumIds: Set<String> = emptySet(),
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo        = MediaRepository(app)
    private val privacyRepo = PrivacyRepository(app)
    private val operationMutex = Mutex()

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _viewerIndex = MutableStateFlow(-1)
    val viewerIndex: StateFlow<Int> = _viewerIndex.asStateFlow()

    private val _theme = MutableStateFlow(repo.getTheme())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    init {
        val priv = privacyRepo.getPrivacyPrefs()
        // Build folder visibility map
        val folders = repo.getSafUris()
            .mapNotNull { repo.safUriToDisplayPath(Uri.parse(it)) }
            .associateWith { repo.isFolderVisible(it) }

        _uiState.update { it.copy(
            privacyPrefs = priv,
            theme = _theme.value,
            accentColor = repo.getAccentColor(),
            isLocked = priv.pinEnabled,
            chosenFolders = folders,
            folderTree = repo.getFolderTree(),
            gridColumns = repo.getGridColumns(),
            sortOrder = repo.getSortOrder()
        ) }
        loadPinnedAlbums()
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val rawMedia = repo.loadAllMedia()             // unfiltered — for folder tree + re-applying visibility
                val allFolderPaths = rawMedia.map { it.albumId }.toSet()
                val media   = rawMedia.filter { repo.isFolderVisible(it.albumId) }  // for display
                val groups  = repo.groupByDate(media)
                val albums  = repo.buildAlbums(media)
                val destinations = repo.buildDestinationAlbums(media)
                val favs    = media.filter { repo.isFavorite(it.id) }
                val deleted = repo.getDeletedItems()
                _uiState.update {
                    it.copy(
                        isLoading      = false,
                        rawMedia       = rawMedia,
                        allMedia       = media,
                        mediaGroups    = groups,
                        albums         = albums,
                        destinationAlbums = destinations,
                        favorites      = favs,
                        deletedItems   = deleted,
                        totalStorageUsage = repo.calculateStorageUsage(media),
                        allFolderPaths = allFolderPaths,
                        folderTree     = repo.getFolderTree(allFolderPaths),
                    )
                }

                // Trigger background refresh for SAF
                refreshSafInBackground()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun refreshSafInBackground() {
        viewModelScope.launch {
            try {
                val updatedSaf = repo.refreshSafMedia()
                // Use the same file identity rules as the initial load, not source-specific IDs.
                val rawMedia = repo.mergeMedia(_uiState.value.rawMedia + updatedSaf)
                val media = rawMedia.filter { repo.isFolderVisible(it.albumId) }
                val groups = repo.groupByDate(media)
                val albums = repo.buildAlbums(media)
                val destinations = repo.buildDestinationAlbums(media)
                val allFolderPaths = rawMedia.map { it.albumId }.toSet()
                _uiState.update { it.copy(
                    rawMedia = rawMedia,
                    allMedia = media,
                    mediaGroups = groups,
                    albums = albums,
                    destinationAlbums = destinations,
                    favorites = media.filter { item -> item.isFavorite },
                    totalStorageUsage = repo.calculateStorageUsage(media),
                    allFolderPaths = allFolderPaths,
                    folderTree = repo.getFolderTree(allFolderPaths),
                ) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Folder management ─────────────────────────────────────────────────

    fun addFolder(treeUri: Uri) {
        repo.addSafUri(treeUri)
        refreshFolders()
        viewModelScope.launch {
            repo.refreshSafMedia()  // populate the SAF cache for the new folder first
            loadMedia()             // then load — cache is warm, media appears immediately
        }
    }

    fun removeChosenFolder(displayPath: String) {
        repo.removeSafUri(displayPath)
        refreshFolders()
        loadMedia()
    }

    fun toggleFolderVisibility(path: String) {
        val current = repo.isFolderVisible(path)
        repo.setFolderVisible(path, !current)

        // Rebuild tree immediately from already-known paths — sync, no disk read.
        val folders = repo.getSafUris()
            .mapNotNull { repo.safUriToDisplayPath(Uri.parse(it)) }
            .associateWith { repo.isFolderVisible(it) }
        val newTree = repo.getFolderTree(_uiState.value.allFolderPaths)

        // Update tree + folder map instantly so the icon flips on the same frame.
        _uiState.update { it.copy(chosenFolders = folders, folderTree = newTree) }

        // buildAlbums is suspend, so refilter media in a coroutine.
        viewModelScope.launch {
            val visibleMedia = _uiState.value.rawMedia.filter { repo.isFolderVisible(it.albumId) }
            val groups = repo.groupByDate(visibleMedia)
            val albums = repo.buildAlbums(visibleMedia)
            val destinations = repo.buildDestinationAlbums(visibleMedia)
            _uiState.update {
                it.copy(
                    allMedia    = visibleMedia,  // Bug 1 fix: keep allMedia in sync with visibility
                    mediaGroups = groups,
                    albums      = albums,
                    destinationAlbums = destinations,
                    favorites = visibleMedia.filter { it.isFavorite },
                    totalStorageUsage = repo.calculateStorageUsage(visibleMedia),
                )
            }
        }
    }

    private fun refreshFolders() {
        val folders = repo.getSafUris()
            .mapNotNull { repo.safUriToDisplayPath(Uri.parse(it)) }
            .associateWith { repo.isFolderVisible(it) }
        // Use allFolderPaths (unfiltered by visibility) so hidden subfolders
        // remain in the tree with a VisibilityOff icon — no restart needed.
        val foldersWithMedia = _uiState.value.allFolderPaths
        _uiState.update { it.copy(chosenFolders = folders, folderTree = repo.getFolderTree(foldersWithMedia)) }
    }

    // ── Lock / unlock ─────────────────────────────────────────────────────

    fun unlock() { _uiState.update { it.copy(isLocked = false) } }

    fun lock() {
        if (_uiState.value.privacyPrefs.pinEnabled) {
            _uiState.update { it.copy(isLocked = true) }
        }
    }

    fun checkPin(input: String): Boolean = privacyRepo.checkPin(input)

    fun savePin(pin: String) {
        privacyRepo.savePin(pin)
        _uiState.update { it.copy(privacyPrefs = privacyRepo.getPrivacyPrefs(), isLocked = true) }
    }

    fun clearPin() {
        privacyRepo.clearPin()
        _uiState.update { it.copy(privacyPrefs = privacyRepo.getPrivacyPrefs()) }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        privacyRepo.setBiometricsEnabled(enabled)
        _uiState.update { it.copy(privacyPrefs = privacyRepo.getPrivacyPrefs()) }
    }

    fun setInstantLockEnabled(enabled: Boolean) {
        privacyRepo.setInstantLockEnabled(enabled)
        _uiState.update { it.copy(privacyPrefs = privacyRepo.getPrivacyPrefs()) }
    }

    // ── Media actions ─────────────────────────────────────────────────────

    fun loadMetadataFor(item: MediaItem) {
        viewModelScope.launch {
            val updated = repo.loadMetadata(item)
            if (updated != item) {
                val currentMedia = _uiState.value.allMedia.map {
                    if (it.id == item.id) updated else it
                }
                _uiState.update { state -> state.copy(
                    allMedia = currentMedia,
                    rawMedia = state.rawMedia.map { if (it.id == item.id) updated else it },
                    mediaGroups = repo.groupByDate(currentMedia),
                    favorites = currentMedia.filter { it.isFavorite },
                ) }
            }
        }
    }

    fun setViewerIndex(index: Int) { _viewerIndex.value = index }

    fun moveMedia(item: MediaItem, destination: Album) {
        organizeMedia(listOf(item), destination, copy = false)
    }

    fun copyMedia(item: MediaItem, destination: Album) {
        organizeMedia(listOf(item), destination, copy = true)
    }

    fun clearFeedback() { _uiState.update { it.copy(errorMessage = null, operationMessage = null) } }

    fun organizeMedia(items: List<MediaItem>, destination: Album, copy: Boolean) {
        viewModelScope.launch {
            operationMutex.withLock {
                _uiState.update { it.copy(operationBusy = true, operationMessage = null) }
                try {
                    val targetUri = requireNotNull(destination.folderUri) { "Choose a destination folder with write access" }
                    var completed = 0
                    items.forEach { item ->
                        if (if (copy) repo.copyMedia(item, targetUri) else repo.moveMedia(item, targetUri)) completed++
                    }
                    val action = if (copy) "Copied" else "Moved"
                    val failed = items.size - completed
                    _uiState.update { it.copy(operationMessage = "$action $completed of ${items.size} items." +
                        if (failed > 0) " $failed failed. Check folder access and try again." else "") }
                    if (completed > 0) loadMedia()
                } catch (e: Exception) {
                    _uiState.update { it.copy(operationMessage = e.message ?: "Operation failed. Check folder access and try again.") }
                } finally {
                    _uiState.update { it.copy(operationBusy = false) }
                }
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        repo.toggleFavorite(item.id)
        val updated = _uiState.value.allMedia.map {
            if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
        }
        val updatedRaw = _uiState.value.rawMedia.map {
            if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
        }
        _uiState.update { it.copy(rawMedia = updatedRaw, allMedia = updated, mediaGroups = repo.groupByDate(updated), favorites = updated.filter { m -> m.isFavorite }) }
    }

    fun deleteMedia(item: MediaItem) {
        repo.deleteMedia(item)
        val updated = _uiState.value.allMedia.filter { it.id != item.id }
        val updatedGroups = _uiState.value.mediaGroups.mapNotNull { group ->
            val filtered = group.items.filter { it.id != item.id }
            if (filtered.isEmpty()) null else group.copy(items = filtered)
        }
        _uiState.update {
            it.copy(
                rawMedia     = it.rawMedia.filter { m -> m.id != item.id },  // Bug 6 fix: remove from rawMedia so it doesn't resurface on visibility toggle
                allMedia     = updated,
                mediaGroups  = updatedGroups,
                favorites    = it.favorites.filter { m -> m.id != item.id },
                deletedItems = repo.getDeletedItems(),
            )
        }
    }

    fun restoreDeleted(item: DeletedItem) {
        repo.restoreDeleted(item)
        _uiState.update { it.copy(deletedItems = repo.getDeletedItems()) }
        loadMedia()
    }

    fun permanentlyDelete(item: DeletedItem) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.permanentlyDelete(item) }
                _uiState.update { it.copy(deletedItems = repo.getDeletedItems(), operationMessage = "Deleted ${item.name}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(operationMessage = "Could not delete ${item.name}. Check folder access and try again.") }
            }
        }
    }

    fun saveEditedImage(original: MediaItem, bitmap: Bitmap, onResult: (Uri?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var pendingUri: Uri? = null
            try {
                val baseName = original.name.substringBeforeLast(".")
                val displayName = "${baseName}_edited_${System.currentTimeMillis()}.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/myMedia")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = getApplication<Application>().contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                pendingUri = uri

                if (uri != null) {
                    requireNotNull(resolver.openOutputStream(uri)) { "Could not open image for writing" }.use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "Could not write image" }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(uri)
                }
                if (uri != null) loadMedia()
            } catch (e: Exception) {
                e.printStackTrace()
                pendingUri?.let { uri -> runCatching { getApplication<Application>().contentResolver.delete(uri, null, null) } }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        repo.setTheme(theme)
        _theme.value = theme
        _uiState.update { it.copy(theme = theme) }
    }

    fun setAccentColor(accent: AccentColor) {
        repo.setAccentColor(accent)
        _uiState.update { it.copy(accentColor = accent) }
    }

    fun updateGridColumns(count: Int) {
        repo.setGridColumns(count)
        _uiState.update { it.copy(gridColumns = count) }
    }

    fun updateSortOrder(order: SortOrder) {
        repo.setSortOrder(order)
        _uiState.update { it.copy(sortOrder = order) }
        loadMedia()
    }

    // ── Bulk actions ──────────────────────────────────────────────────────

    fun deleteMediaBulk(ids: Set<Long>) {
        ids.forEach { id ->
            val item = _uiState.value.allMedia.firstOrNull { it.id == id } ?: return@forEach
            repo.deleteMedia(item)
        }
        val updated      = _uiState.value.allMedia.filter { it.id !in ids }
        val updatedRaw   = _uiState.value.rawMedia.filter { it.id !in ids }
        val updatedGroups = _uiState.value.mediaGroups.mapNotNull { group ->
            val filtered = group.items.filter { it.id !in ids }
            if (filtered.isEmpty()) null else group.copy(items = filtered)
        }
        _uiState.update {
            it.copy(
                allMedia     = updated,
                rawMedia     = updatedRaw,
                mediaGroups  = updatedGroups,
                favorites    = it.favorites.filter { m -> m.id !in ids },
                deletedItems = repo.getDeletedItems(),
            )
        }
    }

    /** Returns the list of URIs for the selected items so MainScreen can fire an ACTION_SEND_MULTIPLE. */
    fun getUrisForIds(ids: Set<Long>): List<android.net.Uri> =
        _uiState.value.allMedia.filter { it.id in ids }.map { it.uri }

    // ── Album management ──────────────────────────────────────────────────

    fun createAlbum(name: String, parent: Album) {
        viewModelScope.launch {
            try {
                repo.createAlbum(name, parent)
                _uiState.update { it.copy(operationMessage = "Folder created. Add photos or videos to show it in Albums.") }
                loadMedia()
            } catch (e: Exception) {
                _uiState.update { it.copy(operationMessage = e.message ?: "Could not create album. Check folder access and try again.") }
            }
        }
    }

    /** Pins or unpins an album (persisted in prefs). */
    fun togglePinAlbum(albumId: String) {
        val current = _uiState.value.pinnedAlbumIds.toMutableSet()
        if (albumId in current) current.remove(albumId) else current.add(albumId)
        val prefs = getApplication<android.app.Application>()
            .getSharedPreferences("gallery_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("pinned_albums", current).apply()
        _uiState.update { it.copy(pinnedAlbumIds = current) }
    }

    private fun loadPinnedAlbums() {
        val prefs = getApplication<android.app.Application>()
            .getSharedPreferences("gallery_prefs", android.content.Context.MODE_PRIVATE)
        val pinned = prefs.getStringSet("pinned_albums", emptySet()) ?: emptySet()
        _uiState.update { it.copy(pinnedAlbumIds = pinned) }
    }

    fun getMediaForAlbum(albumId: String): List<MediaItem> {
        val filtered = _uiState.value.allMedia.filter { it.albumId == albumId }
        return repo.sortItems(filtered, _uiState.value.sortOrder)
    }

    fun getGroupsForAlbum(albumId: String): List<MediaGroup> =
        repo.groupByDate(getMediaForAlbum(albumId))
}
