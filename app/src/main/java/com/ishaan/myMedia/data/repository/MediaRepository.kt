package com.ishaan.myMedia.data.repository

import android.content.ContentUris
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.ishaan.myMedia.data.model.Album
import com.ishaan.myMedia.data.model.AccentColor
import com.ishaan.myMedia.data.model.AppTheme
import com.ishaan.myMedia.data.model.DeletedItem
import com.ishaan.myMedia.data.model.MediaGroup
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.data.model.MediaType
import com.ishaan.myMedia.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MediaRepository(private val context: Context) {

    // ── Favorites (persisted via SharedPreferences) ───────────────────────
    private val prefs = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)

    fun getTheme(): AppTheme = AppTheme.entries.firstOrNull {
        it.name == prefs.getString("theme", null)
    } ?: AppTheme.SYSTEM

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("theme", theme.name).apply()
    }

    fun getAccentColor(): AccentColor = AccentColor.entries.firstOrNull {
        it.name == prefs.getString("accent_color", null)
    } ?: AccentColor.PURPLE

    fun setAccentColor(accent: AccentColor) {
        prefs.edit().putString("accent_color", accent.name).apply()
    }

    private val _favoriteIds = MutableStateFlow(
        prefs.getStringSet("favorites", emptySet())!!.map { it.toLong() }.toMutableSet()
    )

    fun isFavorite(id: Long) = _favoriteIds.value.contains(id)

    fun toggleFavorite(id: Long) {
        val set = _favoriteIds.value.toMutableSet()
        if (set.contains(id)) set.remove(id) else set.add(id)
        _favoriteIds.value = set
        prefs.edit().putStringSet("favorites", set.map { it.toString() }.toSet()).apply()
    }

    // ── Grid columns (persisted) ──────────────────────────────────────────
    fun getGridColumns(): Int = prefs.getInt("grid_columns", 4)

    fun setGridColumns(count: Int) {
        prefs.edit().putInt("grid_columns", count).apply()
    }

    // ── Sort order (persisted) ────────────────────────────────────────────
    fun getSortOrder(): SortOrder {
        val name = prefs.getString("sort_order", SortOrder.DATE_DESC.name) ?: SortOrder.DATE_DESC.name
        return try { SortOrder.valueOf(name) } catch (e: Exception) { SortOrder.DATE_DESC }
    }

    fun setSortOrder(order: SortOrder) {
        prefs.edit().putString("sort_order", order.name).apply()
    }

    // ── Folder visibility (persisted) ────────────────────────────────────
    fun isFolderVisible(path: String): Boolean {
        // If the path itself is hidden, return false
        if (!prefs.getBoolean("folder_visible_$path", true)) return false

        // Also check if any parent directory that was explicitly added is hidden
        val safUris = getSafUris()
        val rootPaths = safUris.mapNotNull { safUriToDisplayPath(Uri.parse(it)) }

        for (root in rootPaths) {
            // Check if path is the root itself or a sub-folder of the root
            if (path == root || path.startsWith("$root/")) {
                if (!prefs.getBoolean("folder_visible_$root", true)) return false
            }
        }
        return true
    }

    fun setFolderVisible(path: String, visible: Boolean) {
        prefs.edit().putBoolean("folder_visible_$path", visible).apply()
    }

    // ── Chosen folders (persisted) ────────────────────────────────────────
    fun getSafUris(): Set<String> =
        prefs.getStringSet("saf_uris", emptySet()) ?: emptySet()

    fun addSafUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val set = getSafUris().toMutableSet()
        set.add(uri.toString())
        prefs.edit().putStringSet("saf_uris", set).apply()
    }

    fun removeSafUri(displayPath: String) {
        // We need to find the URI that matches this display path
        val uris = getSafUris()
        val toRemove = uris.firstOrNull { safUriToDisplayPath(Uri.parse(it)) == displayPath }
        if (toRemove != null) {
            val uri = Uri.parse(toRemove)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if already released
            }

            val set = uris.toMutableSet()
            set.remove(toRemove)
            prefs.edit().putStringSet("saf_uris", set).apply()
        }
    }

    fun safUriToDisplayPath(uri: Uri): String? {
        return try {
            val decoded = java.net.URLDecoder.decode(uri.toString(), "UTF-8")
            val treeId  = decoded.substringAfterLast("/tree/", "").substringBefore(":")
            val subPath = decoded.substringAfterLast(":", "")
            val root    = if (treeId.equals("primary", ignoreCase = true))
                android.os.Environment.getExternalStorageDirectory().absolutePath
            else "/storage/$treeId"
            if (subPath.isBlank()) root else "$root/$subPath"
        } catch (e: Exception) {
            null
        }
    }

    fun getChosenFolders(): Set<String> =
        getSafUris().mapNotNull { safUriToDisplayPath(Uri.parse(it)) }.toSet()

    // ── All discovered folders ──────────────────────────────────────────
    private fun getAllDiscoveredFolders(): Set<String> =
        prefs.getStringSet("discovered_folders", emptySet()) ?: emptySet()

    private fun addDiscoveredFolder(path: String) {
        val set = getAllDiscoveredFolders().toMutableSet()
        if (set.add(path)) {
            prefs.edit().putStringSet("discovered_folders", set).apply()
        }
    }

    fun getFolderTree(foldersWithMedia: Set<String> = emptySet()): List<com.ishaan.myMedia.data.model.FolderNode> {
        val rootPaths = getChosenFolders()
        val allFolders = getAllDiscoveredFolders()

        return rootPaths.map { root ->
            buildFolderNode(root, allFolders, foldersWithMedia)
        }.sortedBy { it.name }
    }

    private fun buildFolderNode(
        path: String,
        allFolders: Set<String>,
        foldersWithMedia: Set<String>,
    ): com.ishaan.myMedia.data.model.FolderNode {
        val name = path.substringAfterLast('/')
        val displayName = if (name.isEmpty()) "Root" else name

        val subFolders = allFolders
            .filter { it.startsWith("$path/") && it.substringAfter("$path/").count { c -> c == '/' } == 0 }
            // Only include subfolders that have media directly or in any descendant
            .filter { sub -> foldersWithMedia.isEmpty() || foldersWithMedia.any { it == sub || it.startsWith("$sub/") } }
            .map { buildFolderNode(it, allFolders, foldersWithMedia) }
            .sortedBy { it.name }

        return com.ishaan.myMedia.data.model.FolderNode(
            name = displayName,
            path = path,
            isVisible = isFolderVisible(path),
            subFolders = subFolders,
        )
    }

    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val safUris = getSafUris()
        val chosenFolders = getChosenFolders()
        if (safUris.isEmpty()) return@withContext emptyList()

        val items = mutableListOf<MediaItem>()
        // 1. Query MediaStore for standard folders
        items += queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, chosenFolders)
        items += queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, chosenFolders)

        // 2. Add cached SAF items immediately
        items += getSafCache()

        // 3. Deduplicate and remove deleted — do NOT filter by visibility here.
        //    The ViewModel applies visibility so the folder tree always knows about
        //    hidden folders (they stay in the tree with a VisibilityOff icon).
        mergeMedia(items)
    }

    /** MediaStore and SAF can refer to the same file with different URIs and IDs. */
    fun mergeMedia(items: List<MediaItem>): List<MediaItem> {
        val deleted = getDeletedItems()
        val deletedIds = deleted.map { it.id }.toSet()
        val deletedFiles = deleted.map { mediaIdentity(it.uri, it.originalAlbumId, it.name) }.toSet()
        val favoriteIds = _favoriteIds.value.toMutableSet()
        val merged = items.groupBy { mediaIdentity(it.uri, it.albumId, it.name) }
            .mapNotNull { (identity, aliases) ->
                if (identity in deletedFiles || aliases.any { it.id in deletedIds }) return@mapNotNull null
                // Keep indexed metadata (dimensions, date taken, video duration) when available.
                val preferred = aliases.firstOrNull { it.uri.authority == "media" } ?: aliases.first()
                val favorite = aliases.any { it.id in favoriteIds }
                // Move a saved favorite from the discarded alias to the retained entry.
                aliases.forEach { favoriteIds.remove(it.id) }
                if (favorite) favoriteIds.add(preferred.id)
                preferred.copy(isFavorite = favorite)
            }
        if (favoriteIds != _favoriteIds.value) {
            _favoriteIds.value = favoriteIds
            prefs.edit().putStringSet("favorites", favoriteIds.map { it.toString() }.toSet()).apply()
        }
        return sortItems(merged, getSortOrder())
    }

    private fun mediaIdentity(uri: Uri, folder: String, name: String): String {
        if (uri.authority == "com.android.externalstorage.documents") {
            try {
                val documentId = DocumentsContract.getDocumentId(uri)
                val volume = documentId.substringBefore(':')
                val relative = documentId.substringAfter(':', "")
                val root = if (volume.equals("primary", ignoreCase = true))
                    android.os.Environment.getExternalStorageDirectory().absolutePath
                else "/storage/$volume"
                return "file:${File("$root/$relative").normalize().path}"
            } catch (_: IllegalArgumentException) {
                // Fall back to the URI if this is not a document URI.
            }
        } else if (uri.authority == "media" && folder.isNotBlank()) {
            return "file:${File(folder, name).normalize().path}"
        } else if (uri.scheme == "file") {
            return "file:${File(uri.path.orEmpty()).normalize().path}"
        } else {
            try {
                // A document may be reached through more than one selected parent tree.
                return "document:${uri.authority}:${DocumentsContract.getDocumentId(uri)}"
            } catch (_: IllegalArgumentException) {
                // Unknown providers retain their distinct URI identities.
            }
        }
        return "uri:$uri"
    }

    /** All folder paths that have media, regardless of visibility. */
    suspend fun getAllFolderPaths(): Set<String> =
        loadAllMedia().map { it.albumId }.toSet()

    /** Media filtered by current folder visibility — used for display. */
    suspend fun loadVisibleMedia(): List<MediaItem> =
        loadAllMedia().filter { isFolderVisible(it.albumId) }

    fun calculateStorageUsage(items: List<MediaItem>): Long = items.sumOf { it.size }

    fun sortItems(items: List<MediaItem>, order: SortOrder): List<MediaItem> = when (order) {
        SortOrder.DATE_DESC -> items.sortedByDescending { it.dateTaken }
        SortOrder.DATE_ASC  -> items.sortedBy { it.dateTaken }
        SortOrder.NAME_ASC  -> items.sortedBy { it.name.lowercase() }
        SortOrder.SIZE_DESC -> items.sortedByDescending { it.size }
    }

    suspend fun moveMedia(item: MediaItem, targetParentUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val documentItem = findDocumentAlias(item)
        if (documentItem.parentUri == null) return@withContext false
        try {
            // Attempt native move
            val result = DocumentsContract.moveDocument(
                context.contentResolver,
                documentItem.uri,
                documentItem.parentUri,
                targetParentUri
            )
            if (result != null) {
                removeFromCache(item)
                return@withContext true
            }
        } catch (e: Exception) {
            // Cross-provider move might fail, fallback to copy + delete
            if (copyMedia(item, targetParentUri)) {
                permanentlyDeleteFromStorage(item)
                removeFromCache(item)
                return@withContext true
            }
        }
        false
    }

    suspend fun copyMedia(item: MediaItem, targetParentUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val documentItem = findDocumentAlias(item)
        try {
            val result = DocumentsContract.copyDocument(
                context.contentResolver,
                documentItem.uri,
                targetParentUri
            )
            return@withContext result != null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }

    private fun findDocumentAlias(item: MediaItem): MediaItem {
        if (item.parentUri != null) return item
        val identity = mediaIdentity(item.uri, item.albumId, item.name)
        return getSafCache().firstOrNull {
            it.parentUri != null && mediaIdentity(it.uri, it.albumId, it.name) == identity
        } ?: item
    }

    private fun removeFromCache(item: MediaItem) {
        val identity = mediaIdentity(item.uri, item.albumId, item.name)
        val currentSaf = getSafCache().toMutableList()
        if (currentSaf.removeAll { mediaIdentity(it.uri, it.albumId, it.name) == identity }) {
            saveSafCache(currentSaf)
        }
    }

    private fun permanentlyDeleteFromStorage(item: MediaItem) {
        try { context.contentResolver.delete(item.uri, null, null) } catch (_: Exception) {}
    }

    suspend fun refreshSafMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val safUris = getSafUris()
        if (safUris.isEmpty()) {
            saveSafCache(emptyList())
            return@withContext emptyList()
        }

        val allDiscovered = mutableListOf<MediaItem>()
        for (uriString in safUris) {
            try {
                val treeUri = Uri.parse(uriString)
                allDiscovered += scanSafTree(treeUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val uniqueItems = mergeMedia(allDiscovered)
        saveSafCache(uniqueItems)
        uniqueItems
    }

    fun loadMetadata(item: MediaItem): MediaItem {
        if (item.width > 0 && item.height > 0) return item // Already loaded

        var width = 0
        var height = 0
        var duration = 0L

        try {
            context.contentResolver.openFileDescriptor(item.uri, "r")?.use { pfd ->
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(pfd.fileDescriptor)
                if (item.mediaType == MediaType.VIDEO) {
                    duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                }
                width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

                if (width == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_IMAGE_WIDTH)?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_IMAGE_HEIGHT)?.toIntOrNull() ?: 0
                }
                retriever.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return item.copy(width = width, height = height, duration = duration)
    }

    // ── SAF Cache ─────────────────────────────────────────────────────────

    private fun getSafCache(): List<MediaItem> {
        val raw = prefs.getStringSet("saf_cache", emptySet()) ?: emptySet()
        return raw.mapNotNull { deserializeMediaItem(it) }
    }

    private fun saveSafCache(items: List<MediaItem>) {
        val raw = items.map { serializeMediaItem(it) }.toSet()
        prefs.edit().putStringSet("saf_cache", raw).apply()
    }

    private fun serializeMediaItem(item: MediaItem): String =
        listOf(
            item.id, item.uri, item.name, item.dateTaken, item.size,
            item.mimeType, item.width, item.height, item.duration,
            item.mediaType.name, item.albumId, item.parentUri?.toString() ?: ""
        ).joinToString("\u0000")  // use null byte — cannot appear in file names or URIs

    private fun deserializeMediaItem(s: String): MediaItem? = try {
        val p = s.split("\u0000")  //  match new delimiter
        val id = p[0].toLong()
        val parentUriStr = if (p.size > 11) p[11] else ""
        MediaItem(
            id         = id,
            uri        = Uri.parse(p[1]),
            name       = p[2],
            dateTaken  = p[3].toLong(),
            size       = p[4].toLong(),
            mimeType   = p[5],
            width      = p[6].toInt(),
            height     = p[7].toInt(),
            duration   = p[8].toLong(),
            mediaType  = MediaType.valueOf(p[9]),
            isFavorite = isFavorite(id),
            albumId    = p[10],
            parentUri  = if (parentUriStr.isNotEmpty()) Uri.parse(parentUriStr) else null,
        )
    } catch (e: Exception) { null }

    private suspend fun scanSafTree(treeUri: Uri): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        val displayPath = safUriToDisplayPath(treeUri) ?: ""

        recursiveScanSafOptimized(rootUri, treeUri, displayPath, result)
        result
    }

    private fun recursiveScanSafOptimized(parentUri: Uri, treeUri: Uri, albumPath: String, result: MutableList<MediaItem>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(parentUri))

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol)
                val name  = cursor.getString(nameCol)
                val mime  = cursor.getString(mimeCol)
                val size  = cursor.getLong(sizeCol)
                val date  = cursor.getLong(dateCol)
                val uri   = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val subAlbumPath = if (albumPath.isEmpty()) name else "$albumPath/$name"
                    addDiscoveredFolder(subAlbumPath)
                    recursiveScanSafOptimized(uri, treeUri, subAlbumPath, result)
                } else if (mime.startsWith("image/") || mime.startsWith("video/")) {
                    // use 64-bit hash to avoid collisions from 32-bit hashCode()
                    val uriStr = uri.toString()
                    val id = uriStr.hashCode().toLong() xor (uriStr.reversed().hashCode().toLong() shl 32)
                    val type = when {
                        mime.contains("gif") -> MediaType.GIF
                        mime.startsWith("video/") -> MediaType.VIDEO
                        else -> MediaType.IMAGE
                    }

                    result.add(MediaItem(
                        id         = id,
                        uri        = uri,
                        name       = name,
                        dateTaken  = date,
                        size       = size,
                        mimeType   = mime,
                        width      = 0, // Lazy load these later
                        height     = 0,
                        duration   = 0,
                        mediaType  = type,
                        isFavorite = isFavorite(id),
                        albumId    = albumPath,
                        parentUri  = parentUri,
                    ))
                }
            }
        }
    }

    private fun queryMediaStore(
        contentUri: Uri,
        mediaType: MediaType,
        chosenFolders: Set<String>
    ): List<MediaItem> {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            if (mediaType == MediaType.VIDEO) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        val hasFullAccess = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val hasSelectedAccess = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFullAccess && !hasSelectedAccess) return emptyList()
        val projection = if (mediaType == MediaType.VIDEO) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DATA,
                MediaStore.Video.Media.DURATION,
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DATA,
            )
        }

        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(
            contentUri, projection, null, null,
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol     = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val sizeCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val widthCol  = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val dataCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataCol) ?: continue
                if (!isUnderChosenFolder(filePath, chosenFolders)) continue

                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(contentUri, id)
                val mime = cursor.getString(mimeCol) ?: ""
                val actualType = when {
                    mime.contains("gif") -> MediaType.GIF
                    else -> mediaType
                }
                val duration = if (mediaType == MediaType.VIDEO) {
                    val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    if (durCol >= 0) cursor.getLong(durCol) else 0L
                } else 0L

                val relativePath = getRelativePath(filePath, chosenFolders)

                result += MediaItem(
                    id        = id,
                    uri       = uri,
                    name      = cursor.getString(nameCol) ?: "",
                    dateTaken = cursor.getLong(dateCol),
                    size      = cursor.getLong(sizeCol),
                    mimeType  = mime,
                    width     = cursor.getInt(widthCol),
                    height    = cursor.getInt(heightCol),
                    duration  = duration,
                    mediaType = actualType,
                    isFavorite = isFavorite(id),
                    albumId   = relativePath,
                )
            }
        }
        return result
    }

    private fun isUnderChosenFolder(filePath: String, chosenFolders: Set<String>): Boolean =
        chosenFolders.any { folder -> filePath.startsWith("${folder.trimEnd('/')}/") }

    private fun getRelativePath(filePath: String, chosenFolders: Set<String>): String {
        val matchingRoot = chosenFolders.firstOrNull { filePath.startsWith("${it.trimEnd('/')}/") } ?: return ""
        val relative = filePath.removePrefix(matchingRoot).trimStart('/')
        val parentDir = relative.substringBeforeLast('/', "")
        return if (parentDir.isEmpty()) matchingRoot else "$matchingRoot/$parentDir"
    }

    // ── Album building ────────────────────────────────────────────────────
    suspend fun buildAlbums(allMedia: List<MediaItem>): List<Album> = withContext(Dispatchers.IO) {
        allMedia.groupBy { it.albumId }
            .map { (albumId, items) ->
                val folderName = albumId.substringAfterLast('/')
                val displayName = if (folderName.isEmpty()) "Root"
                else folderName.removePrefix(".").replaceFirstChar { it.uppercase() }
                Album(
                    id         = albumId,
                    name       = displayName,
                    coverUri   = items.firstOrNull()?.uri,
                    mediaCount = items.size,
                    folderUri  = folderUriForPath(albumId)
                )
            }
            .sortedBy { it.name }
    }

    fun folderUriForPath(path: String): Uri? {
        getSafCache().firstOrNull { it.albumId == path && it.parentUri != null }?.parentUri?.let { return it }
        val roots = getSafUris().mapNotNull { value ->
            val uri = Uri.parse(value)
            safUriToDisplayPath(uri)?.let { it to uri }
        }.sortedByDescending { it.first.length }
        for ((root, treeUri) in roots) {
            if (path != root && !path.startsWith("${root.trimEnd('/')}/")) continue
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            if (path == root) return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
            if (treeUri.authority == "com.android.externalstorage.documents") {
                val relative = path.removePrefix(root).trimStart('/')
                val documentId = treeId + (if (treeId.endsWith(':')) "" else "/") + relative
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            }
        }
        return null
    }

    suspend fun buildDestinationAlbums(media: List<MediaItem>): List<Album> = withContext(Dispatchers.IO) {
        val roots = getChosenFolders()
        val paths = roots + getAllDiscoveredFolders() + media.map { it.albumId }
        paths.filter { path ->
            isFolderVisible(path) && roots.any { path == it || path.startsWith("${it.trimEnd('/')}/") }
        }.mapNotNull { path ->
            val uri = folderUriForPath(path) ?: return@mapNotNull null
            Album(path, path.substringAfterLast('/'), media.firstOrNull { it.albumId == path }?.uri,
                media.count { it.albumId == path }, folderUri = uri)
        }.sortedBy { it.name.lowercase() }
    }

    // ── Group by date ─────────────────────────────────────────────────────
    fun groupByDate(items: List<MediaItem>): List<MediaGroup> {
        val today     = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 0) }
        val monthFmt  = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        return items.groupBy { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.dateTaken }
            when {
                isSameDay(cal, today)     -> "Today"
                isSameDay(cal, yesterday) -> "Yesterday"
                else -> monthFmt.format(Date(item.dateTaken))
            }
        }.map { (label, items) -> MediaGroup(label, items) }
    }

    private fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    // ── Recycle bin ───────────────────────────────────────────────────────
    // Deleted items stored as pipe-delimited strings in SharedPreferences.
    // Format: id|uriString|name|deletedAt|albumId|mimeType|size|duration|mediaType

    private fun serializeDeleted(item: DeletedItem): String =
        listOf(
            item.id, item.uri, item.name, item.deletedAt,
            item.originalAlbumId, item.mimeType, item.size,
            item.duration, item.mediaType.name,
        ).joinToString("\u0000")  // use null byte — cannot appear in file names or URIs

    private fun deserializeDeleted(s: String): DeletedItem? = try {
        val p = s.split("\u0000")  // match new delimiter
        DeletedItem(
            id              = p[0].toLong(),
            uri             = Uri.parse(p[1]),
            name            = p[2],
            deletedAt       = p[3].toLong(),
            originalAlbumId = p[4],
            mimeType        = p[5],
            size            = p[6].toLong(),
            duration        = p[7].toLong(),
            mediaType       = MediaType.valueOf(p[8]),
        )
    } catch (e: Exception) { null }

    fun getDeletedItems(): List<DeletedItem> {
        val raw = prefs.getStringSet("recycle_bin", emptySet()) ?: emptySet()
        val allItems = raw.mapNotNull { deserializeDeleted(it) }

        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val (validItems, expiredItems) = allItems.partition { it.deletedAt > thirtyDaysAgo }

        // batch-remove all expired items in one prefs write instead of recursing,
        // which could loop infinitely if a deletion keeps failing.
        if (expiredItems.isNotEmpty()) {
            val expiredIds = expiredItems.map { it.id }.toSet()
            val updatedRaw = raw.filter { s -> deserializeDeleted(s)?.id !in expiredIds }.toSet()
            prefs.edit().putStringSet("recycle_bin", updatedRaw).apply()
            expiredItems.forEach { item ->
                try { context.contentResolver.delete(item.uri, null, null) } catch (_: Exception) {}
            }
        }

        return validItems.sortedByDescending { it.deletedAt }
    }

    fun deleteMedia(item: MediaItem) {
        val deleted = DeletedItem(
            id              = item.id,
            uri             = item.uri,
            name            = item.name,
            deletedAt       = System.currentTimeMillis(),
            originalAlbumId = item.albumId,
            mimeType        = item.mimeType,
            size            = item.size,
            duration        = item.duration,
            mediaType       = item.mediaType,
        )
        val raw = prefs.getStringSet("recycle_bin", emptySet())!!.toMutableSet()
        raw.add(serializeDeleted(deleted))
        prefs.edit().putStringSet("recycle_bin", raw).apply()
    }

    fun restoreDeleted(item: DeletedItem) {
        val raw = prefs.getStringSet("recycle_bin", emptySet())!!.toMutableSet()
        raw.removeAll { deserializeDeleted(it)?.id == item.id }
        prefs.edit().putStringSet("recycle_bin", raw).apply()
    }

    fun permanentlyDelete(item: DeletedItem) {
        val raw = prefs.getStringSet("recycle_bin", emptySet())!!.toMutableSet()
        raw.removeAll { deserializeDeleted(it)?.id == item.id }
        prefs.edit().putStringSet("recycle_bin", raw).apply()

        // Also remove from SAF cache
        val identity = mediaIdentity(item.uri, item.originalAlbumId, item.name)
        val currentSaf = getSafCache().toMutableList()
        if (currentSaf.removeAll { mediaIdentity(it.uri, it.albumId, it.name) == identity }) {
            saveSafCache(currentSaf)
        }

        try { context.contentResolver.delete(item.uri, null, null) } catch (_: Exception) {}
    }
}
