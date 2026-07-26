package com.ishaan.myMedia.data.model

import android.net.Uri

enum class MediaType { IMAGE, VIDEO, GIF }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTaken: Long,
    val size: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0L,
    val mediaType: MediaType = MediaType.IMAGE,
    val isFavorite: Boolean = false,
    val albumId: String = "",
    val parentUri: Uri? = null, // Document URI of the parent directory (SAF only)
)

data class Album(
    val id: String,
    val name: String,
    val coverUri: Uri?,
    val mediaCount: Int,
    val subAlbums: List<Album> = emptyList(),
    val folderUri: Uri? = null, // Document URI of the folder (SAF only)
)

data class MediaGroup(
    val label: String,
    val items: List<MediaItem>,
)

data class FolderNode(
    val name: String,
    val path: String,
    val isVisible: Boolean,
    val subFolders: List<FolderNode> = emptyList(),
)

data class DeletedItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val deletedAt: Long,
    val originalAlbumId: String,
    val mimeType: String,
    val size: Long,
    val duration: Long = 0L,
    val mediaType: MediaType = MediaType.IMAGE,
)

enum class AppTheme { SYSTEM, LIGHT, DARK }

enum class SortOrder { DATE_DESC, DATE_ASC, NAME_ASC, SIZE_DESC }

data class PrivacyPrefs(
    val pinEnabled: Boolean = false,
    val pin: String = "",           // holds the HASH (read from "pin_hash" prefs), never plaintext; use PrivacyRepository.checkPin() for comparison
    val biometricsEnabled: Boolean = false,
    val instantLockEnabled: Boolean = false,
)